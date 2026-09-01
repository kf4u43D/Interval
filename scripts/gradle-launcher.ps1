param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Version = "8.13"
$ExpectedHash = "20F1B1176237254A6FC204D8434196FA11A4CFB387567519C61556E8710AED78"
$Url = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"
$GradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $Root ".gradle" }
$env:GRADLE_USER_HOME = $GradleHome
if (-not $env:ANDROID_USER_HOME) {
    $env:ANDROID_USER_HOME = Join-Path $Root ".android"
}
$CacheRoot = Join-Path $GradleHome "portable-dists"
$InstallRoot = Join-Path $CacheRoot "gradle-$Version"
$GradleExe = Join-Path $InstallRoot "gradle-$Version\bin\gradle.bat"
$ZipPath = Join-Path $CacheRoot "gradle-$Version-bin.zip"

function Assert-ChildPath([string] $Parent, [string] $Child) {
    $ParentPath = [System.IO.Path]::GetFullPath($Parent).TrimEnd('\', '/')
    $ChildPath = [System.IO.Path]::GetFullPath($Child)
    $Prefix = "$ParentPath$([System.IO.Path]::DirectorySeparatorChar)"
    if (-not $ChildPath.StartsWith($Prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Chemin de cache Gradle hors racine autorisee: $ChildPath"
    }
}

function Get-Sha256Hex([string] $Path) {
    $HashCommand = Get-Command Get-FileHash -ErrorAction SilentlyContinue
    if ($HashCommand) {
        return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
    }

    $Stream = [System.IO.File]::OpenRead($Path)
    $Hasher = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [System.BitConverter]::ToString($Hasher.ComputeHash($Stream)).Replace("-", "")
    } finally {
        $Hasher.Dispose()
        $Stream.Dispose()
    }
}

if (-not (Test-Path $GradleExe)) {
    New-Item -ItemType Directory -Force -Path $CacheRoot | Out-Null
    if (-not (Test-Path $ZipPath)) {
        $TempPath = "$ZipPath.part.$PID"
        Write-Host "Telechargement de Gradle $Version..."
        Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $TempPath
        Move-Item -Force $TempPath $ZipPath
    }
    $ActualHash = Get-Sha256Hex $ZipPath
    if ($ActualHash -ne $ExpectedHash) {
        Remove-Item -Force $ZipPath
        throw "SHA-256 incorrect pour Gradle: $ActualHash"
    }
    $TempInstall = "$InstallRoot.part.$PID"
    Assert-ChildPath $CacheRoot $TempInstall
    Assert-ChildPath $CacheRoot $InstallRoot
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $TempInstall
    New-Item -ItemType Directory -Force -Path $TempInstall | Out-Null
    Expand-Archive -Path $ZipPath -DestinationPath $TempInstall -Force
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $InstallRoot
    Move-Item $TempInstall $InstallRoot
}

& $GradleExe @GradleArgs
exit $LASTEXITCODE
