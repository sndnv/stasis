#!/usr/bin/env bash

HELP="Retrieve bootstrap codes for Android client"
USAGE="Usage: $0"

if [ "$1" = "-h" ] || [ "$1" = "--help" ]
then
  echo "${HELP}"
  echo "${USAGE}"
  exit
fi

CLIENT_ID="22682c16-0184-40a5-9611-ecb304122a02"
CLIENT_PASSWORD="mobile-secret"

USER_ID="mobile-user"
USER_PASSWORD="passw0rd"
USER_PASSWORD_DERIVED="DvoOjDbYhKGDE9YKBcEBcw" # `passw0rd` - derived with salt `7e9b9db3dfe6`

DEVICE_ID="570c237e-0507-47d7-b90b-8a9a3947fcbc"

OAUTH_TOKEN_URL="https://localhost:8080/oauth/token"
OAUTH_URN="urn:stasis:identity:audience"

SERVER_API="server-api"

SERVER_BOOTSTRAP_URL="https://localhost:20002"
SERVER_BOOTSTRAP_URL_INTERNAL="https://server:20002"

HEADER_JSON="Content-Type: application/json"

function now() {
  timestamp="$(date +"%Y-%m-%dT%H:%M:%SZ")"
  echo "${timestamp}"
}

echo "[$(now)] Requesting user token for [${USER_ID}]..."
USER_TOKEN_REQUEST_PARAMS="grant_type=password&username=${USER_ID}&password=${USER_PASSWORD_DERIVED}&scope=${OAUTH_URN}:${SERVER_API}"
USER_TOKEN=$(curl -sk -u "${CLIENT_ID}:${CLIENT_PASSWORD}" -X POST "${OAUTH_TOKEN_URL}?${USER_TOKEN_REQUEST_PARAMS}" | jq -r .access_token)

echo -n "[$(now)] Retrieving device bootstrap code..."
DEVICE_BOOTSTRAP_CODE_RESULT=$(curl -sk -H "${HEADER_JSON}" -H "Authorization: Bearer ${USER_TOKEN}" -X PUT "${SERVER_BOOTSTRAP_URL}/devices/codes/own/for-device/${DEVICE_ID}")
DEVICE_BOOTSTRAP_CODE=$(jq -r .value <<< "${DEVICE_BOOTSTRAP_CODE_RESULT}")

if [ "${DEVICE_BOOTSTRAP_CODE}" != "" ]
then
  echo "received [${DEVICE_BOOTSTRAP_CODE}]"
else
  echo "failed: [${DEVICE_BOOTSTRAP_CODE_RESULT}]"
  exit 1
fi

echo "[$(now)] Bootstrap parameters:"
echo "    Server:   ${SERVER_BOOTSTRAP_URL_INTERNAL}"
echo "    Password: ${USER_PASSWORD}"
echo "    Code:     ${DEVICE_BOOTSTRAP_CODE}"
