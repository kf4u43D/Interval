param(
    [Parameter(Mandatory = $true)]
    [string] $Serial,

    [Parameter(Mandatory = $true)]
    [string] $Label,

    [string] $Package = "dev.intervaltablet.benchmark",

    [ValidateRange(5, 600)]
    [int] $DurationSeconds = 30,

    [ValidateRange(0, 120)]
    [int] $WarmupSeconds = 10,

    [string] $OutputDirectory = "",

    [string] $AdbPath = "",

    [string] $ExpectedApkPath = "",

    [ValidateRange(0, 240)]
    [double] $ExpectedRefreshRate = 90.0,

    [switch] $Force
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $Root "docs\implementation\evidence\gfxinfo"
}

if (-not $AdbPath) {
    $Sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
    if ($Sdk) {
        $Candidate = Join-Path $Sdk "platform-tools\adb.exe"
        if (Test-Path -LiteralPath $Candidate) { $AdbPath = $Candidate }
    }
}

if (-not $AdbPath) {
    $AdbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($AdbCommand) { $AdbPath = $AdbCommand.Source }
}

if (-not $AdbPath -or -not (Test-Path -LiteralPath $AdbPath)) {
    throw "adb introuvable. Fournir -AdbPath ou ANDROID_SDK_ROOT."
}

$SafeLabel = $Label -replace '[^A-Za-z0-9._-]', '_'
if (-not $SafeLabel) { throw "Label vide après normalisation." }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$RawPath = Join-Path $OutputDirectory "$SafeLabel-gfxinfo.txt"
$SummaryPath = Join-Path $OutputDirectory "$SafeLabel-summary.json"
$MetadataPath = Join-Path $OutputDirectory "$SafeLabel-device.txt"

if (-not $Force) {
    $Existing = @($RawPath, $SummaryPath, $MetadataPath) | Where-Object {
        Test-Path -LiteralPath $_
    }
    if ($Existing.Count -gt 0) {
        throw "Preuve existante : $($Existing -join ', '). Choisir un autre Label ou fournir -Force."
    }
}

function Invoke-Adb([string[]] $Arguments) {
    $Output = & $AdbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb a échoué ($LASTEXITCODE): $($Arguments -join ' ')`n$($Output -join "`n")"
    }
    return @($Output)
}

$PackagePath = Invoke-Adb @("shell", "pm", "path", $Package)
$InstalledApkPaths = @($PackagePath | ForEach-Object {
    if ($_ -match '^package:(.+)$') { $Matches[1].Trim() }
})
if ($InstalledApkPaths.Count -eq 0) {
    throw "Package non installé sur la cible: $Package"
}
$InstalledBaseApk = $InstalledApkPaths |
    Where-Object { $_ -match '(^|/)base\.apk$' } |
    Select-Object -First 1
if (-not $InstalledBaseApk) { $InstalledBaseApk = $InstalledApkPaths[0] }

if (-not $ExpectedApkPath -and $Package -eq "dev.intervaltablet.benchmark") {
    $ExpectedApkPath = Join-Path $Root "app\build\outputs\apk\benchmark\app-benchmark.apk"
}

$ExpectedApkHash = $null
if ($ExpectedApkPath) {
    if (-not [System.IO.Path]::IsPathRooted($ExpectedApkPath)) {
        $ExpectedApkPath = Join-Path $Root $ExpectedApkPath
    }
    if (-not (Test-Path -LiteralPath $ExpectedApkPath)) {
        throw "APK attendu introuvable : $ExpectedApkPath"
    }
    $ExpectedApkPath = (Resolve-Path -LiteralPath $ExpectedApkPath).Path
    $ExpectedApkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $ExpectedApkPath).Hash.ToUpperInvariant()
}

$RemoteHashOutput = Invoke-Adb @("shell", "sha256sum", $InstalledBaseApk)
$RemoteHashMatch = [regex]::Match(($RemoteHashOutput -join "`n"), '(?im)^([0-9a-f]{64})\s')
if (-not $RemoteHashMatch.Success) {
    throw "Impossible de calculer le SHA-256 de l'APK installé."
}
$InstalledApkHash = $RemoteHashMatch.Groups[1].Value.ToUpperInvariant()
if ($ExpectedApkHash -and $InstalledApkHash -ne $ExpectedApkHash) {
    throw "APK installé différent de l'APK attendu ($InstalledApkHash != $ExpectedApkHash)."
}

$PackageDump = Invoke-Adb @("shell", "dumpsys", "package", $Package)

function First-PackageLine([string] $Pattern) {
    $Match = $PackageDump | Select-String -Pattern $Pattern | Select-Object -First 1
    if ($null -eq $Match) { return "" }
    return $Match.Line.Trim()
}

function Get-ForegroundActivity {
    $Match = Invoke-Adb @("shell", "dumpsys", "activity", "activities") |
        Select-String -Pattern '^\s*(?:topResumedActivity|mResumedActivity|ResumedActivity)\s*[:=]' |
        Select-Object -First 1
    if ($null -eq $Match) { return "" }
    return $Match.Line.Trim()
}

function Assert-Foreground {
    $Foreground = Get-ForegroundActivity
    if (-not $Foreground -or $Foreground -notmatch [regex]::Escape($Package)) {
        throw "Le package mesuré n'est pas l'activité au premier plan : $Foreground"
    }
    return $Foreground
}

function Get-DisplaySnapshot {
    return ((Invoke-Adb @('shell', 'dumpsys', 'display') |
        Select-String -Pattern '^\s*mActiveRenderFrameRate=|^\s*mActiveModeId=|^\s*mRefreshRate=' |
        Select-Object -First 8) -join ' | ')
}

function Assert-RefreshRate([string] $DisplaySnapshot) {
    if ($ExpectedRefreshRate -le 0.0) { return }
    $Match = [regex]::Match($DisplaySnapshot, '(?:mActiveRenderFrameRate|renderFrameRate)=([0-9.]+)')
    if (-not $Match.Success) {
        throw "Taux de rendu actif introuvable dans dumpsys display."
    }
    $Actual = [double]::Parse($Match.Groups[1].Value, [System.Globalization.CultureInfo]::InvariantCulture)
    if ([Math]::Abs($Actual - $ExpectedRefreshRate) -gt 0.1) {
        throw "Taux de rendu inattendu : $Actual Hz au lieu de $ExpectedRefreshRate Hz."
    }
}

$ForegroundBefore = Assert-Foreground
$DisplayBefore = Get-DisplaySnapshot
Assert-RefreshRate $DisplayBefore
$BatteryBefore = ((Invoke-Adb @('shell', 'dumpsys', 'battery') |
    Select-String -Pattern 'level:|temperature:|status:|powered:' |
    Select-Object -First 8) -join ' | ')
$ThermalBefore = ((Invoke-Adb @('shell', 'dumpsys', 'thermalservice') |
    Select-String -Pattern 'Thermal Status|Status:' |
    Select-Object -First 4) -join ' | ')

$Metadata = @(
    "capturedUtc=$([DateTimeOffset]::UtcNow.ToString('O'))"
    "package=$Package"
    "durationSeconds=$DurationSeconds"
    "warmupSeconds=$WarmupSeconds"
    "installedApkSha256=$InstalledApkHash"
    "expectedApkSha256=$ExpectedApkHash"
    "versionCode=$(First-PackageLine '^\s*versionCode=')"
    "versionName=$(First-PackageLine '^\s*versionName=')"
    "packageFlags=$(First-PackageLine '^\s*pkgFlags=')"
    "privateFlags=$(First-PackageLine '^\s*privateFlags=')"
    "lastUpdateTime=$(First-PackageLine '^\s*lastUpdateTime=')"
    "model=$((Invoke-Adb @('shell', 'getprop', 'ro.product.model')) -join '')"
    "sdk=$((Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk')) -join '')"
    "buildFingerprint=$((Invoke-Adb @('shell', 'getprop', 'ro.build.fingerprint')) -join '')"
    "foregroundBefore=$ForegroundBefore"
    "refreshBefore=$DisplayBefore"
    "batteryBefore=$BatteryBefore"
    "thermalBefore=$ThermalBefore"
)

if ($WarmupSeconds -gt 0) { Start-Sleep -Seconds $WarmupSeconds }
$ForegroundAtStart = Assert-Foreground
$DisplayAtStart = Get-DisplaySnapshot
Assert-RefreshRate $DisplayAtStart
Invoke-Adb @("shell", "dumpsys", "gfxinfo", $Package, "reset") | Out-Null
Start-Sleep -Seconds $DurationSeconds
$Raw = Invoke-Adb @("shell", "dumpsys", "gfxinfo", $Package, "framestats")
[System.IO.File]::WriteAllLines($RawPath, $Raw, [System.Text.UTF8Encoding]::new($false))
$ForegroundAfter = Assert-Foreground
$DisplayAfter = Get-DisplaySnapshot
Assert-RefreshRate $DisplayAfter
$BatteryAfter = ((Invoke-Adb @('shell', 'dumpsys', 'battery') |
    Select-String -Pattern 'level:|temperature:|status:|powered:' |
    Select-Object -First 8) -join ' | ')
$ThermalAfter = ((Invoke-Adb @('shell', 'dumpsys', 'thermalservice') |
    Select-String -Pattern 'Thermal Status|Status:' |
    Select-Object -First 4) -join ' | ')
$Metadata += @(
    "foregroundAtStart=$ForegroundAtStart"
    "foregroundAfter=$ForegroundAfter"
    "refreshAtStart=$DisplayAtStart"
    "refreshAfter=$DisplayAfter"
    "batteryAfter=$BatteryAfter"
    "thermalAfter=$ThermalAfter"
)
[System.IO.File]::WriteAllLines($MetadataPath, $Metadata, [System.Text.UTF8Encoding]::new($false))

function First-RegexValue([string] $Pattern, [int] $Group = 1) {
    foreach ($Line in $Raw) {
        $Match = [regex]::Match($Line, $Pattern)
        if ($Match.Success) { return $Match.Groups[$Group].Value }
    }
    return $null
}

$Headers = $null
$RequiredHeadersSeen = $false
$DurationsMs = [System.Collections.Generic.List[double]]::new()
$DeadlineMisses = 0
$FirstIntendedVsync = $null
$LastFrameCompleted = $null
foreach ($Line in $Raw) {
    if ($Line.StartsWith("Flags,FrameTimelineVsyncId,")) {
        $Headers = $Line.TrimEnd(',').Split(',')
        $RequiredHeaders = @('Flags', 'IntendedVsync', 'FrameDeadline', 'FrameCompleted')
        $MissingHeaders = @($RequiredHeaders | Where-Object { $_ -notin $Headers })
        if ($MissingHeaders.Count -gt 0) {
            throw "Colonnes framestats manquantes : $($MissingHeaders -join ', ')"
        }
        $RequiredHeadersSeen = $true
        continue
    }
    if ($null -eq $Headers -or -not ($Line -match '^\d+,')) { continue }
    $Values = $Line.TrimEnd(',').Split(',')
    if ($Values.Count -ne $Headers.Count) { continue }
    $Columns = @{}
    for ($Index = 0; $Index -lt $Headers.Count; $Index++) {
        $Columns[$Headers[$Index]] = $Values[$Index]
    }
    if ([int64]$Columns.Flags -ne 0L) { continue }
    $IntendedVsync = [int64]$Columns.IntendedVsync
    $FrameCompleted = [int64]$Columns.FrameCompleted
    if ($null -eq $FirstIntendedVsync -or $IntendedVsync -lt $FirstIntendedVsync) {
        $FirstIntendedVsync = $IntendedVsync
    }
    if ($null -eq $LastFrameCompleted -or $FrameCompleted -gt $LastFrameCompleted) {
        $LastFrameCompleted = $FrameCompleted
    }
    $Duration = ($FrameCompleted - $IntendedVsync) / 1000000.0
    if ($Duration -ge 0.0) { $DurationsMs.Add($Duration) }
    if ($FrameCompleted -gt [int64]$Columns.FrameDeadline) {
        $DeadlineMisses++
    }
}
if (-not $RequiredHeadersSeen) { throw "Aucune section framestats exploitable trouvée." }

function Percentile([double[]] $Values, [double] $Percent) {
    if ($Values.Count -eq 0) { return $null }
    $Sorted = @($Values | Sort-Object)
    $Rank = [Math]::Ceiling(($Percent / 100.0) * $Sorted.Count) - 1
    $Rank = [Math]::Max(0, [Math]::Min($Sorted.Count - 1, $Rank))
    return [Math]::Round([double]$Sorted[$Rank], 3)
}

$PlatformFrames = First-RegexValue '^Total frames rendered:\s+(\d+)'
$PlatformJank = First-RegexValue '^Janky frames:\s+(\d+)\s+\(([0-9.,]+)%\)' 1
$PlatformJankPercent = First-RegexValue '^Janky frames:\s+(\d+)\s+\(([0-9.,]+)%\)' 2
$PlatformLegacyJank = First-RegexValue '^Janky frames \(legacy\):\s+(\d+)\s+\(([0-9.,]+)%\)' 1
$PlatformLegacyPercent = First-RegexValue '^Janky frames \(legacy\):\s+(\d+)\s+\(([0-9.,]+)%\)' 2

function Optional-Int([string] $Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return $null }
    return [int]$Value
}

$PlatformFrameCount = Optional-Int $PlatformFrames
$HasPlatformFrames = $null -ne $PlatformFrameCount -and $PlatformFrameCount -gt 0
$RawTruncated = $null -ne $PlatformFrameCount -and $DurationsMs.Count -lt $PlatformFrameCount
$TailDeadlineMissPercent = if ($DurationsMs.Count) {
    [Math]::Round(100.0 * $DeadlineMisses / $DurationsMs.Count, 2)
} else { $null }
$TailSpanSeconds = if ($null -ne $FirstIntendedVsync -and $null -ne $LastFrameCompleted) {
    [Math]::Round(($LastFrameCompleted - $FirstIntendedVsync) / 1000000000.0, 3)
} else { $null }

$Summary = [ordered]@{
    schemaVersion = 2
    label = $Label
    package = $Package
    installedApkSha256 = $InstalledApkHash
    durationSeconds = $DurationSeconds
    validFrameCount = $DurationsMs.Count
    rawTruncated = $RawTruncated
    tailSpanSeconds = $TailSpanSeconds
    tailDeadlineMissCount = $DeadlineMisses
    tailDeadlineMissPercent = $TailDeadlineMissPercent
    tailP50Ms = Percentile $DurationsMs.ToArray() 50
    tailP90Ms = Percentile $DurationsMs.ToArray() 90
    tailP95Ms = Percentile $DurationsMs.ToArray() 95
    tailP99Ms = Percentile $DurationsMs.ToArray() 99
    derivedDeadlineMissCount = if ($RawTruncated) { $null } else { $DeadlineMisses }
    derivedDeadlineMissPercent = if ($RawTruncated) { $null } else { $TailDeadlineMissPercent }
    derivedP50Ms = if ($RawTruncated) { $null } else { Percentile $DurationsMs.ToArray() 50 }
    derivedP90Ms = if ($RawTruncated) { $null } else { Percentile $DurationsMs.ToArray() 90 }
    derivedP95Ms = if ($RawTruncated) { $null } else { Percentile $DurationsMs.ToArray() 95 }
    derivedP99Ms = if ($RawTruncated) { $null } else { Percentile $DurationsMs.ToArray() 99 }
    platformFrameCount = $PlatformFrameCount
    platformJankyFrameCount = Optional-Int $PlatformJank
    platformJankyPercent = if ($PlatformJankPercent) {
        [double]($PlatformJankPercent -replace ',', '.')
    } else { $null }
    platformLegacyJankyFrameCount = Optional-Int $PlatformLegacyJank
    platformLegacyJankyPercent = if ($PlatformLegacyPercent) {
        [double]($PlatformLegacyPercent -replace ',', '.')
    } else { $null }
    platformP50Ms = if ($HasPlatformFrames) { Optional-Int (First-RegexValue '^50th percentile:\s+(\d+)ms') } else { $null }
    platformP90Ms = if ($HasPlatformFrames) { Optional-Int (First-RegexValue '^90th percentile:\s+(\d+)ms') } else { $null }
    platformP95Ms = if ($HasPlatformFrames) { Optional-Int (First-RegexValue '^95th percentile:\s+(\d+)ms') } else { $null }
    platformP99Ms = if ($HasPlatformFrames) { Optional-Int (First-RegexValue '^99th percentile:\s+(\d+)ms') } else { $null }
    gpuP50Ms = if ($HasPlatformFrames) { Optional-Int (First-RegexValue '^50th gpu percentile:\s+(\d+)ms') } else { $null }
    gpuP90Ms = if ($HasPlatformFrames) { Optional-Int (First-RegexValue '^90th gpu percentile:\s+(\d+)ms') } else { $null }
    rawFile = [System.IO.Path]::GetFileName($RawPath)
    metadataFile = [System.IO.Path]::GetFileName($MetadataPath)
}

$Json = $Summary | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText($SummaryPath, "$Json`n", [System.Text.UTF8Encoding]::new($false))
Write-Output $Json
