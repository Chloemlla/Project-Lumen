# Download a Lumen Crash SDK GitHub Release into a local Maven-style repo tree.
# No GitHub Packages auth required; needs a public/readable release.
#
# Usage:
#   pwsh ./scripts/Sync-LumenCrashReleaseMaven.ps1 [-Version <ver>] [-OutDir .m2-lumen-crash]

param(
    [string]$OwnerRepo = "Chloemlla/Project-Lumen",
    [string]$Version = "",
    [string]$OutDir = ".m2-lumen-crash"
)

$ErrorActionPreference = "Stop"

if (-not $Version) {
    $resolver = Join-Path $PSScriptRoot "Resolve-LumenCrashLatest.ps1"
    if (Test-Path $resolver) {
        $Version = & $resolver -OwnerRepo $OwnerRepo
    } else {
        throw "Version is required when Resolve-LumenCrashLatest.ps1 is unavailable."
    }
}

$headers = @{
    Accept = "application/vnd.github+json"
    "User-Agent" = "lumen-crash-release-sync"
}
$token = $env:GH_TOKEN
if (-not $token) { $token = $env:GITHUB_TOKEN }
if ($token) { $headers.Authorization = "Bearer $token" }

$tag = "lumen-crash-v$Version"
$release = Invoke-RestMethod `
    -Uri "https://api.github.com/repos/$OwnerRepo/releases/tags/$tag" `
    -Headers $headers

$assets = @($release.assets | Where-Object { $_.name -match '\.(aar|pom|module|jar)$' })
if (-not $assets -or $assets.Count -eq 0) {
    throw "No Maven assets found for tag $tag"
}

$bundleDir = Join-Path $OutDir "com/chloemlla/lumen/lumen-crash/$Version"
$coreDir = Join-Path $OutDir "com/chloemlla/lumen/lumen-crash-core/$Version"
New-Item -ItemType Directory -Force -Path $bundleDir | Out-Null
New-Item -ItemType Directory -Force -Path $coreDir | Out-Null

foreach ($asset in $assets) {
    $targetDir = if ($asset.name -like "lumen-crash-core-*") { $coreDir } else { $bundleDir }
    $target = Join-Path $targetDir $asset.name
    Write-Host "Downloading $($asset.name)"
    Invoke-WebRequest -Uri $asset.browser_download_url -Headers $headers -OutFile $target
}

Write-Host "Synced release $Version into $OutDir"
Write-Host "Gradle repo example:"
Write-Host "  maven { url = uri(`"file://$([System.IO.Path]::GetFullPath($OutDir).Replace('\','/'))`") }"
Write-Host "  implementation(`"com.chloemlla.lumen:lumen-crash:$Version`")"
