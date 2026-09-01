$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$DistributionSha = "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
$WrapperSha = "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"

& (Join-Path $Root "gradlew.bat") wrapper --gradle-version 8.13 --distribution-type bin --gradle-distribution-sha256-sum $DistributionSha
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$Jar = Join-Path $Root "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $Jar)) { throw "gradle-wrapper.jar n'a pas ete genere." }
$Actual = (Get-FileHash -Algorithm SHA256 $Jar).Hash.ToLowerInvariant()
if ($Actual -ne $WrapperSha) { throw "Empreinte inattendue du Gradle Wrapper: $Actual" }
Write-Host "Gradle Wrapper officiel 8.13 genere et verifie."
