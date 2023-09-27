#!/usr/bin/env bash

HELP="Installs stasis-client, stasis-client-cli and stasis-client-ui for the current user"
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

function failed() {
  echo "[$(now)] ... failed."
  exit 1
}

function file_name_from_path() {
  local FILE="${1##*/}"
  echo "${FILE%.*}"
}

function run_grep() {
  if command -v ggrep &> /dev/null
  then
    ggrep "$@"
  else
    grep "$@"
  fi
}

SCRIPTS_DIR=$(unset CDPATH && cd "$(dirname "$0")" && echo "${PWD}")
PRODUCTION_DEPLOYMENT_DIR=$(dirname "${SCRIPTS_DIR}")
DEPLOYMENT_DIR=$(dirname "${PRODUCTION_DEPLOYMENT_DIR}")
REPO_DIR=$(dirname "${DEPLOYMENT_DIR}")
CLIENT_CLI_DIR="${REPO_DIR}/client-cli"
CLIENT_UI_DIR="${REPO_DIR}/client-ui"

CLIENT_VERSION=$(cat "${REPO_DIR}/version.sbt" | run_grep -oP "\K\".+\"" | tr -d \")
CLIENT_CLI_VERSION=$(cat "${CLIENT_CLI_DIR}/setup.py" | run_grep -oP "\Kversion='.+'" | run_grep -oP "\K'.+'" | tr -d \')
CLIENT_UI_VERSION=$(cat "${CLIENT_UI_DIR}/pubspec.yaml" | run_grep -oP "\Kversion: .+" | run_grep -oP "\K: .+" | tr -d :)

if [[ "${OSTYPE}" == "linux"* ]]; then
  CLIENT_UI_TARGET="linux"
elif [[ "${OSTYPE}" == "darwin"* ]]; then
  CLIENT_UI_TARGET="macos"
else
  echo "[$(now)] ... operating system [${OSTYPE}] is not supported."
  exit 1
fi

echo "[$(now)] Building [stasis-client]..."
(cd "${REPO_DIR}" && sbt "project client" universal:packageBin) || failed

echo "[$(now)] Building [stasis-client-cli]..."
(cd "${CLIENT_CLI_DIR}" && python3 setup.py sdist --format=zip) || failed

echo "[$(now)] Building [stasis-client-ui]..."
(cd "${CLIENT_UI_DIR}" && flutter build ${CLIENT_UI_TARGET}) || failed

CLIENT_USER=${USER}
CLIENT_USER_HOME=${HOME}

CLIENT_ARCHIVE="${REPO_DIR}/client/target/universal/stasis-client-${CLIENT_VERSION}.zip"
CLIENT_ARCHIVE_NAME=$(file_name_from_path ${CLIENT_ARCHIVE})

CLIENT_CLI_ARCHIVE="${REPO_DIR}/client-cli/dist/stasis-client-cli-${CLIENT_CLI_VERSION}.zip"
CLIENT_CLI_ARCHIVE_NAME=$(file_name_from_path ${CLIENT_CLI_ARCHIVE})

CLIENT_PATH="${CLIENT_USER_HOME}/stasis-client"
CLIENT_UI_PATH="${CLIENT_USER_HOME}/stasis-client-ui"

if [[ "${OSTYPE}" == "linux"* ]]; then
  CLIENT_CONFIG_PATH="${CLIENT_USER_HOME}/.config/stasis-client"
  TARGET_BIN_PATH="${CLIENT_USER_HOME}/.local/bin"
elif [[ "${OSTYPE}" == "darwin"* ]]; then
  CLIENT_CONFIG_PATH="${CLIENT_USER_HOME}/Library/Preferences/stasis-client"
  TARGET_BIN_PATH="/usr/local/bin"
else
  echo "[$(now)] ... operating system [${OSTYPE}] is not supported."
  exit 1
fi

CLIENT_CERTS_PATH="${CLIENT_CONFIG_PATH}/certs"
CLIENT_LOGS_PATH="${CLIENT_PATH}/logs"
CLIENT_STATE_PATH="${CLIENT_PATH}/state"

echo "[$(now)] Installing [stasis-client]..."

echo "[$(now)] Setting up client directory [${CLIENT_PATH}]..."
mkdir -p ${CLIENT_PATH} || failed

echo "[$(now)] Setting up client directory [${CLIENT_UI_PATH}]..."
mkdir -p ${CLIENT_UI_PATH} || failed

echo "[$(now)] Setting up client config directory [${CLIENT_CONFIG_PATH}]..."
mkdir -p ${CLIENT_CONFIG_PATH} || failed
chown -R ${CLIENT_USER} ${CLIENT_PATH} || failed
chmod +w ${CLIENT_PATH} || failed
chown -R ${CLIENT_USER} ${CLIENT_UI_PATH} || failed
chmod +w ${CLIENT_UI_PATH} || failed

echo "[$(now)] Setting up client certificates directory [${CLIENT_CERTS_PATH}]..."
mkdir -p ${CLIENT_CERTS_PATH} || failed
chown -R ${CLIENT_USER} ${CLIENT_CERTS_PATH} || failed
chmod +w ${CLIENT_CERTS_PATH} || failed

echo "[$(now)] Setting up client logs directory [${CLIENT_LOGS_PATH}]..."
mkdir -p ${CLIENT_LOGS_PATH} || failed
chown -R ${CLIENT_USER} ${CLIENT_LOGS_PATH} || failed
chmod +w ${CLIENT_LOGS_PATH} || failed
touch "${CLIENT_LOGS_PATH}/stasis-client.log" || failed

echo "[$(now)] Setting up client state directory [${CLIENT_STATE_PATH}]..."
mkdir -p "${CLIENT_STATE_PATH}/backups" || failed
mkdir -p "${CLIENT_STATE_PATH}/recoveries" || failed
chown -R ${CLIENT_USER} ${CLIENT_STATE_PATH} || failed
chmod +w ${CLIENT_STATE_PATH} || failed

echo "[$(now)] Extracting client from [${CLIENT_ARCHIVE}] to [${CLIENT_PATH}]..."
unzip -o -q -d "${CLIENT_PATH}/" ${CLIENT_ARCHIVE} || failed
mv -f ${CLIENT_PATH}/${CLIENT_ARCHIVE_NAME}/* ${CLIENT_PATH} || failed
rm -r "${CLIENT_PATH}/${CLIENT_ARCHIVE_NAME}" || failed
ln -s "${CLIENT_PATH}/bin/stasis-client" "${TARGET_BIN_PATH}/stasis-client" || failed

echo "[$(now)] Installing [stasis-client-cli]..."
pip3 install ${CLIENT_CLI_ARCHIVE} || failed
ln -s "$(which stasis-client-cli)" "${TARGET_BIN_PATH}/stasis"

echo "[$(now)] Installing [stasis-client-ui]..."
cp -R ${CLIENT_UI_DIR}/build/${CLIENT_UI_TARGET}/x64/release/bundle/* ${CLIENT_UI_PATH}/ || failed
ln -s ${CLIENT_UI_PATH}/stasis_client_ui "${TARGET_BIN_PATH}/stasis-ui"

echo "[$(now)] ... done."
