param(
    [string]$AppInstaller = (Join-Path $PSScriptRoot 'Fisio e Sports - installer.exe'),
    [string]$BackupFile,
    [ValidatePattern('^[A-Za-z0-9_]+$')][string]$DatabaseName = 'fisio_e_sport'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'Eseguire questo script su Windows.'
}
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Avviare PowerShell come amministratore con lo stesso account Windows che userà Fisio e Sports.'
}
if (-not (Test-Path -LiteralPath $AppInstaller -PathType Leaf)) {
    throw "Installer dell'app non trovato: $AppInstaller"
}
$AppInstaller = (Resolve-Path -LiteralPath $AppInstaller).Path
$profileDir = Join-Path $env:LOCALAPPDATA 'Fisio-e-Sports'
$backendDir = Join-Path $profileDir 'backend'
$configPath = Join-Path $backendDir 'config.properties'
$serviceName = 'FisioMySQL'

function New-RandomPassword {
    $bytes = New-Object byte[] 36
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

function Invoke-MySql {
    param([string]$Exe, [string]$Password, [string]$Sql, [string]$ImportFile)
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $Exe
    $start.Arguments = '--protocol=tcp --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4'
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    if ([string]::IsNullOrEmpty($Password)) {
        $start.EnvironmentVariables.Remove('MYSQL_PWD')
    } else {
        $start.EnvironmentVariables['MYSQL_PWD'] = $Password
    }
    $process = [System.Diagnostics.Process]::Start($start)
    try {
        $errorTask = $process.StandardError.ReadToEndAsync()
        if ($ImportFile) {
            $file = [IO.File]::OpenRead($ImportFile)
            try {
                if ($ImportFile.EndsWith('.gz', [StringComparison]::OrdinalIgnoreCase)) {
                    $gzip = [IO.Compression.GZipStream]::new($file, [IO.Compression.CompressionMode]::Decompress)
                    try { $gzip.CopyTo($process.StandardInput.BaseStream) } finally { $gzip.Dispose() }
                } else {
                    $file.CopyTo($process.StandardInput.BaseStream)
                }
            } finally { $file.Dispose() }
        } else {
            $process.StandardInput.WriteLine($Sql)
        }
        $process.StandardInput.Close()
        $output = $process.StandardOutput.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "MySQL: $($errorTask.Result.Trim())" }
        return $output.Trim()
    } finally { $process.Dispose() }
}

function Confirm-BackupFile {
    param([string]$Path)
    $foundCreate = $false
    $foundUse = $false
    $file = [IO.File]::OpenRead($Path)
    try {
        if ($Path.EndsWith('.gz', [StringComparison]::OrdinalIgnoreCase)) {
            $stream = [IO.Compression.GZipStream]::new($file, [IO.Compression.CompressionMode]::Decompress)
        } else {
            $stream = $file
        }
        try {
            $reader = [IO.StreamReader]::new($stream)
            while ($null -ne ($line = $reader.ReadLine())) {
                if ($line -match 'CREATE DATABASE' -and $line.Contains('`' + $DatabaseName + '`')) { $foundCreate = $true }
                if ($line -match '^USE ' -and $line.Contains('`' + $DatabaseName + '`')) { $foundUse = $true }
            }
        } finally { $stream.Dispose() }
    } finally { $file.Dispose() }
    if (-not ($foundCreate -and $foundUse)) { throw 'Il dump non contiene CREATE DATABASE e USE per il database atteso.' }
}

function Get-LatestLtsZip {
    $winget = (Get-Command winget.exe -ErrorAction Stop).Source
    $versions = & $winget show --id Oracle.MySQL --exact --versions --source winget --accept-source-agreements 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Impossibile leggere le versioni MySQL da Windows Package Manager.' }
    # Le serie LTS pubblicate da MySQL; la serie più recente disponibile prevale.
    $candidates = @($versions | ForEach-Object {
        if ($_ -match '^\s*((?:9\.7|8\.4)\.\d+)\s*$') { [version]$Matches[1] }
    } | Sort-Object -Descending)
    if ($candidates.Count -eq 0) { throw 'Nessuna release MySQL LTS 9.7/8.4 trovata nel catalogo winget.' }
    $version = $candidates[0].ToString()
    $series = ($version -split '\.')[0..1] -join '.'
    $url = "https://cdn.mysql.com/Downloads/MySQL-$series/mysql-$version-winx64.zip"
    return @{ Version = $version; Url = $url }
}

if (Test-Path -LiteralPath $configPath -PathType Leaf) {
    if (-not (Get-Service -Name $serviceName -ErrorAction SilentlyContinue)) {
        throw "La configurazione esiste ma il servizio $serviceName manca. Verificare MySQL prima di aggiornare l'app."
    }
    Write-Host 'Configurazione locale già presente: aggiorno soltanto l’app. Il database e i dati non vengono modificati.'
} else {
    $databaseServices = @(Get-Service | Where-Object {
        $_.Name -match 'mysql|mariadb' -or $_.DisplayName -match 'mysql|mariadb'
    })
    if ($databaseServices.Count -gt 0) {
        throw "Un servizio MySQL/MariaDB esiste già ma manca $configPath. Verificare i dati presenti prima di riprovare."
    }
    if (Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue) {
        throw 'La porta MySQL 3306 è già occupata. Non installo né importo sopra un database esistente.'
    }
    $dataDir = Join-Path $env:ProgramData 'Fisio-e-Sports\mysql-data'
    $ini = Join-Path $env:ProgramData 'Fisio-e-Sports\my.ini'
    if ((Test-Path -LiteralPath $dataDir) -or (Test-Path -LiteralPath $ini)) {
        throw 'Esistono dati o configurazioni MySQL locali: verificare prima di riprovare.'
    }

    if ([string]::IsNullOrWhiteSpace($BackupFile)) {
        Add-Type -AssemblyName System.Windows.Forms
        $dialog = New-Object System.Windows.Forms.OpenFileDialog
        $dialog.Title = 'Seleziona il backup del database Fisio e Sports'
        $dialog.Filter = 'Backup SQL (*.sql.gz;*.sql)|*.sql.gz;*.sql|Tutti i file (*.*)|*.*'
        if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
            throw 'Selezione del backup annullata.'
        }
        $BackupFile = $dialog.FileName
    }
    if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf) -or
            $BackupFile -notmatch '\.sql(\.gz)?$') {
        throw 'Selezionare un file di backup .sql oppure .sql.gz.'
    }
    $backup = (Resolve-Path -LiteralPath $BackupFile).Path
    Write-Host ">> Verifico il backup locale: $backup"
    Confirm-BackupFile -Path $backup

    $mysqlRelease = Get-LatestLtsZip
    if (-not (Test-Path -LiteralPath (Join-Path $env:WINDIR 'System32\vcruntime140_1.dll'))) {
        Write-Host '>> Installo il runtime Microsoft Visual C++ richiesto da MySQL'
        & winget.exe install --id 'Microsoft.VCRedist.2015+.x64' --exact --source winget --silent --accept-package-agreements --accept-source-agreements
        if ($LASTEXITCODE -ne 0) { throw 'Installazione del runtime Visual C++ fallita.' }
    }
    $version = $mysqlRelease.Version
    $downloadDir = Join-Path $env:TEMP ('fisio-mysql-' + $version)
    $zip = Join-Path $downloadDir ('mysql-' + $version + '-winx64.zip')
    $stage = Join-Path $downloadDir 'extracted'
    New-Item -ItemType Directory -Path $downloadDir -Force | Out-Null
    Write-Host ">> Scarico MySQL $version dal sito ufficiale"
    Invoke-WebRequest -Uri $mysqlRelease.Url -OutFile $zip -UseBasicParsing
    Expand-Archive -LiteralPath $zip -DestinationPath $stage -Force
    $mysqlServer = Get-ChildItem -Path $stage -Recurse -Filter mysqld.exe -File | Select-Object -First 1
    if (-not $mysqlServer -or $mysqlServer.Directory.Name -ne 'bin') { throw 'Archivio MySQL non valido: mysqld.exe assente.' }
    $installDir = Join-Path $env:ProgramFiles ('Fisio-e-Sports\mysql-' + $version)
    if (Test-Path -LiteralPath $installDir) { throw "MySQL già presente in $installDir: controllo manuale necessario." }
    New-Item -ItemType Directory -Path (Split-Path $installDir) -Force | Out-Null
    Move-Item -LiteralPath $mysqlServer.Directory.Parent.FullName -Destination $installDir
    $mysqld = Join-Path $installDir 'bin\mysqld.exe'
    $mysql = Join-Path $installDir 'bin\mysql.exe'

    New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
    $baseForIni = $installDir.Replace('\', '/')
    $dataForIni = $dataDir.Replace('\', '/')
    @('[mysqld]', "basedir=`"$baseForIni`"", "datadir=`"$dataForIni`"", 'port=3306', 'bind-address=127.0.0.1') |
        Set-Content -LiteralPath $ini -Encoding ascii
    Write-Host '>> Inizializzo MySQL e il servizio locale'
    & $mysqld "--defaults-file=$ini" --initialize-insecure --console
    if ($LASTEXITCODE -ne 0) { throw 'Inizializzazione MySQL fallita.' }
    & $mysqld --install $serviceName "--defaults-file=$ini"
    if ($LASTEXITCODE -ne 0) { throw 'Registrazione del servizio MySQL fallita.' }
    Start-Service -Name $serviceName
    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        try {
            Invoke-MySql -Exe $mysql -Password $null -Sql 'SELECT 1;' | Out-Null
            $ready = $true
            break
        } catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $ready) { throw 'Il nuovo servizio MySQL non è pronto.' }

    $rootPassword = New-RandomPassword
    Invoke-MySql -Exe $mysql -Password $null -Sql "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';" | Out-Null
    $rootSecret = Join-Path $profileDir 'mysql-root-password.dpapi'
    ConvertTo-SecureString -String $rootPassword -AsPlainText -Force |
        ConvertFrom-SecureString | Set-Content -LiteralPath $rootSecret -Encoding ascii

    Write-Host '>> Importo il backup nel database appena creato'
    Invoke-MySql -Exe $mysql -Password $rootPassword -ImportFile $backup | Out-Null
    $checks = @(
        "SELECT COUNT(*) FROM $DatabaseName.patients WHERE therapist_id IS NULL",
        "SELECT COUNT(*) FROM $DatabaseName.appointments a JOIN $DatabaseName.patients p ON a.patient_id=p.id WHERE a.therapist_id<>p.therapist_id",
        "SELECT COUNT(*) FROM $DatabaseName.patient_anamneses a JOIN $DatabaseName.patients p ON a.patient_id=p.id WHERE a.therapist_id<>p.therapist_id",
        "SELECT COUNT(*) FROM $DatabaseName.treatment_plans t JOIN $DatabaseName.patients p ON t.patient_id=p.id WHERE t.therapist_id<>p.therapist_id",
        "SELECT COUNT(*) FROM $DatabaseName.treatment_sessions s JOIN $DatabaseName.patients p ON s.patient_id=p.id WHERE s.therapist_id<>p.therapist_id"
    )
    foreach ($check in $checks) {
        $result = Invoke-MySql -Exe $mysql -Password $rootPassword -Sql "$check;"
        if ($result -notmatch '(?m)^0\s*$') {
            throw 'Importazione completata, ma le associazioni dei pazienti richiedono verifica. Non creo la configurazione applicativa.'
        }
    }
    $appPassword = New-RandomPassword
    $setupSql = "CREATE USER 'fisio_app'@'127.0.0.1' IDENTIFIED BY '$appPassword'; CREATE USER 'fisio_app'@'localhost' IDENTIFIED BY '$appPassword'; GRANT ALL PRIVILEGES ON ``$DatabaseName``.* TO 'fisio_app'@'127.0.0.1', 'fisio_app'@'localhost';"
    Invoke-MySql -Exe $mysql -Password $rootPassword -Sql $setupSql | Out-Null
    New-Item -ItemType Directory -Path $backendDir -Force | Out-Null
    @(
        'db.driver=com.mysql.cj.jdbc.Driver',
        "db.url=jdbc:mysql://127.0.0.1:3306/$DatabaseName`?connectTimeout=3000",
        'db.username=fisio_app',
        "db.password=$appPassword",
        'db.pool.maxSize=5',
        'db.pool.minIdle=1',
        'db.pool.connectionTimeoutMs=3000',
        'whatsapp.baileys.enabled=false'
    ) | Set-Content -LiteralPath $configPath -Encoding ascii
    Write-Host '>> MySQL, dati e configurazione locale pronti'
}

Write-Host '>> Installo Fisio e Sports'
$appProcess = Start-Process -FilePath $AppInstaller -Wait -PassThru
if ($appProcess.ExitCode -ne 0) { throw "Installer Fisio e Sports fallito: codice $($appProcess.ExitCode)" }
Write-Host 'Installazione completata. Avvia Fisio e Sports e verifica http://127.0.0.1:8081/ready.'
