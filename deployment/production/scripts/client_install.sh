#!/usr/bin/env bash

HELP="Installs stasis-client and stasis-client-cli for the current user"
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

SCRIPTS_DIR=$(unset CDPATH && cd "$(dirname "$0")" && echo "${PWD}")
PRODUCTION_DEPLOYMENT_DIR=$(dirname "${SCRIPTS_DIR}")
DEPLOYMENT_DIR=$(dirname "${PRODUCTION_DEPLOYMENT_DIR}")
REPO_DIR=$(dirname "${DEPLOYMENT_DIR}")
CLIENT_CLI_DIR="${REPO_DIR}/client-cli"

CLIENT_VERSION=$(cat "${REPO_DIR}/version.sbt" | grep -oP "\K\".+\"" | tr -d \")
CLIENT_CLI_VERSION=$(cat "${CLIENT_CLI_DIR}/setup.py" | grep -oP "\Kversion='.+'" | grep -oP "\K'.+'" | tr -d \')

echo "[$(now)] Building [stasis-client]..."
(cd "${REPO_DIR}" && sbt "project client" universal:packageBin) || failed

echo "[$(now)] Building [stasis-client-cli]..."
(cd "${CLIENT_CLI_DIR}" && python3 setup.py sdist --format=zip) || failed

CLIENT_USER=${USER}
CLIENT_USER_HOME=${HOME}

CLIENT_ARCHIVE="${REPO_DIR}/client/target/universal/stasis-client-${CLIENT_VERSION}.zip"
CLIENT_ARCHIVE_NAME=$(file_name_from_path ${CLIENT_ARCHIVE})

CLIENT_CLI_ARCHIVE="${REPO_DIR}/client-cli/dist/stasis-client-cli-${CLIENT_CLI_VERSION}.zip"
CLIENT_CLI_ARCHIVE_NAME=$(file_name_from_path ${CLIENT_CLI_ARCHIVE})

CLIENT_PATH="${CLIENT_USER_HOME}/stasis-client"
CLIENT_CONFIG_PATH="${CLIENT_USER_HOME}/.config/stasis-client"
CLIENT_CERTS_PATH="${CLIENT_CONFIG_PATH}/certs"
CLIENT_LOGS_PATH="${CLIENT_PATH}/logs"

echo "[$(now)] Installing [stasis-client]..."

echo "[$(now)] Setting up client directory [${CLIENT_PATH}]..."
mkdir -p ${CLIENT_PATH} || failed

echo "[$(now)] Setting up client config directory [${CLIENT_CONFIG_PATH}]..."
mkdir -p ${CLIENT_CONFIG_PATH} || failed
chown -R ${CLIENT_USER} ${CLIENT_PATH} || failed
chmod +w ${CLIENT_PATH} || failed

echo "[$(now)] Setting up client certificates directory [${CLIENT_CERTS_PATH}]..."
mkdir -p ${CLIENT_CERTS_PATH} || failed
chown -R ${CLIENT_USER} ${CLIENT_CERTS_PATH} || failed
chmod +w ${CLIENT_CERTS_PATH} || failed

echo "[$(now)] Setting up client logs directory [${CLIENT_LOGS_PATH}]..."
mkdir -p ${CLIENT_LOGS_PATH} || failed
chown -R ${CLIENT_USER} ${CLIENT_LOGS_PATH} || failed
chmod +w ${CLIENT_LOGS_PATH} || failed
touch "${CLIENT_LOGS_PATH}/stasis-client.log" || failed

echo "[$(now)] Extracting client from [${CLIENT_ARCHIVE}] to [${CLIENT_PATH}]..."
unzip -q -d "${CLIENT_PATH}/" ${CLIENT_ARCHIVE} || failed
mv ${CLIENT_PATH}/${CLIENT_ARCHIVE_NAME}/* ${CLIENT_PATH} || failed
rm -r "${CLIENT_PATH}/${CLIENT_ARCHIVE_NAME}" || failed
ln -s "${CLIENT_PATH}/bin/stasis-client" "${CLIENT_USER_HOME}/.local/bin/stasis-client" || failed

echo "[$(now)] Installing [stasis-client-cli]..."
pip3 install ${CLIENT_CLI_ARCHIVE} || failed
ln -s "${CLIENT_USER_HOME}/.local/bin/stasis-client-cli" "${CLIENT_USER_HOME}/.local/bin/stasis"

echo "[$(now)] ... done."
