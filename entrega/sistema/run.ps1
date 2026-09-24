param(
    [ValidateSet('web', 'desktop')][string]$Mode = 'web',
    [string]$DriverJar = "lib/mariadb-java-client-3.5.7.jar"
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$jdkBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { '' }
$javacExe = if ($jdkBin -and (Test-Path (Join-Path $jdkBin 'javac.exe'))) { Join-Path $jdkBin 'javac.exe' } else { 'javac' }
$javaExe = if ($jdkBin -and (Test-Path (Join-Path $jdkBin 'java.exe'))) { Join-Path $jdkBin 'java.exe' } else { 'java' }
if (-not (Test-Path -LiteralPath $DriverJar)) {
    throw "Driver JDBC não encontrado: $DriverJar. Veja README.md."
}
$sources = Get-ChildItem -LiteralPath 'src/main/java' -Recurse -Filter '*.java' | ForEach-Object FullName
New-Item -ItemType Directory -Force -Path 'build/classes' | Out-Null
& $javacExe --release 11 -encoding UTF-8 -d 'build/classes' $sources
if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação.' }
Copy-Item -LiteralPath 'src/main/resources/style.css' -Destination 'build/classes/style.css' -Force
$mainClass = if ($Mode -eq 'web') { 'br.saep.estoque.WebApp' } else { 'br.saep.estoque.App' }
& $javaExe -cp "build/classes;$DriverJar" $mainClass
