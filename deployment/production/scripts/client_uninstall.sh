#!/usr/bin/env bash

HELP="Uninstalls stasis-client and stasis-client-cli for the current user"
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
CLIENT_CONFIG_PATH="${CLIENT_USER_HOME}/.config/stasis-client"

echo "[$(now)] Uninstalling [stasis-client]..."
unlink "${CLIENT_USER_HOME}/.local/bin/stasis-client"
rm "${CLIENT_PATH}/bin/stasis-client"
rm "${CLIENT_PATH}/bin/stasis-client.bat"
rm -d "${CLIENT_PATH}/bin"
rm ${CLIENT_PATH}/lib/*.jar
rm -d "${CLIENT_PATH}/lib"

echo "[$(now)] Uninstalling [stasis-client-cli]..."
pip3 uninstall -y stasis-client-cli
unlink "${CLIENT_USER_HOME}/.local/bin/stasis"

echo "[$(now)] ... done."
