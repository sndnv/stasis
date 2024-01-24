#!/usr/bin/env bash

HELP="Create new devices via the server API"
USAGE="Usage: $0 [<password size>|unhashed] [<password iterations>] [<password salt prefix>] [<identity url>] [<server API url>]"

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

PASSWORD_SIZE=${1:-"24"}
PASSWORD_ITERATIONS=${2:-"150000"}
PASSWORD_SALT_PREFIX=${3-"changeme"}

if [ "${PASSWORD_SIZE}" == "unhashed" ]
then
  SKIP_HASHING="true"
else
  SKIP_HASHING="false"
fi

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

if [ "${SKIP_HASHING}" != "true" ]
then
  prompt "User Salt"
  USER_SALT=$(get_param)
  require "${USER_SALT}" "<user salt> is required for authentication"
fi

prompt "Device Name"
DEVICE_NAME=$(get_param)
require "${DEVICE_NAME}" "<device name> is required"

if [ "${SKIP_HASHING}" != "true" ]
then
  USER_PASSWORD_DERIVED=$(./generate_user_password.py -q --user-salt ${USER_SALT} --user-password ${USER_PASSWORD} --authentication-password-size ${PASSWORD_SIZE} --authentication-password-iterations ${PASSWORD_ITERATIONS} --authentication-password-salt-prefix ${PASSWORD_SALT_PREFIX})
else
  USER_PASSWORD_DERIVED=${USER_PASSWORD}
fi

IDENTITY_TOKEN_URL=${4:-"https://localhost:42100/oauth/token"}
IDENTITY_URN="urn:stasis:identity:audience"

SERVER_API="stasis-server-api"
SERVER_API_URL=${5:-"https://localhost:42300"}

echo -n "[$(now)] Requesting user token for [${USER_ID}]..."
USER_TOKEN_REQUEST_PARAMS="grant_type=password&username=${USER_ID}&password=${USER_PASSWORD_DERIVED}&scope=${IDENTITY_URN}:${SERVER_API}"
USER_TOKEN=$(curl -sk -u "${CLIENT_ID}:${CLIENT_SECRET}" -X POST "${IDENTITY_TOKEN_URL}?${USER_TOKEN_REQUEST_PARAMS}" | jq -r .access_token)

if [ "${USER_TOKEN}" != "" ]
then
  echo "OK"
else
  echo "failed; expected access token but none was returned."
  exit 1
fi

CREATE_DEVICE_REQUEST="{\"name\":\"${DEVICE_NAME}\"}"

CREATE_DEVICE_RESULT=$(curl -sk -H "Content-Type: application/json" -H "Authorization: Bearer ${USER_TOKEN}" -X POST "${SERVER_API_URL}/v1/devices/own" -d "${CREATE_DEVICE_REQUEST}")

echo ${CREATE_DEVICE_RESULT}
