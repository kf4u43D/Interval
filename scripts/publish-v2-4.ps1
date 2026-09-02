<#
.SYNOPSIS
Valide et publie la branche V2.4, puis prepare ou cree sa Pull Request GitHub.

.DESCRIPTION
Le script refuse un arbre Git sale, une branche incorrecte, une base absente ou un
historique divergent. Il execute scripts/verify.ps1 par defaut, effectue uniquement un
push fast-forward normal, puis :

- cree/retrouve la Pull Request si GitHub CLI est installe et authentifie ;
- sinon copie la description et ouvre la page Compare dans le navigateur.

Aucun token, remote ou reglage Git global n'est cree. Aucune release n'est publiee.

.EXAMPLE
.\scripts\publish-v2-4.ps1

.EXAMPLE
.\scripts\publish-v2-4.ps1 -DryRun -SkipVerify

.EXAMPLE
.\scripts\publish-v2-4.ps1 -Yes -EnableAutoMerge
#>
[CmdletBinding()]
param(
    [ValidateNotNullOrEmpty()]
    [string] $Branch = "v2.4",

    [ValidateNotNullOrEmpty()]
    [string] $Base = "main",

    [ValidateNotNullOrEmpty()]
    [string] $Remote = "origin",

    [ValidateNotNullOrEmpty()]
    [string] $Title = "V2.4 - workstation surface, synth and configurable arpeggiator",

    [switch] $SkipVerify,
    [switch] $DryRun,
    [switch] $Yes,
    [switch] $EnableAutoMerge,
    [switch] $NoBrowser
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$SafeRoot = $Root.Replace("\", "/")
$GitSafety = @("-c", "safe.directory=$SafeRoot")

function Invoke-GitCapture {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    $Lines = @(& git @GitSafety @Arguments 2>&1)
    $ExitCode = $LASTEXITCODE
    if ($ExitCode -ne 0) {
        $Message = ($Lines | ForEach-Object { "$_" }) -join [Environment]::NewLine
        throw "git $($Arguments -join ' ') a echoue ($ExitCode). $Message"
    }
    return (($Lines | ForEach-Object { "$_" }) -join [Environment]::NewLine).Trim()
}

function Invoke-GitVisible {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    & git @GitSafety @Arguments
    $ExitCode = $LASTEXITCODE
    if ($ExitCode -ne 0) {
        throw "git $($Arguments -join ' ') a echoue ($ExitCode)."
    }
}

function Assert-GitRef {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    if ($Name.StartsWith("-") -or $Name.Contains("..") -or $Name.Contains("@{")) {
        throw "$Label invalide : $Name"
    }
    $null = Invoke-GitCapture -Arguments @("check-ref-format", "--branch", $Name)
}

function Get-GitHubSlug {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RemoteUrl
    )

    $Patterns = @(
        "^https://github\.com/(?<owner>[A-Za-z0-9_.-]+)/(?<repo>[A-Za-z0-9_.-]+?)(?:\.git)?/?$",
        "^git@github\.com:(?<owner>[A-Za-z0-9_.-]+)/(?<repo>[A-Za-z0-9_.-]+?)(?:\.git)?$",
        "^ssh://git@github\.com/(?<owner>[A-Za-z0-9_.-]+)/(?<repo>[A-Za-z0-9_.-]+?)(?:\.git)?/?$"
    )

    foreach ($Pattern in $Patterns) {
        if ($RemoteUrl -match $Pattern) {
            return "$($Matches.owner)/$($Matches.repo)"
        }
    }
    throw "Le remote selectionne n'est pas une URL GitHub HTTPS/SSH reconnue."
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git est absent du PATH."
}

Set-Location $Root
if (-not (Test-Path (Join-Path $Root ".git"))) {
    throw "Le workspace n'est pas un depot Git."
}

Assert-GitRef -Name $Branch -Label "Branche"
Assert-GitRef -Name $Base -Label "Base"
if ($Branch -eq $Base) {
    throw "La branche de livraison doit etre differente de la base."
}
if ($Remote.StartsWith("-") -or $Remote -notmatch "^[A-Za-z0-9][A-Za-z0-9._-]*$") {
    throw "Remote invalide : $Remote"
}

$Dirty = Invoke-GitCapture -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
if ($Dirty) {
    throw "L'arbre Git n'est pas propre. Committer ou ranger les changements avant publication. $Dirty"
}

$CurrentBranch = Invoke-GitCapture -Arguments @("branch", "--show-current")
if ($CurrentBranch -ne $Branch) {
    throw "Branche active : '$CurrentBranch'. Basculer d'abord sur '$Branch'."
}

$RemoteUrl = Invoke-GitCapture -Arguments @("remote", "get-url", $Remote)
$GitHubSlug = Get-GitHubSlug -RemoteUrl $RemoteUrl
$EncodedBase = [Uri]::EscapeDataString($Base)
$EncodedBranch = [Uri]::EscapeDataString($Branch)
$CompareUrl = "https://github.com/{0}/compare/{1}...{2}?expand=1" -f $GitHubSlug, $EncodedBase, $EncodedBranch

$PullRequestBody = @'
## Resume

Cette Pull Request rassemble les evolutions V2 a V2.4 d'Interval Tablet.

### V2.4

- changement immediat de gamme et d'accord sur les pads maintenus ;
- navigation superieure Interval, MIDI, Synthe et Arpegiateur ;
- disposition deux mains avec strummer vertical sur trois octaves ;
- arpegiateur autonome configurable : ordre, 1-3 octaves, motif huit pas et gate ;
- BPM et signature rythmique ;
- synthe plein ecran avec 28 parametres, deux ADSR, drive, LFO assignable,
  delay synchronise et six presets originaux ;
- bouton Mute et informations MIDI In/Out reunies dans la page MIDI ;
- migrations Settings v6, Preset v5 et banque v4.

## Validation

- Domaine Kotlin : 143/143 tests
- Application JVM : 163/163 tests
- Total JVM : 306/306
- Tests natifs : 2/2
- Tests instrumentes SM-X620/API 36 : 8/8
- Runtime natif verifie sur quatre ABI
- V2.4 Performance installee avec la derniere V1
- Audio apres stress : 48 kHz, zero xrun/drop/reprise

## Artefact teste

- Version : 0.2.4-dev-performance
- Taille : 9 276 296 octets
- SHA-256 : ECDDE9FC6E4FBD0811EDA0C24CAB847281599C5A7C94EC785DCBB6D539FC2A82

## Limites de certification

USB MIDI reel, TalkBack, vrai multi-touch, loopback de latence et soak audio
de 60 minutes restent a valider separement.
'@

Write-Host "Depot       : $GitHubSlug"
Write-Host "Branche     : $Branch"
Write-Host "Base        : $Base"
Write-Host "Verification: $(if ($SkipVerify) { 'ignoree sur demande' } else { 'complete' })"

if ($DryRun) {
    Write-Host ""
    Write-Host "[DRY-RUN] Aucun fetch, push, navigateur ou appel GitHub ne sera execute."
    Write-Host "[DRY-RUN] Push prevu : git push --set-upstream $Remote $Branch"
    Write-Host "[DRY-RUN] Pull Request : $CompareUrl"
    if ($EnableAutoMerge) {
        Write-Host "[DRY-RUN] Auto-merge demande apres validation des checks."
    }
    exit 0
}

if (-not $Yes) {
    $Answer = Read-Host "Publier '$Branch' vers '$Remote' et preparer la Pull Request ? [o/N]"
    if ($Answer -notmatch "^(o|oui|y|yes)$") {
        Write-Host "Publication annulee sans modification."
        exit 0
    }
}

$GhCommand = Get-Command gh -ErrorAction SilentlyContinue
$GhAuthenticated = $false
if ($null -ne $GhCommand) {
    & $GhCommand.Source auth status --hostname github.com *> $null
    $GhAuthenticated = ($LASTEXITCODE -eq 0)
}
if ($EnableAutoMerge -and -not $GhAuthenticated) {
    throw "-EnableAutoMerge exige GitHub CLI installe et authentifie (gh auth login)."
}

if (-not $SkipVerify) {
    Write-Host ""
    Write-Host "Execution du gate complet..."
    $PowerShellExecutable = (Get-Process -Id $PID).Path
    & $PowerShellExecutable -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root "scripts\verify.ps1")
    if ($LASTEXITCODE -ne 0) {
        throw "Le gate complet a echoue. Aucun push n'a ete effectue."
    }
}

$DirtyAfterVerify = Invoke-GitCapture -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
if ($DirtyAfterVerify) {
    throw "Le gate a laisse des changements versionnables. Aucun push n'a ete effectue. $DirtyAfterVerify"
}

Write-Host ""
Write-Host "Actualisation des references distantes..."
Invoke-GitVisible -Arguments @("fetch", "--prune", $Remote)

$RemoteBase = "$Remote/$Base"
$null = Invoke-GitCapture -Arguments @("rev-parse", "--verify", "$RemoteBase^{commit}")

& git @GitSafety merge-base --is-ancestor $RemoteBase $Branch
$AncestorExitCode = $LASTEXITCODE
if ($AncestorExitCode -eq 1) {
    throw "'$Branch' n'est pas fondee sur '$RemoteBase'. Aucun push automatique n'est autorise."
}
if ($AncestorExitCode -ne 0) {
    throw "Impossible de verifier l'ascendance entre '$RemoteBase' et '$Branch'."
}

$RemoteBranchRef = "refs/remotes/$Remote/$Branch"
& git @GitSafety show-ref --verify --quiet $RemoteBranchRef
$RemoteBranchExitCode = $LASTEXITCODE
if ($RemoteBranchExitCode -eq 0) {
    & git @GitSafety merge-base --is-ancestor "$Remote/$Branch" $Branch
    $RemoteAncestorExitCode = $LASTEXITCODE
    if ($RemoteAncestorExitCode -eq 1) {
        throw "La branche distante contient des commits absents localement. Aucun push force ne sera effectue."
    }
    if ($RemoteAncestorExitCode -ne 0) {
        throw "Impossible de comparer la branche locale et distante."
    }
} elseif ($RemoteBranchExitCode -ne 1) {
    throw "Impossible de verifier l'existence de la branche distante."
}

$AheadCountText = Invoke-GitCapture -Arguments @("rev-list", "--count", "$RemoteBase..$Branch")
$AheadCount = [int] $AheadCountText
if ($AheadCount -le 0) {
    throw "Aucun commit de '$Branch' n'est en avance sur '$RemoteBase'."
}

Write-Host "Publication de $AheadCount commit(s), sans force-push..."
Invoke-GitVisible -Arguments @("push", "--set-upstream", $Remote, $Branch)

$PullRequestUrl = $null
if ($GhAuthenticated) {
    $ExistingJsonLines = @(& $GhCommand.Source pr list --repo $GitHubSlug --head $Branch --base $Base --state open --json number,url 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Impossible de rechercher une Pull Request existante avec GitHub CLI."
    }

    $ExistingJson = ($ExistingJsonLines | ForEach-Object { "$_" }) -join [Environment]::NewLine
    $ExistingPullRequests = @($ExistingJson | ConvertFrom-Json)
    if ($ExistingPullRequests.Count -gt 0) {
        $PullRequestUrl = [string] $ExistingPullRequests[0].url
        Write-Host "Pull Request existante : $PullRequestUrl"
    } else {
        $CreateOutput = @(& $GhCommand.Source pr create --repo $GitHubSlug --base $Base --head $Branch --title $Title --body $PullRequestBody 2>&1)
        if ($LASTEXITCODE -ne 0) {
            $CreateMessage = ($CreateOutput | ForEach-Object { "$_" }) -join [Environment]::NewLine
            throw "Creation de la Pull Request impossible. $CreateMessage"
        }
        $PullRequestUrl = (($CreateOutput | ForEach-Object { "$_" }) -join [Environment]::NewLine).Trim()
        Write-Host "Pull Request creee : $PullRequestUrl"
    }

    if ($EnableAutoMerge) {
        & $GhCommand.Source pr merge $PullRequestUrl --repo $GitHubSlug --merge --auto --delete-branch
        if ($LASTEXITCODE -ne 0) {
            throw "La Pull Request existe, mais l'auto-merge n'a pas pu etre active."
        }
        Write-Host "Auto-merge active : fusion apres reussite des protections de branche."
    }
} else {
    try {
        Set-Clipboard -Value $PullRequestBody
        Write-Host "Description de Pull Request copiee dans le presse-papiers."
    } catch {
        Write-Warning "Presse-papiers indisponible ; la description reste affichee dans ce script."
    }
    $PullRequestUrl = $CompareUrl
    Write-Host "GitHub CLI absent ou non authentifie."
    Write-Host "Creer la Pull Request ici : $PullRequestUrl"
}

if (-not $NoBrowser) {
    Start-Process -FilePath $PullRequestUrl
}

Write-Host ""
Write-Host "Termine. Ne pas creer de GitHub Release tant que la version reste '-dev',"
Write-Host "que la signature de publication et la licence ne sont pas finalisees."
