$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not (Get-Command kotlinc -ErrorAction SilentlyContinue)) {
    Write-Host "kotlinc absent; verification via le build Gradle autonome du domaine."
    & (Join-Path $Root "gradlew.bat") `
        --project-dir (Join-Path $Root "domain") `
        "-Pkotlin.compiler.execution.strategy=in-process" `
        test
    exit $LASTEXITCODE
}
$Build = Join-Path $Root "build\host-domain"
New-Item -ItemType Directory -Force $Build | Out-Null
$Sources = Get-ChildItem (Join-Path $Root "domain\src\main\kotlin") -Recurse -Filter *.kt | ForEach-Object { $_.FullName }
$Sources += (Join-Path $Root "app\src\main\kotlin\dev\intervaltablet\midi\MidiMessageCodec.kt")
$Sources += (Join-Path $Root "tools\DomainSmoke.kt")
& kotlinc @Sources -Werror -include-runtime -d (Join-Path $Build "domain-smoke.jar")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -jar (Join-Path $Build "domain-smoke.jar")
exit $LASTEXITCODE
