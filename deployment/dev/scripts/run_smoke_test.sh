#!/usr/bin/env bash

HELP="Runs basic tests against deployed services"
USAGE="Usage: $0"

if [ "$1" = "-h" ] || [ "$1" = "--help" ]
then
  echo "${HELP}"
  echo "${USAGE}"
  exit
fi

CLIENT_ID="a928359a-e2ee-4db7-9307-8071b2a1c756"
CLIENT_PASSWORD="test-secret"

USER_ID="test-user"
USER_PASSWORD="passw0rd"

OAUTH_TOKEN_URL="https://localhost:8080/oauth/token"
OAUTH_URN="urn:stasis:identity:audience"

SERVER_API="server-api"
SERVER_NODE="b4885566-dd69-4b7f-be7f-0568611d1a20"

SERVER_API_URL="https://localhost:19090"
SERVER_CORE_URL="https://localhost:19091"

HEADER_JSON="Content-Type: application/json"
HEADER_DATA="Content-Type: application/octet-stream"

echo "Started: [$(date -Is)]"

echo ">: Requesting client token for [${CLIENT_ID}]..."
CLIENT_TOKEN_REQUEST_PARAMS="grant_type=client_credentials&scope=${OAUTH_URN}:${SERVER_NODE}"
CLIENT_TOKEN=$(curl -sk -u "${CLIENT_ID}:${CLIENT_PASSWORD}" -X POST "${OAUTH_TOKEN_URL}?${CLIENT_TOKEN_REQUEST_PARAMS}" | jq -r .access_token)

echo ">: Requesting user token for [${USER_ID}]..."
USER_TOKEN_REQUEST_PARAMS="grant_type=password&username=${USER_ID}&password=${USER_PASSWORD}&scope=${OAUTH_URN}:${SERVER_API}"
USER_TOKEN=$(curl -sk -u "${CLIENT_ID}:${CLIENT_PASSWORD}" -X POST "${OAUTH_TOKEN_URL}?${USER_TOKEN_REQUEST_PARAMS}" | jq -r .access_token)

CRATE_ID=$(uuidgen)
CRATE_DATA_SIZE=64

echo -n ">: Generating [${CRATE_DATA_SIZE}] bytes of data for crate [${CRATE_ID}]... "
CRATE_DATA=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w ${CRATE_DATA_SIZE} | head -n 1)

if [ "${CRATE_DATA}" != "" ]
then
  echo "OK"
else
  echo "failed; no data generated"
  exit 1
fi

RESERVATION_REQUEST_ID=$(uuidgen)
RESERVATION_REQUEST="{
  \"id\": \"${RESERVATION_REQUEST_ID}\",
  \"crate\": \"${CRATE_ID}\",
  \"size\": ${CRATE_DATA_SIZE},
  \"copies\": 1,
  \"origin\": \"${CLIENT_ID}\",
  \"source\": \"${CLIENT_ID}\"
}"

echo -n ">: Requesting reservation for crate [${CRATE_ID}]... "
RESERVATION_RESULT=$(curl -sk -H "${HEADER_JSON}" -H "Authorization: Bearer ${CLIENT_TOKEN}" -X PUT "${SERVER_CORE_URL}/reservations" -d "${RESERVATION_REQUEST}")
RESERVATION_ID=$(jq -r .id <<< "${RESERVATION_RESULT}")

if [ $? = 0 ]
then
  echo "OK"
else
  echo "failed: [${RESERVATION_RESULT}]"
  exit 1
fi

echo -n ">: Pushing crate [${CRATE_ID}] with reservation [${RESERVATION_ID}]... "
PUSH_RESULT=$(curl -sk -H "${HEADER_DATA}" -H "Authorization: Bearer ${CLIENT_TOKEN}" -X PUT "${SERVER_CORE_URL}/crates/${CRATE_ID}?reservation=${RESERVATION_ID}" -d "${CRATE_DATA}")
echo "${PUSH_RESULT}"

echo -n ">: Pulling crate [${CRATE_ID}]... "
PULLED_CRATE_DATA=$(curl -sk -H "Authorization: Bearer ${CLIENT_TOKEN}" -X GET "${SERVER_CORE_URL}/crates/${CRATE_ID}")

if [ "${PULLED_CRATE_DATA}" = "${CRATE_DATA}" ]
then
  echo "OK"
else
  echo "failed: [${PULLED_CRATE_DATA}]"
  exit 1
fi

echo -n ">: Removing crate [${CRATE_ID}]... "
DELETE_RESULT=$(curl -sk -H "${HEADER_DATA}" -H "Authorization: Bearer ${CLIENT_TOKEN}" -X DELETE "${SERVER_CORE_URL}/crates/${CRATE_ID}")
echo "${DELETE_RESULT}"

echo -n ">: Pulling removed crate [${CRATE_ID}]... "
PULLED_CRATE_RESULT=$(curl -sk -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${CLIENT_TOKEN}" -X GET "${SERVER_CORE_URL}/crates/${CRATE_ID}")

if [ "${PULLED_CRATE_RESULT}" = "404" ]
then
  echo "not found (OK)"
else
  echo "failed: [${PULLED_CRATE_RESULT}]"
  exit 1
fi

echo -n ">: Retrieving users..."
EXPECTED_USERS_COUNT=1
ACTUAL_USERS_COUNT=$(curl -sk -H "Authorization: Bearer ${USER_TOKEN}" -X GET "${SERVER_API_URL}/users" | jq ". | length")
if [ "${ACTUAL_USERS_COUNT}" = "${EXPECTED_USERS_COUNT}" ]
then
  echo "found [${ACTUAL_USERS_COUNT}] (OK)"
else
  echo "failed; expected [${EXPECTED_USERS_COUNT}] but found [${ACTUAL_USERS_COUNT}]"
fi

echo -n ">: Retrieving devices..."
EXPECTED_DEVICES_COUNT=1
ACTUAL_DEVICES_COUNT=$(curl -sk -H "Authorization: Bearer ${USER_TOKEN}" -X GET "${SERVER_API_URL}/devices" | jq ". | length")
if [ "${ACTUAL_DEVICES_COUNT}" = "${EXPECTED_DEVICES_COUNT}" ]
then
  echo "found [${ACTUAL_DEVICES_COUNT}] (OK)"
else
  echo "failed; expected [${EXPECTED_DEVICES_COUNT}] but found [${ACTUAL_DEVICES_COUNT}]"
fi

echo -n ">: Retrieving nodes..."
EXPECTED_NODES_COUNT=2
ACTUAL_NODES_COUNT=$(curl -sk -H "Authorization: Bearer ${USER_TOKEN}" -X GET "${SERVER_API_URL}/nodes" | jq ". | length")
if [ "${ACTUAL_NODES_COUNT}" = "${EXPECTED_NODES_COUNT}" ]
then
  echo "found [${ACTUAL_NODES_COUNT}] (OK)"
else
  echo "failed; expected [${EXPECTED_NODES_COUNT}] but found [${ACTUAL_NODES_COUNT}]"
fi

echo "Completed: [$(date -Is)]"
