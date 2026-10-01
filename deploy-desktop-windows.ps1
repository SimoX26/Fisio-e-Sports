param(
    [ValidateSet('exe', 'app-image')]
    [string]$Type = 'exe',
    [ValidatePattern('^\d+(\.\d+){0,3}$')]
    [string]$AppVersion = '1.0.0'
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
    New-Item $outputDir -ItemType Directory -Force | Out-Null

    $packageArgs = @(
        '--type', $Type,
        '--name', 'Fisio-e-Sports',
        '--app-version', $AppVersion,
        '--vendor', 'SimoSW',
        '--input', $inputDir,
        '--dest', $outputDir,
        '--main-jar', 'fisio-desktop-1.0.jar',
        '--main-class', 'it.SimoSW.desktop.DesktopLauncher',
        '--add-modules', 'ALL-MODULE-PATH'
    )
    if ($Type -eq 'exe') {
        $packageArgs += @('--win-menu', '--win-shortcut', '--win-dir-chooser', '--win-per-user-install')
    }

    Write-Host ">> Crea pacchetto Windows ($Type)"
    & $jpackage @packageArgs
    if ($LASTEXITCODE -ne 0) { throw 'jpackage non ha creato il pacchetto.' }

    Write-Host ">> Pacchetto pronto in: $outputDir"
} finally {
    Pop-Location
}
