#!/usr/bin/env bash

HELP="Uninstalls stasis-client, stasis-client-cli and stasis-client-ui for the current user"
USAGE="Usage: $0 [-v|--verbose] [-h|--help]"

function now() {
  timestamp="$(date +"%Y-%m-%dT%H:%M:%SZ")"
  echo "${timestamp}"
}

function log_info() {
    echo "[$(now)] [ INFO] $1"
}

function log_warn() {
    echo "[$(now)] [ WARN] $1"
}

function log_error() {
    echo "[$(now)] [ERROR] $1"
}

function log_debug() {
    if [[ "${VERBOSE_FLAG}" == "YES" ]]; then
      echo "[$(now)] [DEBUG] $1"
    fi
}

function log_requires_sudo() {
    log_warn "This action requires elevated privileges; you may be prompted for your credentials..."
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

CLIENT_USER=${USER}
CLIENT_USER_HOME=${HOME}

log_debug "User detected as [${CLIENT_USER}] with home directory at [${CLIENT_USER_HOME}]"

CLIENT_PATH="${CLIENT_USER_HOME}/stasis-client"
CLIENT_VENV_PATH="${CLIENT_PATH}/.venv"

if [[ "${OSTYPE}" == "linux"* ]]; then
  CLIENT_CONFIG_PATH="${CLIENT_USER_HOME}/.config/stasis-client"
  TARGET_BIN_PATH="${CLIENT_USER_HOME}/.local/bin"
  CLIENT_UI_PATH="${CLIENT_USER_HOME}/stasis-client-ui"
elif [[ "${OSTYPE}" == "darwin"* ]]; then
  CLIENT_CONFIG_PATH="${CLIENT_USER_HOME}/Library/Preferences/stasis-client"
  TARGET_BIN_PATH="/usr/local/bin"
  CLIENT_UI_PATH="/Applications/stasis.app"
else
  log_error "Operating system [${OSTYPE}] is not supported."
  exit 1
fi

log_debug "Uninstallation proceeding with:"

log_debug "  Environment:"
log_debug "    CLIENT_PATH = ${CLIENT_PATH}"
log_debug "    CLIENT_VENV_PATH = ${CLIENT_VENV_PATH}"
log_debug "    CLIENT_CONFIG_PATH = ${CLIENT_CONFIG_PATH}"
log_debug "    TARGET_BIN_PATH = ${TARGET_BIN_PATH}"
log_debug "    CLIENT_UI_PATH = ${CLIENT_UI_PATH}"

log_info "Uninstalling [stasis-client]..."
rm -r "${CLIENT_PATH}/bin" 2> /dev/null
rm -r "${CLIENT_PATH}/lib" 2> /dev/null

source "${CLIENT_VENV_PATH}/bin/activate" 2> /dev/null

log_info "Uninstalling [stasis-client-cli]..."
pip3 uninstall -y stasis-client-cli 2> /dev/null

deactivate 2> /dev/null

log_debug "Removing python venv from [${CLIENT_VENV_PATH}]..."
rm -r "${CLIENT_VENV_PATH}" 2> /dev/null

log_info "Uninstalling [stasis-client-ui]..."
rm -r "${CLIENT_UI_PATH}" 2> /dev/null

if [[ "${OSTYPE}" == "darwin"* ]]; then
  rm -r "${CLIENT_USER_HOME}/Applications/stasis.app" 2> /dev/null
fi

log_info "Unlinking executables..."
if [[ "${OSTYPE}" == "linux"* ]]; then
  unlink "${TARGET_BIN_PATH}/stasis-client" 2> /dev/null
  unlink "${TARGET_BIN_PATH}/stasis" 2> /dev/null
  unlink "${TARGET_BIN_PATH}/stasis-ui" 2> /dev/null
elif [[ "${OSTYPE}" == "darwin"* ]]; then
  log_requires_sudo
  sudo bash -c "unlink \"${TARGET_BIN_PATH}/stasis-client\" 2> /dev/null && unlink \"${TARGET_BIN_PATH}/stasis\" 2> /dev/null"
else
  log_error "Operating system [${OSTYPE}] is not supported."
  exit 1
fi

log_info "... done."
