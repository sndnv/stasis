#!/usr/bin/env bash

DOWNLOAD_DIR_BASE="/tmp/stasis-download-"
REPO="sndnv/stasis"

HELP="Downloads and installs stasis-client, stasis-client-cli and stasis-client-ui for the current user\n\tDownloads are stored under [${DOWNLOAD_DIR_BASE}<version>/]\n\tSource Github repo is [${REPO}]"
USAGE="Usage: $0 [-v|--verbose] [-h|--help] [-s|--skip-download] [--version=<version>]"

function now() {
  timestamp="$(date +"%Y-%m-%dT%H:%M:%SZ")"
  echo "${timestamp}"
}

function log_info() {
    echo "[$(now)] [ INFO] $1"
}

function log_error() {
    echo "[$(now)] [ERROR] $1"
}

function log_debug() {
    if [[ "${VERBOSE_FLAG}" == "YES" ]]; then
      echo "[$(now)] [DEBUG] $1"
    fi
}

function failed() {
  echo "[$(now)] ... failed."
  exit 1
}

function file_name_from_path() {
  local FILE="${1##*/}"
  echo "${FILE%.*}"
}

for i in "$@"; do
  case $i in
    -h|--help)
      HELP_FLAG=YES
      shift
      ;;
    -v|--verbose)
      VERBOSE_FLAG=YES
      shift
      ;;
    -s|--skip-download)
      SKIP_DOWNLOAD_FLAG=YES
      shift
      ;;
    --version=*)
      VERSION="${i#*=}"
      shift
      ;;
    *)
      log_error "Unknown argument $i"
      exit 1
      ;;
  esac
done

if [ "$HELP_FLAG" == "YES" ]; then
  echo -e "${HELP}"
  echo -e "${USAGE}"
  exit 0
fi

if ! command -v jq &> /dev/null; then
  log_error "[jq] is required for the installation process; see [https://jqlang.github.io/jq/download/] for installation instructions"
  exit 1
fi

if [[ "${OSTYPE}" == "linux"* ]]; then
  CLIENT_UI_TARGET="linux"
  CLIENT_UI_EXT="AppImage"
elif [[ "${OSTYPE}" == "darwin"* ]]; then
  CLIENT_UI_TARGET="macos"
  CLIENT_UI_EXT="dmg"
else
  log_error "Operating system [${OSTYPE}] is not supported."
  exit 1
fi

log_debug "Target system detected as [${CLIENT_UI_TARGET}]"

if [ "$SKIP_DOWNLOAD_FLAG" == "YES" ]; then
  log_info "Skipping assets download"

  if [[ -z "${VERSION}" ]]; then
    DOWNLOAD_DIRS=$(ls -d1 ${DOWNLOAD_DIR_BASE}*)
  else
    DOWNLOAD_DIRS=$(ls -d1 ${DOWNLOAD_DIR_BASE}${VERSION})
  fi

  IFS=$'\n' read -rd '' -a DOWNLOADED_VERSIONS <<< "$DOWNLOAD_DIRS"

  if [ ${#DOWNLOADED_VERSIONS[@]} -eq 0 ]; then
    log_error "No matching local assets found"
    exit 1
  elif [ ${#DOWNLOADED_VERSIONS[@]} -eq 1 ]; then
    ACTUAL_VERSION=${DOWNLOADED_VERSIONS[0]#*${DOWNLOAD_DIR_BASE}}

    STASIS_CLIENT_FILE="${DOWNLOADED_VERSIONS[0]}/stasis-client-${ACTUAL_VERSION}.zip"
    STASIS_CLIENT_CLI_FILE="${DOWNLOADED_VERSIONS[0]}/stasis_client_cli-${ACTUAL_VERSION}-py3-none-any.whl"
    STASIS_CLIENT_UI_FILE="${DOWNLOADED_VERSIONS[0]}/stasis-client-ui-${CLIENT_UI_TARGET}-${ACTUAL_VERSION}.${CLIENT_UI_EXT}"

    if [ ! -f ${STASIS_CLIENT_FILE} ]; then
        log_error "Client file missing - [${STASIS_CLIENT_FILE}]"
        FILE_MISSING=YES
    else
      log_debug "Using [${STASIS_CLIENT_FILE}] to install client"
    fi

    if [ ! -f ${STASIS_CLIENT_CLI_FILE} ]; then
        log_error "Client CLI file missing - [${STASIS_CLIENT_CLI_FILE}]"
        FILE_MISSING=YES
    else
      log_debug "Using [${STASIS_CLIENT_CLI_FILE}] to install client CLI"
    fi

    if [ ! -f ${STASIS_CLIENT_UI_FILE} ]; then
        log_error "Client UI file missing - [${STASIS_CLIENT_UI_FILE}]"
        FILE_MISSING=YES
    else
      log_debug "Using [${STASIS_CLIENT_UI_FILE}] to install client UI"
    fi

    if [ "$FILE_MISSING" == "YES" ]; then
      exit 1
    else
      DOWNLOAD_DIR=${DOWNLOADED_VERSIONS[0]}
    fi
  else
    log_error "Found too many matching local assets (${#DOWNLOADED_VERSIONS[@]}): [${DOWNLOAD_DIRS//$'\n'/, }]"
    exit 1
  fi
else
  RELEASES_API="https://api.github.com/repos/${REPO}/releases"

  function get_latest_version() {
      echo $(curl -s "${RELEASES_API}" | jq -r ".[0] | .tag_name, .id")
  }

  function get_requested_version() {
      echo $(curl -s "${RELEASES_API}" | jq -r ".[] | select(.tag_name == \"${VERSION}\") | .tag_name, .id")
  }

  function get_asset() {
    ASSET=$1
    RELEASE_VERSION=$2
    OUTPUT_DIR=$3

    URL="https://github.com/${REPO}/releases/download/${RELEASE_VERSION}/${ASSET}"

    log_debug "Downloading [${URL}] to [${OUTPUT_DIR}]"
    $(curl -sOL --output-dir ${OUTPUT_DIR} ${URL})
  }

  log_debug "Loading version information from [${RELEASES_API}]"

  if [[ -z "${VERSION}" ]]; then
    REPORTED_VERSION=$(get_latest_version)
    ACTUAL_VERSION=${REPORTED_VERSION% *}
    ACTUAL_RELEASE_ID=${REPORTED_VERSION#* }
    log_debug "Found release with ID [${ACTUAL_RELEASE_ID}] for version [${ACTUAL_VERSION}]"
    log_info "Starting installation for version: [${ACTUAL_VERSION} (latest)]"
  else
    REQUESTED_VERSION=$(get_requested_version)
    if [[ -z "${REQUESTED_VERSION}" ]]; then
      log_error "Version [${VERSION}] was not found"
      exit 1
    else
      ACTUAL_VERSION=${REQUESTED_VERSION% *}
      ACTUAL_RELEASE_ID=${REQUESTED_VERSION#* }
      log_debug "Found release with ID [${ACTUAL_RELEASE_ID}] for version [${ACTUAL_VERSION}]"
      log_info "Starting installation for version: [${ACTUAL_VERSION}]"
    fi
  fi

  DOWNLOAD_DIR="${DOWNLOAD_DIR_BASE}${ACTUAL_VERSION}"
  mkdir -p ${DOWNLOAD_DIR} || failed

  get_asset "stasis-client-${ACTUAL_VERSION}.zip" ${ACTUAL_VERSION} ${DOWNLOAD_DIR}
  get_asset "stasis_client_cli-${ACTUAL_VERSION}-py3-none-any.whl" ${ACTUAL_VERSION} ${DOWNLOAD_DIR}
  get_asset "stasis-client-ui-${CLIENT_UI_TARGET}-${ACTUAL_VERSION}.${CLIENT_UI_EXT}" ${ACTUAL_VERSION} ${DOWNLOAD_DIR}
fi

CLIENT_USER=${USER}
CLIENT_USER_HOME=${HOME}

log_debug "User detected as [${CLIENT_USER}] with home directory at [${CLIENT_USER_HOME}]"

CLIENT_ARCHIVE="${DOWNLOAD_DIR}/stasis-client-${ACTUAL_VERSION}.zip"
CLIENT_ARCHIVE_FULL_NAME=$(file_name_from_path ${CLIENT_ARCHIVE})
CLIENT_ARCHIVE_NAME=${CLIENT_ARCHIVE_FULL_NAME/"stasis-client-v"/"stasis-client-"}
CLIENT_CLI_ARCHIVE="${DOWNLOAD_DIR}/stasis_client_cli-${ACTUAL_VERSION}-py3-none-any.whl"
CLIENT_UI_BINARY="${DOWNLOAD_DIR}/stasis-client-ui-${CLIENT_UI_TARGET}-${ACTUAL_VERSION}.${CLIENT_UI_EXT}"

CLIENT_PATH="${CLIENT_USER_HOME}/stasis-client"

if [[ "${OSTYPE}" == "linux"* ]]; then
  CLIENT_CONFIG_PATH="${CLIENT_USER_HOME}/.config/stasis-client"
  TARGET_BIN_PATH="${CLIENT_USER_HOME}/.local/bin"
  CLIENT_UI_PATH="${CLIENT_USER_HOME}/stasis-client-ui"
elif [[ "${OSTYPE}" == "darwin"* ]]; then
  CLIENT_CONFIG_PATH="${CLIENT_USER_HOME}/Library/Preferences/stasis-client"
  TARGET_BIN_PATH="/usr/local/bin"
  CLIENT_UI_PATH="${CLIENT_USER_HOME}/Applications/stasis.app"
else
  log_error "Operating system [${OSTYPE}] is not supported."
  exit 1
fi

CLIENT_CERTS_PATH="${CLIENT_CONFIG_PATH}/certs"
CLIENT_LOGS_PATH="${CLIENT_PATH}/logs"
CLIENT_STATE_PATH="${CLIENT_PATH}/state"

log_debug "Installation proceeding with:"

log_debug "  Environment:"
log_debug "    CLIENT_ARCHIVE = ${CLIENT_ARCHIVE}"
log_debug "    CLIENT_ARCHIVE_NAME = ${CLIENT_ARCHIVE_NAME}"
log_debug "    CLIENT_CLI_ARCHIVE = ${CLIENT_CLI_ARCHIVE}"
log_debug "    CLIENT_PATH = ${CLIENT_PATH}"
log_debug "    CLIENT_CONFIG_PATH = ${CLIENT_CONFIG_PATH}"
log_debug "    TARGET_BIN_PATH = ${TARGET_BIN_PATH}"
log_debug "    CLIENT_UI_PATH = ${CLIENT_UI_PATH}"
log_debug "    CLIENT_CERTS_PATH = ${CLIENT_CERTS_PATH}"
log_debug "    CLIENT_LOGS_PATH = ${CLIENT_LOGS_PATH}"
log_debug "    CLIENT_STATE_PATH = ${CLIENT_STATE_PATH}"
log_debug "    CLIENT_UI_BINARY = ${CLIENT_UI_BINARY}"

CLIENT_ARCHIVE_CHECKSUM=$(shasum -a 256 ${CLIENT_ARCHIVE}) || failed
CLIENT_CLI_ARCHIVE_CHECKSUM=$(shasum -a 256 ${CLIENT_CLI_ARCHIVE}) || failed
CLIENT_UI_BINARY_CHECKSUM=$(shasum -a 256 ${CLIENT_UI_BINARY}) || failed

log_debug "  Files:"
log_debug "    ${CLIENT_ARCHIVE_CHECKSUM}"
log_debug "    ${CLIENT_CLI_ARCHIVE_CHECKSUM}"
log_debug "    ${CLIENT_UI_BINARY_CHECKSUM}"

log_info "Installing [stasis-client]..."

log_debug "Setting up client directory [${CLIENT_PATH}]..."
mkdir -p ${CLIENT_PATH} || failed

if [[ "${OSTYPE}" == "linux"* ]]; then
  mkdir -p ${CLIENT_UI_PATH} || failed
  chown -R ${CLIENT_USER} ${CLIENT_UI_PATH} || failed
  chmod +w ${CLIENT_UI_PATH} || failed
fi

log_debug "Setting up client config directory [${CLIENT_CONFIG_PATH}]..."
mkdir -p ${CLIENT_CONFIG_PATH} || failed
chown -R ${CLIENT_USER} ${CLIENT_PATH} || failed
chmod +w ${CLIENT_PATH} || failed

log_debug "Setting up client certificates directory [${CLIENT_CERTS_PATH}]..."
mkdir -p ${CLIENT_CERTS_PATH} || failed
chown -R ${CLIENT_USER} ${CLIENT_CERTS_PATH} || failed
chmod +w ${CLIENT_CERTS_PATH} || failed

log_debug "Setting up client logs directory [${CLIENT_LOGS_PATH}]..."
mkdir -p ${CLIENT_LOGS_PATH} || failed
chown -R ${CLIENT_USER} ${CLIENT_LOGS_PATH} || failed
chmod +w ${CLIENT_LOGS_PATH} || failed
touch "${CLIENT_LOGS_PATH}/stasis-client.log" || failed

log_debug "Setting up client state directory [${CLIENT_STATE_PATH}]..."
mkdir -p "${CLIENT_STATE_PATH}/backups" || failed
mkdir -p "${CLIENT_STATE_PATH}/recoveries" || failed
chown -R ${CLIENT_USER} ${CLIENT_STATE_PATH} || failed
chmod +w ${CLIENT_STATE_PATH} || failed

log_debug "Extracting client from [${CLIENT_ARCHIVE}] to [${CLIENT_PATH}]..."
unzip -o -q -d "${CLIENT_PATH}/" ${CLIENT_ARCHIVE} || failed
mv -f ${CLIENT_PATH}/${CLIENT_ARCHIVE_NAME}/* ${CLIENT_PATH} || failed
rm -r "${CLIENT_PATH}/${CLIENT_ARCHIVE_NAME}" || failed
ln -s "${CLIENT_PATH}/bin/stasis-client" "${TARGET_BIN_PATH}/stasis-client" || failed

log_info "Installing [stasis-client-cli]..."
pip3 install ${CLIENT_CLI_ARCHIVE} || failed
ln -s "$(which stasis-client-cli)" "${TARGET_BIN_PATH}/stasis"

log_debug "Installing [stasis-client-ui]..."

if [[ "${OSTYPE}" == "linux"* ]]; then
  cp ${CLIENT_UI_BINARY} ${CLIENT_UI_PATH}/ || failed
  chmod u+x ${CLIENT_UI_BINARY}
  ln -s ${CLIENT_UI_BINARY} "${TARGET_BIN_PATH}/stasis-ui"
elif [[ "${OSTYPE}" == "darwin"* ]]; then
  open ${CLIENT_UI_BINARY}
  log_info "You can now install the stasis UI by opening Finder and dragging the app to your Applications folder"
else
  log_error "Operating system [${OSTYPE}] is not supported."
  exit 1
fi

log_info "... done."
