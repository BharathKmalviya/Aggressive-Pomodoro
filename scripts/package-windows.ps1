param(
    [switch]$Msi
)

$ErrorActionPreference = 'Stop'
$repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$versionFile = Join-Path $repo 'desktopApp\resources\version.properties'
$version = ((Get-Content -LiteralPath $versionFile | Where-Object { $_ -match '^version=' }) -replace '^version=', '').Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw "Invalid version in $versionFile" }
# Preserve the UpgradeCode read from the immutable v0.1.0 MSI. Changing it creates
# a different Windows product family instead of upgrading the installed app.
$windowsUpgradeUuid = '8b4bb341-127a-3a18-945b-b92f5c0cd1fd'

Push-Location $repo
try {
    foreach ($command in @('build', 'test')) {
        & .\kotlin.bat $command
        if ($LASTEXITCODE -ne 0) { throw "Kotlin $command failed" }
    }
    & .\kotlin.bat package -m desktopApp
    if ($LASTEXITCODE -ne 0) { throw 'Executable JAR package failed' }

    $jar = Join-Path $repo 'build\tasks\_desktopApp_executableJarJvm\desktopApp-jvm-executable.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing executable JAR: $jar" }
    $buildRoot = [System.IO.Path]::GetFullPath((Join-Path $repo 'build'))
    $output = [System.IO.Path]::GetFullPath((Join-Path $buildRoot 'distribution'))
    if (-not $output.StartsWith($buildRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to clean a path outside build/'
    }
    if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Recurse -Force }
    $inputDir = Join-Path $output 'input'
    $artifactDir = Join-Path $output 'artifacts'
    New-Item -ItemType Directory -Force $inputDir, $artifactDir | Out-Null
    Copy-Item -LiteralPath $jar -Destination (Join-Path $inputDir 'desktopApp-jvm-executable.jar')
    Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination (Join-Path $artifactDir 'LICENSE')

    & jpackage --type app-image --name AggressivePomodoro --app-version $version `
        --vendor 'Bharath Malviya' --copyright 'Copyright (c) 2026 Bharath Malviya' `
        --icon desktopApp\resources\app.ico --input $inputDir --main-jar desktopApp-jvm-executable.jar `
        --dest $artifactDir
    if ($LASTEXITCODE -ne 0) { throw 'App image packaging failed' }

    $image = Join-Path $artifactDir 'AggressivePomodoro'
    $exe = Join-Path $image 'AggressivePomodoro.exe'
    $process = Start-Process -FilePath $exe -PassThru -WindowStyle Hidden
    try {
        Start-Sleep -Seconds 5
        $process.Refresh()
        if ($process.HasExited) { throw "App image exited during smoke check: $($process.ExitCode)" }
    } finally {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
    }

    if ($Msi) {
        if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -or
            -not (Get-Command light.exe -ErrorAction SilentlyContinue)) {
            throw 'WiX 3.14.1 candle.exe and light.exe must be on PATH to create MSI'
        }
        & jpackage --type msi --app-image $image --app-version $version --license-file LICENSE `
            --vendor 'Bharath Malviya' --copyright 'Copyright (c) 2026 Bharath Malviya' `
            --dest $artifactDir --win-menu --win-shortcut --win-upgrade-uuid $windowsUpgradeUuid
        if ($LASTEXITCODE -ne 0) { throw 'MSI packaging failed' }
        $msiFile = Join-Path $artifactDir "AggressivePomodoro-$version.msi"
        if (-not (Test-Path -LiteralPath $msiFile)) { throw "Expected MSI missing: $msiFile" }
        $installer = $database = $view = $null
        try {
            $installer = New-Object -ComObject WindowsInstaller.Installer
            $database = $installer.OpenDatabase($msiFile, 0)
            $view = $database.OpenView('SELECT `Property`, `Value` FROM `Property`')
            $view.Execute()
            $properties = @{}
            while ($record = $view.Fetch()) { $properties[$record.StringData(1)] = $record.StringData(2) }
            if ($properties['ProductVersion'] -ne $version) { throw 'MSI version differs from the application version' }
            if ($properties['UpgradeCode'] -ne "{$windowsUpgradeUuid}") { throw 'MSI upgrade identity changed' }
            if ($properties['ProductName'] -ne 'AggressivePomodoro') { throw 'Unexpected MSI product name' }
        } finally {
            if ($view) { $view.Close() }
            foreach ($comObject in @($view, $database, $installer)) {
                if ($null -ne $comObject) { [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($comObject) }
            }
        }
        $hash = (Get-FileHash -LiteralPath $msiFile -Algorithm SHA256).Hash.ToLowerInvariant()
        Set-Content -LiteralPath (Join-Path $artifactDir 'SHA256SUMS.txt') `
            -Value "$hash  $(Split-Path -Leaf $msiFile)" -Encoding ascii
    }
    Write-Output "Windows artifacts: $artifactDir"
} finally {
    Pop-Location
}
