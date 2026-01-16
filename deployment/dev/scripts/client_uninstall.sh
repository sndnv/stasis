#!/usr/bin/env bash

HELP="Uninstalls stasis-client, stasis-client-cli and stasis-client-ui for the current user"
USAGE="Usage: $0"

if [ "$1" = "-h" ] || [ "$1" = "--help" ]
then
  echo "${HELP}"
  echo "${USAGE}"
  exit
fi

function now() {
  timestamp="$(date +"%Y-%m-%dT%H:%M:%SZ")"
  echo "${timestamp}"
}

CLIENT_USER=${USER}
CLIENT_USER_HOME=${HOME}

CLIENT_PATH="${CLIENT_USER_HOME}/stasis-client"
CLIENT_VENV_PATH="${CLIENT_PATH}/.venv"

if [[ "${OSTYPE}" == "linux"* ]]; then
  CLIENT_CONFIG_PATH="${CLIENT_USER_HOME}/.config/stasis-client"
  TARGET_BIN_PATH="${CLIENT_USER_HOME}/.local/bin"
  CLIENT_UI_PATH="${CLIENT_USER_HOME}/stasis-client-ui"
elif [[ "${OSTYPE}" == "darwin"* ]]; then
  CLIENT_CONFIG_PATH="${CLIENT_USER_HOME}/Library/Preferences/stasis-client"
  TARGET_BIN_PATH="/usr/local/bin"
  CLIENT_UI_PATH="${CLIENT_USER_HOME}/Applications/stasis.app"
else
  echo "[$(now)] ... operating system [${OSTYPE}] is not supported."
  exit 1
fi

echo "[$(now)] Uninstalling [stasis-client]..."
unlink "${TARGET_BIN_PATH}/stasis-client"
rm -r "${CLIENT_PATH}/bin"
rm -r "${CLIENT_PATH}/lib"

source "${CLIENT_VENV_PATH}/bin/activate"

echo "[$(now)] Uninstalling [stasis-client-cli]..."
pip3 uninstall -y stasis-client-cli
unlink "${TARGET_BIN_PATH}/stasis"

deactivate

log_debug "Removing python venv from [${CLIENT_VENV_PATH}]..."
rm -r "${CLIENT_VENV_PATH}"

echo "[$(now)] Uninstalling [stasis-client-ui]..."
unlink "${TARGET_BIN_PATH}/stasis-ui"
rm -r "${CLIENT_UI_PATH}"

echo "[$(now)] ... done."
