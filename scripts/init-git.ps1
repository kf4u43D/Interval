param([Parameter(Mandatory = $true)][string] $RemoteUrl)
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $Root
if (-not (Test-Path ".git")) { git init -b main }
$Existing = git remote get-url origin 2>$null
if ($LASTEXITCODE -eq 0) {
    if ($Existing -ne $RemoteUrl) {
        throw "Le remote origin existe deja: $Existing. Aucune modification; remplacer explicitement avec git remote set-url si necessaire."
    }
    Write-Host "Remote origin deja configure avec cette URL."
} else {
    git remote add origin $RemoteUrl
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "Remote origin ajoute: $(git remote get-url origin)"
}
Write-Host "Aucun commit ni push n'a ete effectue."
