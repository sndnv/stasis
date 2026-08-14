param(
    [switch]$Verbose,
    [switch]$Help,
    [switch]$SkipDownload,
    [string]$Version
)

$ErrorActionPreference = 'Stop'

$DOWNLOAD_DIR_BASE = "$env:TEMP\stasis-download-"
$REPO = 'sndnv/stasis'

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
    Write-Host "Downloads and installs stasis-client, stasis-client-cli and stasis-client-ui for the current user"
    Write-Host "`tDownloads are stored under [$DOWNLOAD_DIR_BASE<version>\]"
    Write-Host "`tSource Github repo is [$REPO]"
    Write-Host "Usage: .\client_install.ps1 [-Verbose] [-Help] [-SkipDownload] [-Version <version>]"
    exit 0
}

$PYTHON3_VERSION_MIN = 13
try { $PYTHON3_VERSION_ACTUAL = & python3 -c 'import sys; print(sys.version_info[1:2][0])' 2>$null } catch {}

if (-not $PYTHON3_VERSION_ACTUAL) {
    try { $PYTHON3_VERSION_ACTUAL = & python -c 'import sys; print(sys.version_info[1:2][0])' 2>$null } catch {}
}

if (-not $PYTHON3_VERSION_ACTUAL -or [int]$PYTHON3_VERSION_ACTUAL -lt $PYTHON3_VERSION_MIN) {
    Log-Error "The minimum required Python version is [3.$PYTHON3_VERSION_MIN] but [3.$PYTHON3_VERSION_ACTUAL] was found"
    exit 1
}

$CLIENT_UI_TARGET = 'windows'
$CLIENT_UI_EXT = 'zip'

Log-Debug "Target system detected as [$CLIENT_UI_TARGET]"

if ($SkipDownload) {
    Log-Info "Skipping assets download"

    if ($Version) {
        $DOWNLOAD_DIRS = @(Get-Item "$DOWNLOAD_DIR_BASE$Version" -ErrorAction SilentlyContinue)
    } else {
        $DOWNLOAD_DIRS = @(Get-Item "$DOWNLOAD_DIR_BASE*" -ErrorAction SilentlyContinue)
    }

    if ($DOWNLOAD_DIRS.Count -eq 0) {
        Log-Error "No matching local assets found"
        exit 1
    } elseif ($DOWNLOAD_DIRS.Count -eq 1) {
        $ACTUAL_VERSION = $DOWNLOAD_DIRS[0].Name -replace '^stasis-download-', ''
        $ACTUAL_PYTHON_VERSION = ($ACTUAL_VERSION -replace '-', '+').ToLower()
        $DOWNLOAD_DIR = $DOWNLOAD_DIRS[0].FullName

        $STASIS_CLIENT_FILE = "$DOWNLOAD_DIR\stasis-client-$ACTUAL_VERSION.zip"
        $STASIS_CLIENT_CLI_FILE = "$DOWNLOAD_DIR\stasis_client_cli-$ACTUAL_PYTHON_VERSION-py3-none-any.whl"
        $STASIS_CLIENT_UI_FILE = "$DOWNLOAD_DIR\stasis-client-ui-$CLIENT_UI_TARGET-$ACTUAL_VERSION.$CLIENT_UI_EXT"

        $FILE_MISSING = $false

        if (-not (Test-Path $STASIS_CLIENT_FILE)) {
            Log-Error "Client file missing - [$STASIS_CLIENT_FILE]"
            $FILE_MISSING = $true
        } else {
            Log-Debug "Using [$STASIS_CLIENT_FILE] to install client"
        }

        if (-not (Test-Path $STASIS_CLIENT_CLI_FILE)) {
            Log-Error "Client CLI file missing - [$STASIS_CLIENT_CLI_FILE]"
            $FILE_MISSING = $true
        } else {
            Log-Debug "Using [$STASIS_CLIENT_CLI_FILE] to install client CLI"
        }

        if (-not (Test-Path $STASIS_CLIENT_UI_FILE)) {
            Log-Error "Client UI file missing - [$STASIS_CLIENT_UI_FILE]"
            $FILE_MISSING = $true
        } else {
            Log-Debug "Using [$STASIS_CLIENT_UI_FILE] to install client UI"
        }

        if ($FILE_MISSING) {
            exit 1
        }
    } else {
        Log-Error "Found too many matching local assets ($($DOWNLOAD_DIRS.Count)): [$($DOWNLOAD_DIRS.FullName -join ', ')]"
        exit 1
    }
} else {
    $RELEASES_API = "https://api.github.com/repos/$REPO/releases"

    Log-Debug "Loading version information from [$RELEASES_API]"

    $RELEASES = Invoke-RestMethod -Uri $RELEASES_API

    if ($Version) {
        $RELEASE = $RELEASES | Where-Object { $_.tag_name -eq $Version } | Select-Object -First 1
        if (-not $RELEASE) {
            Log-Error "Version [$Version] was not found"
            exit 1
        }
        $ACTUAL_VERSION = $RELEASE.tag_name
        Log-Debug "Found release with ID [$($RELEASE.id)] for version [$ACTUAL_VERSION]"
        Log-Info "Starting installation for version: [$ACTUAL_VERSION]"
    } else {
        $RELEASE = $RELEASES | Select-Object -First 1
        $ACTUAL_VERSION = $RELEASE.tag_name
        Log-Debug "Found release with ID [$($RELEASE.id)] for version [$ACTUAL_VERSION]"
        Log-Info "Starting installation for version: [$ACTUAL_VERSION (latest)]"
    }

    $DOWNLOAD_DIR = "$DOWNLOAD_DIR_BASE$ACTUAL_VERSION"
    New-Item -ItemType Directory -Force -Path $DOWNLOAD_DIR | Out-Null

    $ACTUAL_PYTHON_VERSION = ($ACTUAL_VERSION -replace '-', '+').ToLower()

    $ASSETS = @(
        "stasis-client-$ACTUAL_VERSION.zip",
        "stasis_client_cli-$ACTUAL_PYTHON_VERSION-py3-none-any.whl",
        "stasis-client-ui-$CLIENT_UI_TARGET-$ACTUAL_VERSION.$CLIENT_UI_EXT"
    )

    foreach ($ASSET in $ASSETS) {
        $URL = "https://github.com/$REPO/releases/download/$ACTUAL_VERSION/$ASSET"
        $OUTPUT = "$DOWNLOAD_DIR\$ASSET"
        Log-Debug "Downloading [$URL] to [$DOWNLOAD_DIR]"
        Invoke-WebRequest -Uri $URL -OutFile $OUTPUT
    }
}

$CLIENT_USER_HOME = $env:USERPROFILE

Log-Debug "User detected with home directory at [$CLIENT_USER_HOME]"

$CLIENT_ARCHIVE = "$DOWNLOAD_DIR\stasis-client-$ACTUAL_VERSION.zip"
$CLIENT_ARCHIVE_NAME = [System.IO.Path]::GetFileNameWithoutExtension($CLIENT_ARCHIVE) -replace '^stasis-client-v', 'stasis-client-'
$CLIENT_CLI_ARCHIVE = "$DOWNLOAD_DIR\stasis_client_cli-$ACTUAL_PYTHON_VERSION-py3-none-any.whl"
$CLIENT_UI_ARCHIVE = "$DOWNLOAD_DIR\stasis-client-ui-$CLIENT_UI_TARGET-$ACTUAL_VERSION.$CLIENT_UI_EXT"

$CLIENT_PATH = "$CLIENT_USER_HOME\stasis-client"
$CLIENT_VENV_PATH = "$CLIENT_PATH\.venv"
$CLIENT_CONFIG_PATH = "$env:LOCALAPPDATA\stasis-client"
$CLIENT_CERTS_PATH = "$CLIENT_CONFIG_PATH\certs"
$CLIENT_LOGS_PATH = "$CLIENT_PATH\logs"
$CLIENT_STATE_PATH = "$CLIENT_PATH\state"

Log-Debug "Installation proceeding with:"
Log-Debug "  Environment:"
Log-Debug "    CLIENT_ARCHIVE = $CLIENT_ARCHIVE"
Log-Debug "    CLIENT_ARCHIVE_NAME = $CLIENT_ARCHIVE_NAME"
Log-Debug "    CLIENT_CLI_ARCHIVE = $CLIENT_CLI_ARCHIVE"
Log-Debug "    CLIENT_PATH = $CLIENT_PATH"
Log-Debug "    CLIENT_VENV_PATH = $CLIENT_VENV_PATH"
Log-Debug "    CLIENT_CONFIG_PATH = $CLIENT_CONFIG_PATH"
Log-Debug "    CLIENT_CERTS_PATH = $CLIENT_CERTS_PATH"
Log-Debug "    CLIENT_LOGS_PATH = $CLIENT_LOGS_PATH"
Log-Debug "    CLIENT_STATE_PATH = $CLIENT_STATE_PATH"
Log-Debug "    CLIENT_UI_ARCHIVE = $CLIENT_UI_ARCHIVE"

$CLIENT_ARCHIVE_CHECKSUM = (Get-FileHash -Algorithm SHA256 $CLIENT_ARCHIVE).Hash
$CLIENT_CLI_ARCHIVE_CHECKSUM = (Get-FileHash -Algorithm SHA256 $CLIENT_CLI_ARCHIVE).Hash
$CLIENT_UI_ARCHIVE_CHECKSUM = (Get-FileHash -Algorithm SHA256 $CLIENT_UI_ARCHIVE).Hash

Log-Debug "  Files:"
Log-Debug "    $CLIENT_ARCHIVE_CHECKSUM  $CLIENT_ARCHIVE"
Log-Debug "    $CLIENT_CLI_ARCHIVE_CHECKSUM  $CLIENT_CLI_ARCHIVE"
Log-Debug "    $CLIENT_UI_ARCHIVE_CHECKSUM  $CLIENT_UI_ARCHIVE"

Log-Info "Installing [stasis-client]..."

Log-Debug "Setting up client directory [$CLIENT_PATH]..."
New-Item -ItemType Directory -Force -Path $CLIENT_PATH | Out-Null

Log-Debug "Setting up client config directory [$CLIENT_CONFIG_PATH]..."
New-Item -ItemType Directory -Force -Path $CLIENT_CONFIG_PATH | Out-Null

Log-Debug "Setting up client certificates directory [$CLIENT_CERTS_PATH]..."
New-Item -ItemType Directory -Force -Path $CLIENT_CERTS_PATH | Out-Null

Log-Debug "Setting up client logs directory [$CLIENT_LOGS_PATH]..."
New-Item -ItemType Directory -Force -Path $CLIENT_LOGS_PATH | Out-Null
if (-not (Test-Path "$CLIENT_LOGS_PATH\stasis-client.log")) {
    New-Item -ItemType File -Path "$CLIENT_LOGS_PATH\stasis-client.log" | Out-Null
}

Log-Debug "Setting up client state directory [$CLIENT_STATE_PATH]..."
New-Item -ItemType Directory -Force -Path "$CLIENT_STATE_PATH\backups" | Out-Null
New-Item -ItemType Directory -Force -Path "$CLIENT_STATE_PATH\recoveries" | Out-Null

Log-Debug "Extracting client from [$CLIENT_ARCHIVE] to [$CLIENT_PATH]..."
Expand-Archive -Path $CLIENT_ARCHIVE -DestinationPath $CLIENT_PATH -Force
Copy-Item -Path "$CLIENT_PATH\$CLIENT_ARCHIVE_NAME\*" -Destination $CLIENT_PATH -Recurse -Force
Remove-Item -Path "$CLIENT_PATH\$CLIENT_ARCHIVE_NAME" -Recurse -Force

Log-Debug "Setting up new python venv in [$CLIENT_VENV_PATH]..."
if (Get-Command python3 -ErrorAction SilentlyContinue) {
    & python3 -m venv $CLIENT_VENV_PATH
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    & python -m venv $CLIENT_VENV_PATH
} else {
    Log-Error "Python 3 is required to install the client CLI but was not found"
    exit 1
}
if ($LASTEXITCODE -ne 0) {
    Log-Error "Failed to create the python venv in [$CLIENT_VENV_PATH]"
    exit 1
}

& "$CLIENT_VENV_PATH\Scripts\Activate.ps1"

Log-Info "Installing [stasis-client-cli]..."
& pip install $CLIENT_CLI_ARCHIVE
if ($LASTEXITCODE -ne 0) {
    Log-Error "Failed to install [stasis-client-cli]"
    exit 1
}

& deactivate

Log-Info "Installing [stasis-client-ui]..."

$CLIENT_UI_PATH = "$CLIENT_PATH\ui"
$CLIENT_UI_EXE = "$CLIENT_UI_PATH\stasis_client_ui.exe"

Log-Debug "Setting up UI directory [$CLIENT_UI_PATH]..."
if (Test-Path $CLIENT_UI_PATH) {
    Remove-Item -Path $CLIENT_UI_PATH -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $CLIENT_UI_PATH | Out-Null

Log-Debug "Extracting UI from [$CLIENT_UI_ARCHIVE] to [$CLIENT_UI_PATH]..."
Expand-Archive -Path $CLIENT_UI_ARCHIVE -DestinationPath $CLIENT_UI_PATH -Force

if (-not (Test-Path $CLIENT_UI_EXE)) {
    Log-Error "UI executable not found after extraction: [$CLIENT_UI_EXE]"
    exit 1
}

function New-UiShortcut {
    param(
        [Parameter(Mandatory = $true)][string]$ShortcutPath
    )

    $Shell = New-Object -ComObject WScript.Shell
    $Shortcut = $Shell.CreateShortcut($ShortcutPath)
    $Shortcut.TargetPath = $CLIENT_UI_EXE
    $Shortcut.WorkingDirectory = $CLIENT_UI_PATH
    $Shortcut.Description = 'stasis - client user interface'
    $Shortcut.Save()
}

$START_MENU_UI_SHORTCUT = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\stasis.lnk"
$DESKTOP_UI_SHORTCUT = "$CLIENT_USER_HOME\Desktop\stasis.lnk"

Log-Debug "Creating Start Menu shortcut [$START_MENU_UI_SHORTCUT]..."
New-UiShortcut -ShortcutPath $START_MENU_UI_SHORTCUT

Log-Debug "Creating Desktop shortcut [$DESKTOP_UI_SHORTCUT]..."
New-UiShortcut -ShortcutPath $DESKTOP_UI_SHORTCUT

Log-Info "Linking executables..."

$TARGET_BIN_PATH = "$CLIENT_PATH\bin"

Log-Debug "Creating stasis CLI shim in [$TARGET_BIN_PATH]..."
Set-Content -Path "$TARGET_BIN_PATH\stasis.cmd" -Value "@`"$CLIENT_VENV_PATH\Scripts\stasis-client-cli.exe`" %*"

$USER_PATH = [Environment]::GetEnvironmentVariable('Path', 'User')
if (-not $USER_PATH) { $USER_PATH = '' }
$PATHS_TO_ADD = @($TARGET_BIN_PATH)
$PATHS_ADDED = @()

foreach ($P in $PATHS_TO_ADD) {
    if ($USER_PATH -notlike "*$P*") {
        $PATHS_ADDED += $P
    }
}

if ($PATHS_ADDED.Count -gt 0) {
    $NEW_PATH = ($USER_PATH.TrimEnd(';') + ';' + ($PATHS_ADDED -join ';')).TrimStart(';')
    [Environment]::SetEnvironmentVariable('Path', $NEW_PATH, 'User')
    $env:Path = $NEW_PATH + ';' + [Environment]::GetEnvironmentVariable('Path', 'Machine')
    Log-Debug "Added to user PATH: [$($PATHS_ADDED -join ', ')]"
    Log-Warn "You might have to restart your terminal session for PATH changes to take effect"
}

Log-Info "... done."
