[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [string]$UpdateDescription = "",
    [string]$ApkPath = "",
    [string]$Shortcut = "lingpaozhikong",
    [string]$CliPath = "",
    [switch]$Force,
    [int]$WaitTimeoutSeconds = 300,
    [int]$PollIntervalSeconds = 10
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$localPropertiesPath = Join-Path $projectRoot "local.properties"

function Get-LocalProperty {
    param(
        [Parameter(Mandatory)][string]$Name,
        [switch]$Optional
    )

    if (-not (Test-Path -LiteralPath $localPropertiesPath)) {
        if ($Optional) { return $null }
        throw "Missing $localPropertiesPath."
    }

    $line = Get-Content -LiteralPath $localPropertiesPath -Encoding UTF8 |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -First 1

    if ([string]::IsNullOrWhiteSpace($line)) {
        if ($Optional) { return $null }
        throw "Missing $Name in local.properties."
    }

    return $line.Substring($Name.Length + 1)
}

function Resolve-PgyerCommand {
    param([string]$Explicit)

    if (-not [string]::IsNullOrWhiteSpace($Explicit)) {
        if (-not (Test-Path -LiteralPath $Explicit)) {
            throw "Pgyer CLI was not found at: $Explicit"
        }
        return $Explicit
    }

    $envCli = $env:PGYER_CLI_PATH
    if (-not [string]::IsNullOrWhiteSpace($envCli) -and (Test-Path -LiteralPath $envCli)) {
        return $envCli
    }

    $command = Get-Command pgyer.cmd -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }

    $command = Get-Command pgyer.ps1 -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }

    $command = Get-Command pgyer -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }

    throw "The Pgyer CLI is not installed. Run: npm install -g @pgyer/cli"
}

function Invoke-PgyerCli {
    param([Parameter(Mandatory)][string[]]$CliArguments)

    $stderrFile = [IO.Path]::GetTempFileName()
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $stdout = & $script:cliCommand @CliArguments 2> $stderrFile
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }

    $stderrText = ""
    if (Test-Path -LiteralPath $stderrFile) {
        $stderrText = Get-Content -LiteralPath $stderrFile -Raw -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stderrFile -Force -ErrorAction SilentlyContinue
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        StdOut   = ($stdout | Out-String)
        StdErr   = $stderrText
    }
}

function ConvertTo-JsonPayload {
    param([string]$Text)

    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }
    try {
        return ($Text | ConvertFrom-Json)
    }
    catch {
        return $null
    }
}

function Find-JsonProperty {
    param(
        [Parameter(Mandatory)]$InputObject,
        [Parameter(Mandatory)][string]$Name
    )

    if ($null -eq $InputObject) { return $null }
    if ($InputObject -is [string] -or $InputObject -is [ValueType]) { return $null }

    if ($InputObject -is [System.Collections.IEnumerable] -and
        -not ($InputObject -is [System.Management.Automation.PSCustomObject]) -and
        -not ($InputObject -is [System.Collections.IDictionary])) {
        foreach ($item in $InputObject) {
            $found = Find-JsonProperty -InputObject $item -Name $Name
            if ($null -ne $found) { return $found }
        }
        return $null
    }

    $property = $InputObject.PSObject.Properties[$Name]
    if ($property -and $null -ne $property.Value) { return $property.Value }

    foreach ($child in $InputObject.PSObject.Properties) {
        $found = Find-JsonProperty -InputObject $child.Value -Name $Name
        if ($null -ne $found) { return $found }
    }
    return $null
}

function Get-PublishedState {
    param([Parameter(Mandatory)][string]$AppShortcut)

    $result = Invoke-PgyerCli -CliArguments @("apps", "resolve", $AppShortcut, "--json")
    if ($result.ExitCode -ne 0) { return $null }

    $payload = ConvertTo-JsonPayload -Text $result.StdOut
    if ($null -eq $payload) { return $null }

    $version = Find-JsonProperty -InputObject $payload -Name "version"
    if ([string]::IsNullOrWhiteSpace($version)) { return $null }

    return [pscustomobject]@{
        Version   = "$version"
        VersionNo = "$(Find-JsonProperty -InputObject $payload -Name 'versionNo')"
        BuildKey  = "$(Find-JsonProperty -InputObject $payload -Name 'buildKey')"
    }
}

$apiKey = $env:PGYER_API_KEY
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    $apiKey = Get-LocalProperty -Name "PGYER_API_KEY" -Optional
}
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    throw "Missing PGYER_API_KEY. Set the environment variable or add PGYER_API_KEY to local.properties."
}

$userKey = $env:PGYER_USER_KEY
if ([string]::IsNullOrWhiteSpace($userKey)) {
    $userKey = Get-LocalProperty -Name "PGYER_USER_KEY" -Optional
}

$script:cliCommand = Resolve-PgyerCommand -Explicit $CliPath

$buildScript = Get-Content -LiteralPath (Join-Path $projectRoot "app/build.gradle.kts") -Raw -Encoding UTF8
$versionName = [regex]::Match($buildScript, 'val\s+apkVersionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $versionName) {
    throw "Could not read apkVersionName from app/build.gradle.kts."
}
$versionCode = [regex]::Match($buildScript, 'versionCode\s*=\s*(\d+)').Groups[1].Value

if ([string]::IsNullOrWhiteSpace($UpdateDescription)) {
    $UpdateDescription = $env:PGYER_UPDATE_DESCRIPTION
}
if ([string]::IsNullOrWhiteSpace($UpdateDescription)) {
    $releaseInfoPath = Join-Path $projectRoot "app/src/main/java/com/leapauto/app/AppReleaseInfo.kt"
    if (Test-Path -LiteralPath $releaseInfoPath) {
        $releaseInfo = Get-Content -LiteralPath $releaseInfoPath -Raw -Encoding UTF8
        $notesMatch = [regex]::Match($releaseInfo, 'currentReleaseNotes\s*=\s*"((?:[^"\\]|\\.)*)"')
        if ($notesMatch.Success) {
            $UpdateDescription = $notesMatch.Groups[1].Value.Replace('\n', "`n").Replace('\"', '"')
        }
    }
}

if (-not [string]::IsNullOrWhiteSpace($ApkPath)) {
    if (-not (Test-Path -LiteralPath $ApkPath)) {
        throw "Release APK was not found at: $ApkPath"
    }
    $releaseApk = (Resolve-Path -LiteralPath $ApkPath).Path
}
else {
    if (-not $SkipBuild) {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            & (Join-Path $projectRoot "gradlew.bat") :app:packageReleaseApk
            $packagingExitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousPreference
        }
        if ($packagingExitCode -ne 0) {
            throw "APK packaging failed with exit code $packagingExitCode."
        }
    }

    $releaseApkFile = Get-ChildItem -LiteralPath (Join-Path $projectRoot "app/build/distributions/release") -Filter "*-$versionName.apk" | Select-Object -First 1
    if (-not $releaseApkFile) {
        throw "Signed Release APK was not created for version: $versionName"
    }
    $releaseApk = $releaseApkFile.FullName
}

$previousApiKey = $env:PGYER_API_KEY
$previousUserKey = $env:PGYER_USER_KEY
try {
    $env:PGYER_API_KEY = $apiKey
    if (-not [string]::IsNullOrWhiteSpace($userKey)) {
        $env:PGYER_USER_KEY = $userKey
    }

    $publishedState = Get-PublishedState -AppShortcut $Shortcut
    $alreadyPublished = $false
    if ($publishedState) {
        if ($publishedState.Version -eq $versionName) { $alreadyPublished = $true }
        if ($versionCode -and $publishedState.VersionNo -eq $versionCode) { $alreadyPublished = $true }
    }

    if ($alreadyPublished -and -not $Force) {
        Write-Host "PGYER already publishes version $($publishedState.Version) (buildKey $($publishedState.BuildKey))."
        Write-Host "Skipping upload. Use -Force to publish the same version again."
        Write-Host "https://www.pgyer.com/$Shortcut"
        return
    }

    $uploadArguments = @("upload", $releaseApk)
    if (-not [string]::IsNullOrWhiteSpace($UpdateDescription)) {
        # PGYER's upload endpoint truncates multiline form values after the first line.
        # Keep multiline notes in the app and GitHub Release, but send one readable value to PGYER.
        $pgyerUpdateDescription = $UpdateDescription -replace "`r?`n", "；"
        $uploadArguments += @("--build-update-description", $pgyerUpdateDescription)
    }

    Write-Host "Uploading $releaseApk (version $versionName) to PGYER..."
    $uploadResult = Invoke-PgyerCli -CliArguments $uploadArguments
    if ($uploadResult.ExitCode -ne 0) {
        if (-not [string]::IsNullOrWhiteSpace($uploadResult.StdErr)) {
            Write-Host $uploadResult.StdErr
        }
        throw "Pgyer upload failed with exit code $($uploadResult.ExitCode)."
    }
    if (-not [string]::IsNullOrWhiteSpace($uploadResult.StdOut)) {
        Write-Host $uploadResult.StdOut.Trim()
    }

    $deadline = (Get-Date).AddSeconds($WaitTimeoutSeconds)
    $confirmedState = $null
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds $PollIntervalSeconds
        $state = Get-PublishedState -AppShortcut $Shortcut
        if ($state) {
            $matched = ($state.Version -eq $versionName)
            if (-not $matched -and $versionCode) {
                $matched = ($state.VersionNo -eq $versionCode)
            }
            if ($matched) {
                $confirmedState = $state
                break
            }
        }
    }

    if ($confirmedState) {
        Write-Host "PGYER published version $($confirmedState.Version) (buildKey $($confirmedState.BuildKey))."
    }
    else {
        Write-Warning "Upload finished but the published version could not be confirmed within $WaitTimeoutSeconds seconds. Check https://www.pgyer.com/$Shortcut"
    }
}
finally {
    $env:PGYER_API_KEY = $previousApiKey
    $env:PGYER_USER_KEY = $previousUserKey
}

Write-Host "https://www.pgyer.com/$Shortcut"
