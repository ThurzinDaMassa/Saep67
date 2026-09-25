param(
    [string]$Jdk11 = $env:SAEP_JDK11,
    [string]$Jpackage = $env:SAEP_JPACKAGE
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$dist = Join-Path $root 'dist'

if (-not $Jdk11) {
    $Jdk11 = @('C:\Program Files\OpenJDK\jdk-11.0.16.8-hotspot', 'C:\Program Files\Java\jdk-11') |
        Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\jlink.exe') } | Select-Object -First 1
}
if (-not $Jpackage) {
    $Jpackage = 'C:\Program Files\Java\jdk-23\bin\jpackage.exe'
}
if (-not (Test-Path -LiteralPath (Join-Path $Jdk11 'bin\jlink.exe'))) { throw 'JDK 11 com jlink não encontrado. Defina SAEP_JDK11.' }
if (-not (Test-Path -LiteralPath $Jpackage)) { throw 'jpackage não encontrado. Defina SAEP_JPACKAGE.' }

function Remove-BuildDirectory([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return }
    $resolved = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $path).Path)
    $expectedPrefix = [IO.Path]::GetFullPath($dist) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Recusa remover caminho fora de dist: $resolved"
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $dist | Out-Null

$previousJavaHome = $env:JAVA_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = $Jdk11
    if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $root 'tmp\gradle-home' }
    Push-Location $root
    try {
        & (Join-Path $root 'gradlew.bat') ':sistema:standaloneJar'
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar SAEP-Site.jar.' }
    } finally { Pop-Location }
} finally { $env:JAVA_HOME = $previousJavaHome; $env:GRADLE_USER_HOME = $previousGradleHome }

Remove-BuildDirectory (Join-Path $dist 'saep-runtime')
Remove-BuildDirectory (Join-Path $dist 'package-input')
Remove-BuildDirectory (Join-Path $dist 'SAEP-Estoque')

$runtime = Join-Path $dist 'saep-runtime'
& (Join-Path $Jdk11 'bin\jlink.exe') --add-modules java.base,java.desktop,java.management,java.naming,java.security.jgss,java.sql,jdk.httpserver,jdk.net,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar o runtime Java 11.' }

$inputDir = Join-Path $dist 'package-input'
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'entrega\sistema\build\libs\SAEP-Site.jar') -Destination (Join-Path $inputDir 'SAEP-Site.jar')
& $Jpackage --type app-image --name SAEP-Estoque --app-version 2.0 --input $inputDir --main-jar SAEP-Site.jar --main-class br.saep.estoque.WebLauncher --runtime-image $runtime --dest $dist --win-console
if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar o executável.' }
$exe = Join-Path $dist 'SAEP-Estoque\SAEP-Estoque.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw 'Executável não encontrado após o empacotamento.' }
Write-Host "Executável atualizado: $exe"
