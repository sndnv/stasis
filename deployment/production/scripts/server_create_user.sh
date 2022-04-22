#!/usr/bin/env bash

HELP="Create new users via the server API"
USAGE="Usage: $0 [identity url] [server API url]"

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

prompt "Management User ID"
USER_ID=$(get_param)
require "${USER_ID}" "<management user ID> is required for authentication"

prompt "Management User Password"
USER_PASSWORD=$(get_secret)
render_secret "${USER_PASSWORD}"
require "${USER_PASSWORD}" "<management user password> is required for authentication"

prompt "NEW User ID"
NEW_USER_ID=$(get_param)
require "${NEW_USER_ID}" "<new user ID> is required"

prompt "NEW User Password"
NEW_USER_PASSWORD=$(get_secret)
render_secret "${NEW_USER_PASSWORD}"
require "${NEW_USER_PASSWORD}" "<new user password> is required"

prompt "NEW User Password (verify)"
NEW_USER_PASSWORD_VERIFY=$(get_secret)
render_secret "${NEW_USER_PASSWORD_VERIFY}"
require "${NEW_USER_PASSWORD_VERIFY}" "<new user password> is required"

prompt "NEW User Permissions (CSV)"
NEW_USER_PERMISSIONS=$(get_param)
require "${NEW_USER_PERMISSIONS}" "<new user permissions> are required"

PERMISSIONS_LIST=""
IFS=',' read -a SPLIT_PERMISSIONS <<< "$NEW_USER_PERMISSIONS"
for CURRENT_PERMISSION in "${SPLIT_PERMISSIONS[@]}" ; do
    PERMISSIONS_LIST="${PERMISSIONS_LIST}\"${CURRENT_PERMISSION}\","
done
PERMISSIONS_LIST=${PERMISSIONS_LIST%,}

IDENTITY_TOKEN_URL=${1:-"https://localhost:42100/oauth/token"}
IDENTITY_URN="urn:stasis:identity:audience"

SERVER_API="stasis-server-api"
SERVER_API_URL=${2:-"https://localhost:42300"}

echo -n "[$(now)] Requesting user token for [${USER_ID}]..."
USER_TOKEN_REQUEST_PARAMS="grant_type=password&username=${USER_ID}&password=${USER_PASSWORD}&scope=${IDENTITY_URN}:${SERVER_API}"
USER_TOKEN=$(curl -sk -u "${CLIENT_ID}:${CLIENT_SECRET}" -X POST "${IDENTITY_TOKEN_URL}?${USER_TOKEN_REQUEST_PARAMS}" | jq -r .access_token)

if [ "${USER_TOKEN}" != "" ]
then
  echo "OK"
else
  echo "failed; expected access token but none was returned."
  exit 1
fi

CREATE_USER_REQUEST="{
  \"username\": \"${NEW_USER_ID}\",
  \"raw_password\": \"${NEW_USER_PASSWORD}\",
  \"permissions\": [${PERMISSIONS_LIST}]
}"

echo -n "[$(now)] Creating user [${NEW_USER_ID}]..."
CREATE_USER_RESULT=$(curl -sk -H "Content-Type: application/json" -H "Authorization: Bearer ${USER_TOKEN}" -X POST "${SERVER_API_URL}/users" -d "${CREATE_USER_REQUEST}")
CREATED_USER_ID=$(jq -r .user <<< "${CREATE_USER_RESULT}")

if [ "${CREATED_USER_ID}" != "" ]
then
  echo "created [${CREATED_USER_ID}]"
else
  echo "failed: [${CREATE_USER_RESULT}]"
  exit 1
fi

echo -n "[$(now)] Request user information for [${NEW_USER_ID}]..."
QUERY_USER_RESULT=$(curl -sk -H "Content-Type: application/json" -H "Authorization: Bearer ${USER_TOKEN}" -X GET "${SERVER_API_URL}/users/${CREATED_USER_ID}")
QUERY_USER_SALT=$(jq -r .salt <<< "${QUERY_USER_RESULT}")

if [ "${QUERY_USER_SALT}" != "" ]
then
  echo "found salt [${QUERY_USER_SALT}] for user [${CREATED_USER_ID}]"
else
  echo "failed: [${QUERY_USER_RESULT}]"
  exit 1
fi
