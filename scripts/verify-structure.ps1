$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (Get-Command py -ErrorAction SilentlyContinue) {
    & py -3 (Join-Path $Root "tools\verify_structure.py")
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    & python (Join-Path $Root "tools\verify_structure.py")
} else {
    throw "Python 3.11+ est requis pour verifier la structure."
}
exit $LASTEXITCODE
