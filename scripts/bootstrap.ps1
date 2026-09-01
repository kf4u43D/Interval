$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "doctor.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot "fetch-dependencies.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot "verify.ps1")
exit $LASTEXITCODE
