param(
    [switch]$Verbose,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'

function Get-Timestamp {
    return (Get-Date -Format 'yyyy-MM-ddTHH:mm:ssZ')
}

function Log-Info($Message) {
    Write-Host "[$(Get-Timestamp)] [ INFO] $Message"
}

function Log-Warn($Message) {
    Write-Host "[$(Get-Timestamp)] [ WARN] $Message"
}

function Log-Error($Message) {
    Write-Host "[$(Get-Timestamp)] [ERROR] $Message"
}

function Log-Debug($Message) {
    if ($Verbose) {
        Write-Host "[$(Get-Timestamp)] [DEBUG] $Message"
    }
}

if ($Help) {
    Write-Host "Uninstalls stasis-client, stasis-client-cli and stasis-client-ui for the current user"
    Write-Host "Usage: .\client_uninstall.ps1 [-Verbose] [-Help]"
    exit 0
}

$CLIENT_USER_HOME = $env:USERPROFILE

Log-Debug "User detected with home directory at [$CLIENT_USER_HOME]"

$CLIENT_PATH = "$CLIENT_USER_HOME\stasis-client"
$CLIENT_VENV_PATH = "$CLIENT_PATH\.venv"
$TARGET_BIN_PATH = "$CLIENT_PATH\bin"

Log-Debug "Uninstallation proceeding with:"
Log-Debug "  Environment:"
Log-Debug "    CLIENT_PATH = $CLIENT_PATH"
Log-Debug "    CLIENT_VENV_PATH = $CLIENT_VENV_PATH"
Log-Debug "    TARGET_BIN_PATH = $TARGET_BIN_PATH"

Log-Info "Uninstalling [stasis-client]..."
Remove-Item -Path "$CLIENT_PATH\bin" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$CLIENT_PATH\lib" -Recurse -Force -ErrorAction SilentlyContinue

if (Test-Path "$CLIENT_VENV_PATH\Scripts\Activate.ps1") {
    & "$CLIENT_VENV_PATH\Scripts\Activate.ps1"

    Log-Info "Uninstalling [stasis-client-cli]..."
    & pip uninstall -y stasis-client-cli 2>$null

    & deactivate
}

Log-Debug "Removing python venv from [$CLIENT_VENV_PATH]..."
Remove-Item -Path $CLIENT_VENV_PATH -Recurse -Force -ErrorAction SilentlyContinue

Log-Info "Uninstalling [stasis-client-ui]..."
$CLIENT_UI_PATH = "$CLIENT_PATH\ui"
Remove-Item -Path $CLIENT_UI_PATH -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\stasis.lnk" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$CLIENT_USER_HOME\Desktop\stasis.lnk" -Force -ErrorAction SilentlyContinue

Log-Info "Removing PATH entries..."
$USER_PATH = [Environment]::GetEnvironmentVariable('Path', 'User')
if (-not $USER_PATH) { $USER_PATH = '' }
$PATHS_TO_REMOVE = @($TARGET_BIN_PATH)
$UPDATED = $false

foreach ($P in $PATHS_TO_REMOVE) {
    if ($USER_PATH -like "*$P*") {
        $USER_PATH = ($USER_PATH.Split(';') | Where-Object { $_ -ne $P }) -join ';'
        $UPDATED = $true
    }
}

if ($UPDATED) {
    [Environment]::SetEnvironmentVariable('Path', $USER_PATH, 'User')
    Log-Debug "Removed stasis entries from user PATH"
    Log-Warn "You might have to restart your terminal session for PATH changes to take effect"
}

Log-Info "... done."
