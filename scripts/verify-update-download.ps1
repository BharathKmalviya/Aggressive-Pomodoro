# Run after kotlin.bat package -m desktopApp. Uses only updater classes, never Main or msiexec.
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$jar = Join-Path $repo 'build\tasks\_desktopApp_executableJarJvm\desktopApp-jvm-executable.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Package the executable JAR before verifying the updater.' }
$work = Join-Path $repo ('build\updater-verification\' + [guid]::NewGuid().ToString('N'))
$lib = Join-Path $work 'lib'
New-Item -ItemType Directory -Path $lib -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^BOOT-INF/lib/([^/\\]+\.jar)$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $lib $Matches[1]))
        }
    }
} finally { $archive.Dispose() }
$classpath = Join-Path $lib '*'
& javac -cp $classpath -d $work (Join-Path $PSScriptRoot 'UpdateDownloadProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Updater probe compilation failed.' }
& java -cp "$work;$classpath" UpdateDownloadProbe (Join-Path $work 'downloads')
if ($LASTEXITCODE -ne 0) { throw 'Packaged updater could not download and verify the stable installer.' }
