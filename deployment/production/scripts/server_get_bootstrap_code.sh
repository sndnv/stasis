#!/usr/bin/env bash

HELP="Retrieve bootstrap codes"
USAGE="Usage: $0 [password size] [password iterations] [password salt prefix] [identity url] [server API url]"

if [ "$1" = "-h" ] || [ "$1" = "--help" ]
then
  echo "${HELP}"
  echo "${USAGE}"
  exit
fi

function prompt() {
    echo -n "$1: "
}

function get_param() {
  IFS= read -r PARAM
  echo ${PARAM}
}

function get_secret() {
    local CURRENT_STATE=$(stty -g)
    stty -echo
    IFS= read -r SECRET
    stty "${CURRENT_STATE}"
    echo ${SECRET}
}

function render_secret() {
    echo "$1" | sed 's/./*/g'
}

function require() {
    local VALUE=$1
    local MESSAGE=$2

    if [ "${VALUE}" = "" ]
    then
      echo "${MESSAGE}"
      echo "${USAGE}"
      exit
    fi
}

function now() {
  timestamp="$(date +"%Y-%m-%dT%H:%M:%SZ")"
  echo "${timestamp}"
}

prompt "Client ID"
CLIENT_ID=$(get_param)
require "${CLIENT_ID}" "<client ID> is required for authentication"

prompt "Client Secret"
CLIENT_SECRET=$(get_secret)
render_secret "${CLIENT_SECRET}"
require "${CLIENT_SECRET}" "<client secret> is required for authentication"

prompt "User ID"
USER_ID=$(get_param)
require "${USER_ID}" "<user ID> is required for authentication"

prompt "User Password"
USER_PASSWORD=$(get_secret)
render_secret "${USER_PASSWORD}"
require "${USER_PASSWORD}" "<user password> is required for authentication"

prompt "User Salt"
USER_SALT=$(get_param)
require "${USER_SALT}" "<user salt> is required for authentication"

prompt "Device ID"
DEVICE_ID=$(get_param)
require "${DEVICE_ID}" "<user ID> is required"

PASSWORD_SIZE=${1:-"24"}
PASSWORD_ITERATIONS=${2:-"150000"}
PASSWORD_SALT_PREFIX=${3:-"changeme"}

USER_PASSWORD_DERIVED=$(./generate_user_password.py -q --user-salt ${USER_SALT} --user-password ${USER_PASSWORD} --authentication-password-size ${PASSWORD_SIZE} --authentication-password-iterations ${PASSWORD_ITERATIONS} --authentication-password-salt-prefix ${PASSWORD_SALT_PREFIX})

IDENTITY_TOKEN_URL=${4:-"https://localhost:42100/oauth/token"}
IDENTITY_URN="urn:stasis:identity:audience"

SERVER_API="stasis-server-api"
SERVER_BOOTSTRAP_URL=${5:-"https://localhost:42302"}

echo "[$(now)] Requesting user token for [${USER_ID}]..."
USER_TOKEN_REQUEST_PARAMS="grant_type=password&username=${USER_ID}&password=${USER_PASSWORD_DERIVED}&scope=${IDENTITY_URN}:${SERVER_API}"
USER_TOKEN=$(curl -sk -u "${CLIENT_ID}:${CLIENT_SECRET}" -X POST "${IDENTITY_TOKEN_URL}?${USER_TOKEN_REQUEST_PARAMS}" | jq -r .access_token)

echo -n "[$(now)] Retrieving device bootstrap code..."
DEVICE_BOOTSTRAP_CODE_RESULT=$(curl -sk -H "Content-Type: application/json" -H "Authorization: Bearer ${USER_TOKEN}" -X PUT "${SERVER_BOOTSTRAP_URL}/devices/codes/own/for-device/${DEVICE_ID}")
DEVICE_BOOTSTRAP_CODE=$(jq -r .value <<< "${DEVICE_BOOTSTRAP_CODE_RESULT}")

if [ "${DEVICE_BOOTSTRAP_CODE}" != "" ]
then
  echo "received [${DEVICE_BOOTSTRAP_CODE}]"
else
  echo "failed: [${DEVICE_BOOTSTRAP_CODE_RESULT}]"
  exit 1
fi

echo "[$(now)] Bootstrap parameters:"
echo "    Server:   ${SERVER_BOOTSTRAP_URL}"
echo "    Code:     ${DEVICE_BOOTSTRAP_CODE}"
