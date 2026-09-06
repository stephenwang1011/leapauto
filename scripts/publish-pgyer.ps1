[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [string]$UpdateDescription = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$localPropertiesPath = Join-Path $projectRoot "local.properties"

function Get-LocalProperty {
    param([Parameter(Mandatory)][string]$Name)

    $line = Get-Content -LiteralPath $localPropertiesPath |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -First 1

    if ([string]::IsNullOrWhiteSpace($line)) {
        throw "Missing $Name in local.properties."
    }

    return $line.Substring($Name.Length + 1)
}

$apiKey = Get-LocalProperty "PGYER_API_KEY"
if (-not (Get-Command pgyer -ErrorAction SilentlyContinue)) {
    throw "The Pgyer CLI is not installed. Run: npm install -g @pgyer/cli"
}

if (-not $SkipBuild) {
    & (Join-Path $projectRoot "gradlew.bat") :app:packageReleaseApk
    if ($LASTEXITCODE -ne 0) {
        throw "APK packaging failed."
    }
}

$buildScript = Get-Content -LiteralPath (Join-Path $projectRoot "app/build.gradle.kts") -Raw
$versionMatch = [regex]::Match($buildScript, 'val\s+apkVersionName\s*=\s*"([^"]+)"')
if (-not $versionMatch.Success) {
    throw "Could not read apkVersionName from app/build.gradle.kts."
}

$targetVersion = $versionMatch.Groups[1].Value
$releaseApkFile = Get-ChildItem -LiteralPath (Join-Path $projectRoot "app/build/distributions/release") -Filter "*-$targetVersion.apk" | Select-Object -First 1
if (-not $releaseApkFile) {
    throw "Signed Release APK was not created for version: $targetVersion"
}
$releaseApk = $releaseApkFile.FullName

$previousApiKey = $env:PGYER_API_KEY
try {
    $env:PGYER_API_KEY = $apiKey
    $uploadArguments = @("upload", $releaseApk)
    if (-not [string]::IsNullOrWhiteSpace($UpdateDescription)) {
        $uploadArguments += @("--build-update-description", $UpdateDescription)
    }
    & pgyer @uploadArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Pgyer upload failed."
    }
}
finally {
    $env:PGYER_API_KEY = $previousApiKey
}
