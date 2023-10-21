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

function log_error() {
    echo "[$(now)] [ERROR] $1"
}

function log_debug() {
    if [[ "${VERBOSE_FLAG}" == "YES" ]]; then
      echo "[$(now)] [DEBUG] $1"
    fi
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

log_debug "Uninstallation proceeding with:"

log_debug "  Environment:"
log_debug "    CLIENT_PATH = ${CLIENT_PATH}"
log_debug "    CLIENT_CONFIG_PATH = ${CLIENT_CONFIG_PATH}"
log_debug "    TARGET_BIN_PATH = ${TARGET_BIN_PATH}"
log_debug "    CLIENT_UI_PATH = ${CLIENT_UI_PATH}"

log_info "Uninstalling [stasis-client]..."
unlink "${TARGET_BIN_PATH}/stasis-client"
rm "${CLIENT_PATH}/bin/stasis-client"
rm "${CLIENT_PATH}/bin/stasis-client.bat"
rm -d "${CLIENT_PATH}/bin"
rm ${CLIENT_PATH}/lib/*.jar
rm -d "${CLIENT_PATH}/lib"
rm ${CLIENT_PATH}/conf/*.ini
rm -d "${CLIENT_PATH}/conf"

log_info "Uninstalling [stasis-client-cli]..."
pip3 uninstall -y stasis-client-cli
unlink "${TARGET_BIN_PATH}/stasis"

log_info "Uninstalling [stasis-client-ui]..."
if [[ "${OSTYPE}" == "linux"* ]]; then
  unlink "${TARGET_BIN_PATH}/stasis-ui"
  rm ${CLIENT_UI_PATH}/*.AppImage
  rm -d ${CLIENT_UI_PATH}
elif [[ "${OSTYPE}" == "darwin"* ]]; then
  unlink "${TARGET_BIN_PATH}/stasis-ui"
  rm -r ${CLIENT_UI_PATH}
else
  log_error "Operating system [${OSTYPE}] is not supported."
  exit 1
fi

log_info "... done."
