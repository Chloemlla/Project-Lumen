param(
    [string]$Arg = ''
)

try {
    $ErrorActionPreference = 'Stop'
    Set-StrictMode -Version Latest
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [Console]::InputEncoding = $utf8NoBom
    [Console]::OutputEncoding = $utf8NoBom
    $OutputEncoding = $utf8NoBom

    $versionName = $null

    $commitHash = (git rev-parse HEAD).Trim()
    $shortHash = $commitHash.Substring(0, 9)
    $utcNow = [DateTime]::UtcNow
    $buildTime = [int]([DateTimeOffset]::new($utcNow).ToUnixTimeSeconds())
    $versionCode = [int]('{0:yy}{1:000}{2:HHmm}' -f $utcNow, $utcNow.DayOfYear, $utcNow)

    $updatedContent = foreach ($line in (Get-Content -Path 'pubspec.yaml' -Encoding UTF8)) {
        if ($line -match '^\s*version:\s*([\d\.]+)') {
            $versionName = $matches[1]
            if ($Arg -eq 'android') {
                $displayName = "$versionName-$shortHash"
            }
            else {
                $displayName = $versionName
            }
            "version: $displayName"
        }
        else {
            $line
        }
    }

    if ($null -eq $versionName) {
        throw 'version not found'
    }

    $updatedContent | Set-Content -Path 'pubspec.yaml' -Encoding UTF8

    # Use displayName (with hash for android) instead of versionName
    $data = @{
        'nexai.name'  = $displayName
        'nexai.code'  = $versionCode
        'nexai.hash'  = $commitHash
        'nexai.time'  = $buildTime
        'nexai.short' = $shortHash
    }

    $data | ConvertTo-Json -Compress | Out-File 'nexai_release.json' -Encoding UTF8

    # Export for GitHub Actions
    if ($env:GITHUB_ENV) {
        Add-Content -Path $env:GITHUB_ENV -Value "version=$displayName"
        Add-Content -Path $env:GITHUB_ENV -Value "VERSION_NAME=$versionName"
        Add-Content -Path $env:GITHUB_ENV -Value "VERSION_CODE=$versionCode"
        Add-Content -Path $env:GITHUB_ENV -Value "SHORT_HASH=$shortHash"
    }

    @{
        version = $displayName
        versionName = $versionName
        versionCode = $versionCode
        versionCodeFormat = 'yyDDDHHmm'
        shortHash = $shortHash
        commitHash = $commitHash
        buildTime = $buildTime
    } | ConvertTo-Json -Compress | Write-Output
}
catch {
    Write-Error "Prebuild Error: $($_.Exception.Message)"
    exit 1
}
