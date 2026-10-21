param(
    [ValidateSet('exe', 'app-image')]
    [string]$Type = 'exe',
    [ValidatePattern('^\d+(\.\d+){0,3}$')]
    [string]$AppVersion = '1.0.3'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'Il pacchetto Windows deve essere costruito su Windows.'
}
if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw 'Imposta JAVA_HOME su un JDK 21 o successivo con jpackage.'
}

$javaRelease = Join-Path $env:JAVA_HOME 'release'
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path $javaRelease) -or -not (Test-Path $jpackage)) {
    throw 'JAVA_HOME non indica un JDK con jpackage.'
}
$javaVersion = [regex]::Match((Get-Content $javaRelease -Raw), 'JAVA_VERSION="([0-9]+)')
if (-not $javaVersion.Success -or [int]$javaVersion.Groups[1].Value -lt 21) {
    throw 'Serve un JDK 21 o successivo.'
}
$maven = (Get-Command 'mvn.cmd' -ErrorAction Stop).Source

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$desktopDir = Join-Path $projectRoot 'fisio-desktop'
$icon = Join-Path $desktopDir 'src\main\resources\desktop\assets\img\logo.ico'
$stageDir = Join-Path $desktopDir 'target\windows-package'
$inputDir = Join-Path $stageDir 'input'
$outputDir = Join-Path $desktopDir "target\windows-dist\$AppVersion"

Push-Location $projectRoot
try {
    Write-Host '>> Build Maven desktop e moduli richiesti'
    & $maven '-pl' 'fisio-desktop' '-am' 'package'
    if ($LASTEXITCODE -ne 0) { throw 'Build Maven fallita.' }

    if (Test-Path $stageDir) { Remove-Item $stageDir -Recurse -Force }
    New-Item $inputDir -ItemType Directory -Force | Out-Null

    Write-Host '>> Copia dipendenze runtime Windows'
    $dependencyArgs = @('-f', (Join-Path $desktopDir 'pom.xml'),
        'org.apache.maven.plugins:maven-dependency-plugin:3.7.0:copy-dependencies',
        '-DincludeScope=runtime', "-DoutputDirectory=$inputDir")
    & $maven @dependencyArgs
    if ($LASTEXITCODE -ne 0) { throw 'Copia dipendenze Maven fallita.' }

    $appJar = Join-Path $desktopDir 'target\fisio-desktop-1.0.jar'
    if (-not (Test-Path $appJar)) { throw "JAR desktop non trovato: $appJar" }
    if (-not (Get-ChildItem $inputDir -Filter 'javafx-web-*-win.jar' -File)) {
        throw 'Dipendenza JavaFX Web per Windows assente. Esegui lo script su Windows.'
    }
    Copy-Item $appJar $inputDir
    if (Test-Path $outputDir) { Remove-Item $outputDir -Recurse -Force }
    New-Item $outputDir -ItemType Directory -Force | Out-Null

    $packageArgs = @(
        '--type', $Type,
        '--name', 'Fisio e Sports',
        '--app-version', $AppVersion,
        '--vendor', 'SimoSW',
        '--icon', $icon,
        '--input', $inputDir,
        '--dest', $outputDir,
        '--main-jar', 'fisio-desktop-1.0.jar',
        '--main-class', 'it.SimoSW.desktop.DesktopLauncher',
        '--java-options', "-Dfisio.desktop.version=$AppVersion",
        '--java-options', "-Dfisio.desktop.installType=$Type",
        '--java-options', '-Dprism.order=sw',
        '--add-modules', 'ALL-MODULE-PATH,java.desktop'
    )
    if ($Type -eq 'exe') {
        # Codice di upgrade dei vecchi pacchetti jpackage (SimoSW/Fisio-e-Sports).
        $packageArgs += @('--win-upgrade-uuid', '873d5ba6-5207-3ea9-ac27-e0693c6158d2',
            '--win-menu', '--win-shortcut', '--win-dir-chooser', '--win-per-user-install')
    }

    Write-Host ">> Crea pacchetto Windows ($Type)"
    & $jpackage @packageArgs
    if ($LASTEXITCODE -ne 0) { throw 'jpackage non ha creato il pacchetto.' }

    if ($Type -eq 'exe') {
        $installer = @(Get-ChildItem $outputDir -Filter '*.exe' -File)
        if ($installer.Count -ne 1) { throw 'Installer EXE non trovato o ambiguo.' }
        $releaseInstaller = Join-Path $outputDir 'Fisio e Sports - installer.exe'
        Move-Item $installer[0].FullName $releaseInstaller -Force
        $hash = (Get-FileHash $releaseInstaller -Algorithm SHA256).Hash.ToLowerInvariant()
        @("version=$AppVersion", "sha256=$hash") |
            Set-Content (Join-Path $outputDir 'desktop-update.properties') -Encoding ascii
        Write-Host '>> Pubblica Fisio e Sports - installer.exe e desktop-update.properties nella stessa GitHub Release'
    }

    Write-Host ">> Pacchetto pronto in: $outputDir"
} finally {
    Pop-Location
}
