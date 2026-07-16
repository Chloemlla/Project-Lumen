# Resolve the latest Lumen Crash SDK main auto-release from GitHub Releases.
# Filters tags with prefix lumen-crash-v so non-SDK releases are ignored.

param(
    [string]$OwnerRepo = "Chloemlla/Project-Lumen"
)

$headers = @{
    Accept = "application/vnd.github+json"
    "User-Agent" = "lumen-crash-version-resolver"
}
$token = $env:GH_TOKEN
if (-not $token) { $token = $env:GITHUB_TOKEN }
if ($token) { $headers.Authorization = "Bearer $token" }

$releases = Invoke-RestMethod `
    -Uri "https://api.github.com/repos/$OwnerRepo/releases?per_page=100" `
    -Headers $headers

$latest = $releases |
    Where-Object { -not $_.draft -and $_.tag_name -like "lumen-crash-v*" } |
    Sort-Object {
        if ($_.published_at) { [datetime]$_.published_at } else { [datetime]$_.created_at }
    } -Descending |
    Select-Object -First 1

if (-not $latest) {
    throw "No lumen-crash release found"
}

$version = $latest.tag_name -replace '^lumen-crash-v', ''
Write-Output $version
