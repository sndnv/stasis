#!/usr/bin/env bash

HELP="Run basic tests against deployed services"
USAGE="Usage: $0"

if [ "$1" = "-h" ] || [ "$1" = "--help" ]
then
  echo "${HELP}"
  echo "${USAGE}"
  exit
fi

SMOKE_TEST_CLIENT_ID="1068208a-3cd7-4121-bad7-050dee3b4494"
CLIENT_PASSWORD="test-secret"

USER_ID="test-user"
USER_PASSWORD="passw0rd"
USER_PASSWORD_DERIVED="QcilMRSTjZvClf-I6Ac2RA" # `passw0rd` - derived with salt `d92beb8f7c3b`

SECONDARY_DEVICE_ID="7dee7457-da81-48bb-aae0-7cfdb1f827a8"

OAUTH_TOKEN_URL="https://localhost:10000/oauth/token"
OAUTH_URN="urn:stasis:identity:audience"

SERVER_API="server-api"
SERVER_NODE="b4885566-dd69-4b7f-be7f-0568611d1a20"

SERVER_API_URL="https://localhost:20000"
SERVER_CORE_URL="https://localhost:20001"
SERVER_BOOTSTRAP_URL="https://localhost:20002"
SERVER_BOOTSTRAP_URL_INTERNAL="https://server:20002"

HEADER_JSON="Content-Type: application/json"
HEADER_DATA="Content-Type: application/octet-stream"

PRIMARY_CLIENT_CONTAINER_NAME="primary-client"
SECONDARY_CLIENT_CONTAINER_NAME="secondary-client"
CLIENT_CONTAINER_HOME="/home/demiourgos728"
CLIENT_CONTAINER_CONFIG="${CLIENT_CONTAINER_OME}/.config/stasis-client"

CLIENT_CONFIG_FILES=()
CLIENT_CONFIG_FILES+=("${CLIENT_CONTAINER_CONFIG}/authentication.p12")
CLIENT_CONFIG_FILES+=("${CLIENT_CONTAINER_CONFIG}/client-api.p12")
CLIENT_CONFIG_FILES+=("${CLIENT_CONTAINER_CONFIG}/client-api.p12.as.pem")
CLIENT_CONFIG_FILES+=("${CLIENT_CONTAINER_CONFIG}/client.conf")
CLIENT_CONFIG_FILES+=("${CLIENT_CONTAINER_CONFIG}/client.rules")
CLIENT_CONFIG_FILES+=("${CLIENT_CONTAINER_CONFIG}/client.schedules")
CLIENT_CONFIG_FILES+=("${CLIENT_CONTAINER_CONFIG}/device-secret")

CLIENT_BACKUP_DIRS=()
CLIENT_BACKUP_DIRS+=("${CLIENT_CONTAINER_HOME}/backup")
CLIENT_BACKUP_DIRS+=("${CLIENT_CONTAINER_HOME}/backup/0")
CLIENT_BACKUP_DIRS+=("${CLIENT_CONTAINER_HOME}/backup/0/1")
CLIENT_BACKUP_DIRS+=("${CLIENT_CONTAINER_HOME}/backup/0/2")
CLIENT_BACKUP_DIRS_COUNT=${#CLIENT_BACKUP_DIRS[@]}

CLIENT_RECOVERY_DIR="${CLIENT_CONTAINER_HOME}/recover"

CLIENT_TEST_FILES=()
CLIENT_TEST_FILES+=("${CLIENT_CONTAINER_HOME}/backup/a")
CLIENT_TEST_FILES+=("${CLIENT_CONTAINER_HOME}/backup/b")
CLIENT_TEST_FILES+=("${CLIENT_CONTAINER_HOME}/backup/c")
CLIENT_TEST_FILES+=("${CLIENT_CONTAINER_HOME}/backup/0/d")
CLIENT_TEST_FILES+=("${CLIENT_CONTAINER_HOME}/backup/0/e")
CLIENT_TEST_FILES+=("${CLIENT_CONTAINER_HOME}/backup/0/1/f")
CLIENT_TEST_FILES+=("${CLIENT_CONTAINER_HOME}/backup/0/2/g")
CLIENT_TEST_FILES_COUNT=${#CLIENT_TEST_FILES[@]}

CLIENT_UPDATED_TEST_FILE="${CLIENT_CONTAINER_HOME}/backup/0/d"

declare -A CLIENT_TEST_FILE_SHA_SUMS

function now() {
  timestamp="$(date +"%Y-%m-%dT%H:%M:%SZ")"
  echo "${timestamp}"
}

TEST_START="$(date +%s)"

echo "Started: [$(now)]"

echo "[$(now)] Requesting client token for [${SMOKE_TEST_CLIENT_ID}]..."
CLIENT_TOKEN_REQUEST_PARAMS="grant_type=client_credentials&scope=${OAUTH_URN}:${SERVER_NODE}"
CLIENT_TOKEN=$(curl -sk -u "${SMOKE_TEST_CLIENT_ID}:${CLIENT_PASSWORD}" -X POST "${OAUTH_TOKEN_URL}?${CLIENT_TOKEN_REQUEST_PARAMS}" | jq -r .access_token)

echo "[$(now)] Requesting user token for [${USER_ID}]..."
USER_TOKEN_REQUEST_PARAMS="grant_type=password&username=${USER_ID}&password=${USER_PASSWORD_DERIVED}&scope=${OAUTH_URN}:${SERVER_API}"
USER_TOKEN=$(curl -sk -u "${SMOKE_TEST_CLIENT_ID}:${CLIENT_PASSWORD}" -X POST "${OAUTH_TOKEN_URL}?${USER_TOKEN_REQUEST_PARAMS}" | jq -r .access_token)

CRATE_ID=$(uuidgen)
CRATE_DATA_SIZE=64

echo -n "[$(now)] Generating [${CRATE_DATA_SIZE}] bytes of data for crate [${CRATE_ID}]... "
CRATE_DATA=$(cat /dev/urandom | LC_ALL=C tr -dc 'a-zA-Z0-9' | fold -w ${CRATE_DATA_SIZE} | head -n 1)

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
  \"origin\": \"${SMOKE_TEST_CLIENT_ID}\",
  \"source\": \"${SMOKE_TEST_CLIENT_ID}\"
}"

echo -n "[$(now)] Requesting reservation for crate [${CRATE_ID}]... "
RESERVATION_RESULT=$(curl -sk -H "${HEADER_JSON}" -H "Authorization: Bearer ${CLIENT_TOKEN}" -X PUT "${SERVER_CORE_URL}/reservations" -d "${RESERVATION_REQUEST}")
RESERVATION_ID=$(jq -r .id <<< "${RESERVATION_RESULT}")

if [ $? = 0 ]
then
  echo "OK"
else
  echo "failed: [${RESERVATION_RESULT}]"
  exit 1
fi

echo -n "[$(now)] Pushing crate [${CRATE_ID}] with reservation [${RESERVATION_ID}]... "
PUSH_RESULT=$(curl -sk -H "${HEADER_DATA}" -H "Authorization: Bearer ${CLIENT_TOKEN}" -X PUT "${SERVER_CORE_URL}/crates/${CRATE_ID}?reservation=${RESERVATION_ID}" -d "${CRATE_DATA}")
echo "${PUSH_RESULT}"

echo -n "[$(now)] Pulling crate [${CRATE_ID}]... "
PULLED_CRATE_DATA=$(curl -sk -H "Authorization: Bearer ${CLIENT_TOKEN}" -X GET "${SERVER_CORE_URL}/crates/${CRATE_ID}")

if [ "${PULLED_CRATE_DATA}" = "${CRATE_DATA}" ]
then
  echo "OK"
else
  echo "failed: [${PULLED_CRATE_DATA}]"
  exit 1
fi

echo -n "[$(now)] Removing crate [${CRATE_ID}]... "
DELETE_RESULT=$(curl -sk -H "${HEADER_DATA}" -H "Authorization: Bearer ${CLIENT_TOKEN}" -X DELETE "${SERVER_CORE_URL}/crates/${CRATE_ID}")
echo "${DELETE_RESULT}"

echo -n "[$(now)] Pulling removed crate [${CRATE_ID}]... "
PULLED_CRATE_RESULT=$(curl -sk -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${CLIENT_TOKEN}" -X GET "${SERVER_CORE_URL}/crates/${CRATE_ID}")

if [ "${PULLED_CRATE_RESULT}" = "404" ]
then
  echo "not found (OK)"
else
  echo "failed: [${PULLED_CRATE_RESULT}]"
  exit 1
fi

echo -n "[$(now)] Retrieving users..."
EXPECTED_USERS_COUNT=2
ACTUAL_USERS_COUNT=$(curl -sk -H "Authorization: Bearer ${USER_TOKEN}" -X GET "${SERVER_API_URL}/v1/users" | jq ". | length")
if [ "${ACTUAL_USERS_COUNT}" = "${EXPECTED_USERS_COUNT}" ]
then
  echo "found [${ACTUAL_USERS_COUNT}] (OK)"
else
  echo "failed; expected [${EXPECTED_USERS_COUNT}] but found [${ACTUAL_USERS_COUNT}]"
fi

echo -n "[$(now)] Retrieving devices..."
EXPECTED_DEVICES_COUNT=3
ACTUAL_DEVICES_COUNT=$(curl -sk -H "Authorization: Bearer ${USER_TOKEN}" -X GET "${SERVER_API_URL}/v1/devices" | jq ". | length")
if [ "${ACTUAL_DEVICES_COUNT}" = "${EXPECTED_DEVICES_COUNT}" ]
then
  echo "found [${ACTUAL_DEVICES_COUNT}] (OK)"
else
  echo "failed; expected [${EXPECTED_DEVICES_COUNT}] but found [${ACTUAL_DEVICES_COUNT}]"
fi

echo -n "[$(now)] Retrieving nodes..."
EXPECTED_NODES_COUNT=5
ACTUAL_NODES_COUNT=$(curl -sk -H "Authorization: Bearer ${USER_TOKEN}" -X GET "${SERVER_API_URL}/v1/nodes" | jq ". | length")
if [ "${ACTUAL_NODES_COUNT}" = "${EXPECTED_NODES_COUNT}" ]
then
  echo "found [${ACTUAL_NODES_COUNT}] (OK)"
else
  echo "failed; expected [${EXPECTED_NODES_COUNT}] but found [${ACTUAL_NODES_COUNT}]"
fi

echo -n "[$(now)] Retrieving device keys..."
EXPECTED_INITIAL_DEVICE_KEYS_COUNT=0
ACTUAL_INITIAL_DEVICE_KEYS_COUNT=$(curl -sk -H "Authorization: Bearer ${USER_TOKEN}" -X GET "${SERVER_API_URL}/v1/devices/keys" | jq ". | length")
if [ "${ACTUAL_INITIAL_DEVICE_KEYS_COUNT}" = "${EXPECTED_INITIAL_DEVICE_KEYS_COUNT}" ]
then
  echo "found [${ACTUAL_INITIAL_DEVICE_KEYS_COUNT}] (OK)"
else
  echo "failed; expected [${EXPECTED_INITIAL_DEVICE_KEYS_COUNT}] but found [${ACTUAL_INITIAL_DEVICE_KEYS_COUNT}]"
fi

echo -n "[$(now)] Looking up PRIMARY client container [${PRIMARY_CLIENT_CONTAINER_NAME}]..."
PRIMARY_CLIENT_CONTAINER_ID=$(docker ps --filter "name=${PRIMARY_CLIENT_CONTAINER_NAME}" --quiet)
if [ "${PRIMARY_CLIENT_CONTAINER_ID}" != "" ]
then
  echo "found [${PRIMARY_CLIENT_CONTAINER_ID}] (OK)"
else
  echo "failed; container not found"
  exit 1
fi

echo -n "[$(now)] Looking up SECONDARY client container [${SECONDARY_CLIENT_CONTAINER_NAME}]..."
SECONDARY_CLIENT_CONTAINER_ID=$(docker ps --filter "name=${SECONDARY_CLIENT_CONTAINER_NAME}" --quiet)
if [ "${SECONDARY_CLIENT_CONTAINER_ID}" != "" ]
then
  echo "found [${SECONDARY_CLIENT_CONTAINER_ID}] (OK)"
else
  echo "failed; container not found"
  exit 1
fi

echo -n "[$(now)] (PRIMARY) Starting client service..."
CLIENT_SERVICE_START_COMMAND="stasis-client-cli --json service start --username ${USER_ID} --password ${USER_PASSWORD}"
CLIENT_SERVICE_START_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${CLIENT_SERVICE_START_COMMAND})
CLIENT_SERVICE_START_SUCCESSFUL="$(echo "${CLIENT_SERVICE_START_RESULT}" | jq '.successful')"
CLIENT_SERVICE_START_FAILURE="$(echo "${CLIENT_SERVICE_START_RESULT}" | jq -r '.failure')"
CLIENT_SERVICE_ALREADY_ACTIVE="Background service is already active"
if [ "${CLIENT_SERVICE_START_SUCCESSFUL}" = "true" ] || [ "${CLIENT_SERVICE_START_FAILURE}" = "${CLIENT_SERVICE_ALREADY_ACTIVE}" ]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_SERVICE_START_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (PRIMARY) Checking user status..."
CLIENT_STATUS_USER_COMMAND="stasis-client-cli --json service status user"
CLIENT_STATUS_USER_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${CLIENT_STATUS_USER_COMMAND})
if [ "$(echo "${CLIENT_STATUS_USER_RESULT}" | jq '.active')" = "true" ]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_STATUS_USER_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (PRIMARY) Checking device status..."
CLIENT_STATUS_DEVICE_COMMAND="stasis-client-cli --json service status device"
CLIENT_STATUS_DEVICE_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${CLIENT_STATUS_DEVICE_COMMAND})
if [ "$(echo "${CLIENT_STATUS_DEVICE_RESULT}" | jq '.active')" = "true" ]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_STATUS_DEVICE_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (PRIMARY) Looking up backup definition..."
CLIENT_BACKUP_SHOW_DEFINITIONS_COMMAND="stasis-client-cli --json backup show definitions"
CLIENT_BACKUP_SHOW_DEFINITIONS_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${CLIENT_BACKUP_SHOW_DEFINITIONS_COMMAND})
CLIENT_BACKUP_DEFINITION=$(echo "${CLIENT_BACKUP_SHOW_DEFINITIONS_RESULT}" | jq -r '.[0] | .definition')
if [ "${CLIENT_BACKUP_DEFINITION}" != "null" ]
then
  echo "found [${CLIENT_BACKUP_DEFINITION}] (OK)"
else
  echo "failed; output was [${CLIENT_BACKUP_SHOW_DEFINITIONS_RESULT}]"
  exit 1
fi

function start_backup() {
  CONTAINER_ID=$1
  DEFINITION=$2
  BACKUP_NAME=$3

  [[ "${CONTAINER_ID}" = "${PRIMARY_CLIENT_CONTAINER_ID}" ]] && CONTAINER_TYPE="PRIMARY" || CONTAINER_TYPE="SECONDARY"

  echo -n "[$(now)] (${CONTAINER_TYPE}) Running backup [${BACKUP_NAME}]..."
  BACKUP_START_COMMAND="stasis-client-cli --json backup start ${DEFINITION} --follow"
  BACKUP_START_RESULT=$(docker exec "${CONTAINER_ID}" ${BACKUP_START_COMMAND})
  if [ $? = 0 ]
  then
    echo "OK"
  else
    echo "failed; output was [${BACKUP_START_RESULT}]"
    exit 1
  fi
}

start_backup "${PRIMARY_CLIENT_CONTAINER_ID}" "${CLIENT_BACKUP_DEFINITION}" "base"

echo -n "[$(now)] (PRIMARY) Creating [${CLIENT_BACKUP_DIRS_COUNT}] backup directories..."
for TEST_DIR in "${CLIENT_BACKUP_DIRS[@]}"
do
  MKDIR_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" mkdir -p ${TEST_DIR} 2>&1)
  if [ $? != 0 ]
  then
    echo "failed creating backup directory [${TEST_DIR}]: [${MKDIR_RESULT}]"
    exit 1
  fi
done
echo "OK"

echo -n "[$(now)] (PRIMARY) Creating [${CLIENT_TEST_FILES_COUNT}] test files..."
for i in "${!CLIENT_TEST_FILES[@]}"
do
  DD_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" dd if=/dev/urandom of=${CLIENT_TEST_FILES[$i]} bs=1M count=$((i + 1)) 2>&1)
  if [ $? != 0 ]
  then
    echo "failed creating test file [${CLIENT_TEST_FILES[$i]}]: [${DD_RESULT}]"
    exit 1
  fi

  SHA_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" sha256sum ${CLIENT_TEST_FILES[$i]} 2>&1)
  if [ $? != 0 ]
  then
    echo "failed creating checksum for file [${CLIENT_TEST_FILES[$i]}]: [${SHA_RESULT}]"
    exit 1
  fi

  SHA_RESULT="${SHA_RESULT%% *}"
  CLIENT_TEST_FILE_SHA_SUMS+=(["${CLIENT_TEST_FILES[$i]}"]="${SHA_RESULT}")
done
echo "OK"

start_backup "${PRIMARY_CLIENT_CONTAINER_ID}" "${CLIENT_BACKUP_DEFINITION}" "primary"

echo -n "[$(now)] (PRIMARY) Updating test files..."
CLIENT_UPDATED_TEST_FILE_DD_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" dd if=/dev/urandom of=${CLIENT_UPDATED_TEST_FILE} bs=1M count=10 2>&1)
if [ $? != 0 ]
then
  echo "failed updating test file [${CLIENT_UPDATED_TEST_FILE}]: [${CLIENT_UPDATED_TEST_FILE_DD_RESULT}]"
  exit 1
fi

CLIENT_UPDATED_TEST_FILE_SHA_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" sha256sum ${CLIENT_UPDATED_TEST_FILE} 2>&1)
if [ $? != 0 ]
then
  echo "failed creating updated checksum for file [${CLIENT_UPDATED_TEST_FILE}]: [${CLIENT_UPDATED_TEST_FILE_SHA_RESULT}]"
  exit 1
fi

CLIENT_UPDATED_TEST_FILE_SHA_RESULT="${CLIENT_UPDATED_TEST_FILE_SHA_RESULT%% *}"
if [ "${CLIENT_UPDATED_TEST_FILE_SHA_RESULT}" != "${CLIENT_TEST_FILE_SHA_SUMS[${CLIENT_UPDATED_TEST_FILE}]}" ]
then
  echo "OK"
else
  echo "failed; updated checksum [${CLIENT_UPDATED_TEST_FILE_SHA_RESULT}] is same as original [${CLIENT_TEST_FILE_SHA_SUMS[${CLIENT_UPDATED_TEST_FILE}]}]"
  exit 1
fi

start_backup "${PRIMARY_CLIENT_CONTAINER_ID}" "${CLIENT_BACKUP_DEFINITION}" "updated"

start_backup "${PRIMARY_CLIENT_CONTAINER_ID}" "${CLIENT_BACKUP_DEFINITION}" "empty"

echo -n "[$(now)] (PRIMARY) Looking up primary backup entry..."
CLIENT_BACKUP_ENTRY_PRIMARY_COMMAND="stasis-client-cli --json backup show entries -f crates==${CLIENT_TEST_FILES_COUNT} -o created --ordering DESC"
CLIENT_BACKUP_ENTRY_PRIMARY_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${CLIENT_BACKUP_ENTRY_PRIMARY_COMMAND})
CLIENT_BACKUP_ENTRY_PRIMARY=$(echo "${CLIENT_BACKUP_ENTRY_PRIMARY_RESULT}" | jq -r '.[0] | .entry')
if [ "${CLIENT_BACKUP_ENTRY_PRIMARY}" != "null" ] && [ "${CLIENT_BACKUP_ENTRY_PRIMARY}" != "" ]
then
  echo "found [${CLIENT_BACKUP_ENTRY_PRIMARY}] (OK)"
else
  echo "failed; output was [${CLIENT_BACKUP_ENTRY_PRIMARY_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (PRIMARY) Looking up updated backup entry..."
CLIENT_BACKUP_ENTRY_SECONDARY_COMMAND="stasis-client-cli --json backup show entries -f crates==1 -o created --ordering DESC"
CLIENT_BACKUP_ENTRY_SECONDARY_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${CLIENT_BACKUP_ENTRY_SECONDARY_COMMAND})
CLIENT_BACKUP_ENTRY_SECONDARY=$(echo "${CLIENT_BACKUP_ENTRY_SECONDARY_RESULT}" | jq -r '.[0] | .entry')
if [ "${CLIENT_BACKUP_ENTRY_SECONDARY}" != "null" ] && [ "${CLIENT_BACKUP_ENTRY_SECONDARY}" != "" ]
then
  echo "found [${CLIENT_BACKUP_ENTRY_SECONDARY}] (OK)"
else
  echo "failed; output was [${CLIENT_BACKUP_ENTRY_SECONDARY_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (PRIMARY) Looking up empty backup entry..."
CLIENT_BACKUP_ENTRY_EMPTY_COMMAND="stasis-client-cli --json backup show entries -f crates==0 -o created --ordering DESC"
CLIENT_BACKUP_ENTRY_EMPTY_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${CLIENT_BACKUP_ENTRY_EMPTY_COMMAND})
CLIENT_BACKUP_ENTRY_EMPTY=$(echo "${CLIENT_BACKUP_ENTRY_EMPTY_RESULT}" | jq -r '.[0] | .entry')
if [ "${CLIENT_BACKUP_ENTRY_EMPTY}" != "null" ] && [ "${CLIENT_BACKUP_ENTRY_EMPTY}" != "" ]
then
  echo "found [${CLIENT_BACKUP_ENTRY_EMPTY}] (OK)"
else
  echo "failed; output was [${CLIENT_BACKUP_ENTRY_EMPTY_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (PRIMARY) Checking metadata of [${CLIENT_TEST_FILES_COUNT}] test files..."
for i in "${!CLIENT_TEST_FILES[@]}"
do
  BACKUP_FILE_METADATA_COMMAND="stasis-client-cli --json backup show metadata ${CLIENT_BACKUP_ENTRY_PRIMARY} -f entity==${CLIENT_TEST_FILES[$i]}"
  BACKUP_FILE_METADATA_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${BACKUP_FILE_METADATA_COMMAND})

  BACKUP_FILE_METADATA_ACTUAL_ENTITY=$(echo "${BACKUP_FILE_METADATA_RESULT}" | jq -r '.[0] | .entity')
  BACKUP_FILE_METADATA_EXPECTED_ENTITY="${CLIENT_TEST_FILES[$i]}"
  if [ "${BACKUP_FILE_METADATA_ACTUAL_ENTITY}" != "${BACKUP_FILE_METADATA_EXPECTED_ENTITY}" ]
  then
    echo "failed; expected entity [${BACKUP_FILE_METADATA_EXPECTED_ENTITY}] but found [${BACKUP_FILE_METADATA_ACTUAL_ENTITY}]; output was [${BACKUP_FILE_METADATA_RESULT}]"
    exit 1
  fi

  BACKUP_FILE_METADATA_ACTUAL_SIZE=$(echo "${BACKUP_FILE_METADATA_RESULT}" | jq -r '.[0] | .size')
  BACKUP_FILE_METADATA_EXPECTED_SIZE="$((i + 1)) MB"
  if [ "${BACKUP_FILE_METADATA_ACTUAL_SIZE}" != "${BACKUP_FILE_METADATA_EXPECTED_SIZE}" ]
  then
    echo "failed; expected size [${BACKUP_FILE_METADATA_EXPECTED_SIZE}] but found [${BACKUP_FILE_METADATA_ACTUAL_SIZE}]; output was [${BACKUP_FILE_METADATA_RESULT}]"
    exit 1
  fi

  BACKUP_FILE_METADATA_ACTUAL_TYPE=$(echo "${BACKUP_FILE_METADATA_RESULT}" | jq -r '.[0] | .type')
  BACKUP_FILE_METADATA_EXPECTED_TYPE="file"
  if [ "${BACKUP_FILE_METADATA_ACTUAL_TYPE}" != "${BACKUP_FILE_METADATA_EXPECTED_TYPE}" ]
  then
    echo "failed; expected type [${BACKUP_FILE_METADATA_EXPECTED_TYPE}] but found [${BACKUP_FILE_METADATA_ACTUAL_TYPE}]; output was [${BACKUP_FILE_METADATA_RESULT}]"
    exit 1
  fi
done
echo "OK"

echo -n "[$(now)] (PRIMARY) Checking metadata of updated test file..."
CLIENT_UPDATED_BACKUP_FILE_METADATA_COMMAND="stasis-client-cli --json backup show metadata ${CLIENT_BACKUP_ENTRY_SECONDARY} -f entity==${CLIENT_UPDATED_TEST_FILE}"
CLIENT_UPDATED_BACKUP_FILE_METADATA_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${CLIENT_UPDATED_BACKUP_FILE_METADATA_COMMAND})

CLIENT_UPDATED_BACKUP_FILE_METADATA_ACTUAL_SIZE=$(echo "${CLIENT_UPDATED_BACKUP_FILE_METADATA_RESULT}" | jq -r '.[0] | .size')
CLIENT_UPDATED_BACKUP_FILE_METADATA_EXPECTED_SIZE="10 MB"
if [ "${CLIENT_UPDATED_BACKUP_FILE_METADATA_ACTUAL_SIZE}" != "${CLIENT_UPDATED_BACKUP_FILE_METADATA_EXPECTED_SIZE}" ]
then
  echo "failed; expected size [${CLIENT_UPDATED_BACKUP_FILE_METADATA_EXPECTED_SIZE}] but found [${CLIENT_UPDATED_BACKUP_FILE_METADATA_ACTUAL_SIZE}]; output was [${CLIENT_UPDATED_BACKUP_FILE_METADATA_RESULT}]"
  exit 1
fi
echo "OK"

echo -n "[$(now)] (PRIMARY) Searching for file [.*/d]..."
CLIENT_BACKUP_SEARCH_COMMAND="stasis-client-cli --json backup search .*/d"
CLIENT_BACKUP_SEARCH_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${CLIENT_BACKUP_SEARCH_COMMAND})

CLIENT_BACKUP_SEARCH_ACTUAL_ENTRY=$(echo "${CLIENT_BACKUP_SEARCH_RESULT}" | jq -r '.[0] | .entry')
CLIENT_BACKUP_SEARCH_ACTUAL_STATE=$(echo "${CLIENT_BACKUP_SEARCH_RESULT}" | jq -r '.[0] | .state')
CLIENT_BACKUP_SEARCH_ACTUAL_ENTITY=$(echo "${CLIENT_BACKUP_SEARCH_RESULT}" | jq -r '.[0] | .entity')
CLIENT_BACKUP_SEARCH_EXPECTED_ENTRY="${CLIENT_BACKUP_ENTRY_SECONDARY}"
CLIENT_BACKUP_SEARCH_EXPECTED_STATE="existing"
CLIENT_BACKUP_SEARCH_EXPECTED_ENTITY="${CLIENT_CONTAINER_HOME}/backup/0/d"

if [ "${CLIENT_BACKUP_SEARCH_ACTUAL_ENTRY}" != "${CLIENT_BACKUP_SEARCH_EXPECTED_ENTRY}" ]
then
  echo "failed; expected entry [${CLIENT_BACKUP_SEARCH_EXPECTED_ENTRY}] but found [${CLIENT_BACKUP_SEARCH_ACTUAL_ENTRY}]; output was [${CLIENT_BACKUP_SEARCH_RESULT}]"
  exit 1
fi

if [ "${CLIENT_BACKUP_SEARCH_ACTUAL_STATE}" != "${CLIENT_BACKUP_SEARCH_EXPECTED_STATE}" ]
then
  echo "failed; expected state [${CLIENT_BACKUP_SEARCH_EXPECTED_STATE}] but found [${CLIENT_BACKUP_SEARCH_ACTUAL_STATE}]; output was [${CLIENT_BACKUP_SEARCH_RESULT}]"
  exit 1
fi

if [ "${CLIENT_BACKUP_SEARCH_ACTUAL_ENTITY}" != "${CLIENT_BACKUP_SEARCH_EXPECTED_ENTITY}" ]
then
  echo "failed; expected entry [${CLIENT_BACKUP_SEARCH_EXPECTED_ENTITY}] but found [${CLIENT_BACKUP_SEARCH_ACTUAL_ENTITY}]; output was [${CLIENT_BACKUP_SEARCH_RESULT}]"
  exit 1
fi
echo "OK"

echo -n "[$(now)] (PRIMARY) Removing recovery directory..."
CLIENT_RECOVERY_DIR_RM_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" rm -rf ${CLIENT_RECOVERY_DIR} 2>&1)
if [ $? = 0 ]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_RECOVERY_DIR_RM_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (PRIMARY) Creating recovery directory..."
CLIENT_RECOVERY_DIR_MKDIR_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" mkdir -p ${CLIENT_RECOVERY_DIR} 2>&1)
if [ $? = 0 ]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_RECOVERY_DIR_MKDIR_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (PRIMARY) Recovering from entry [${CLIENT_BACKUP_ENTRY_PRIMARY}]..."
CLIENT_RECOVER_FROM_ENTRY_COMMAND="stasis-client-cli --json recover from ${CLIENT_BACKUP_DEFINITION} ${CLIENT_BACKUP_ENTRY_PRIMARY} --follow --destination ${CLIENT_RECOVERY_DIR}"
CLIENT_RECOVER_FROM_ENTRY_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${CLIENT_RECOVER_FROM_ENTRY_COMMAND})

if [ $? = 0 ]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_RECOVER_FROM_ENTRY_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (PRIMARY) Checking [${CLIENT_TEST_FILES_COUNT}] recovered test files..."
for i in "${!CLIENT_TEST_FILES[@]}"
do
  ORIGINAL_FILE="${CLIENT_TEST_FILES[$i]}"
  RECOVERED_FILE="${CLIENT_RECOVERY_DIR}${ORIGINAL_FILE}"

  ORIGINAL_SHA_RESULT="${CLIENT_TEST_FILE_SHA_SUMS["${ORIGINAL_FILE}"]}"
  RECOVERED_SHA_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" sha256sum ${RECOVERED_FILE} 2>&1)
  if [ $? != 0 ]
  then
    echo "failed creating checksum for recovered file [${RECOVERED_FILE}]: [${RECOVERED_SHA_RESULT}]"
    exit 1
  fi

  RECOVERED_SHA_RESULT="${RECOVERED_SHA_RESULT%% *}"
  if [ "${RECOVERED_SHA_RESULT}" != "${ORIGINAL_SHA_RESULT}" ]
  then
    echo "failed; recovered checksum [${RECOVERED_SHA_RESULT} / ${RECOVERED_FILE}] is not same as original [${ORIGINAL_SHA_RESULT} / ${ORIGINAL_FILE}]"
    exit 1
  fi
done
echo "OK"

echo -n "[$(now)] (PRIMARY) Stopping client service..."
CLIENT_SERVICE_STOP_COMMAND="stasis-client-cli --json service stop --confirm"
CLIENT_SERVICE_STOP_RESULT=$(docker exec "${PRIMARY_CLIENT_CONTAINER_ID}" ${CLIENT_SERVICE_STOP_COMMAND})
CLIENT_SERVICE_STOP_SUCCESSFUL="$(echo "${CLIENT_SERVICE_STOP_RESULT}" | jq '.successful')"
if [ "${CLIENT_SERVICE_STOP_SUCCESSFUL}" = "true" ]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_SERVICE_STOP_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (SECONDARY) Ensuring client service is stopped..."
CLIENT_SERVICE_STOP_COMMAND="stasis-client-cli --json service stop --confirm"
CLIENT_SERVICE_STOP_RESULT=$(docker exec "${SECONDARY_CLIENT_CONTAINER_ID}" ${CLIENT_SERVICE_STOP_COMMAND} 2>&1)
CLIENT_SERVICE_STOP_SUCCESSFUL="$(echo "${CLIENT_SERVICE_STOP_RESULT}" | jq '.successful' 2>&1)"
CLIENT_SERVICE_STOP_FAILURE="$(echo "${CLIENT_SERVICE_STOP_RESULT}" | jq -r '.failure' 2>&1)"
CLIENT_SERVICE_NOT_ACTIVE="Background service is not active"
if [ "${CLIENT_SERVICE_STOP_SUCCESSFUL}" = "true" ] || [ "${CLIENT_SERVICE_STOP_FAILURE}" = "${CLIENT_SERVICE_NOT_ACTIVE}" ] || [[ "${CLIENT_SERVICE_STOP_RESULT}" == *"client not configured"* ]]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_SERVICE_STOP_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (SECONDARY) Ensuring client config is not available..."
for CONFIG_FILE in "${CLIENT_CONFIG_FILES[@]}"
do
  RM_RESULT=$(docker exec "${SECONDARY_CLIENT_CONTAINER_ID}" rm -rf ${CONFIG_FILE} 2>&1)
  if [ $? != 0 ]
  then
    echo "failed removing config file [${CONFIG_FILE}]: [${RM_RESULT}]"
    exit 1
  fi
done
echo "OK"

echo -n "[$(now)] (SECONDARY) Checking user status..."
CLIENT_STATUS_USER_COMMAND="stasis-client-cli --json service status user"
CLIENT_STATUS_USER_RESULT=$(docker exec "${SECONDARY_CLIENT_CONTAINER_ID}" ${CLIENT_STATUS_USER_COMMAND} 2>&1)
if [[ "${CLIENT_STATUS_USER_RESULT}" == *"client not configured"* ]]
then
  echo "not configured (OK)"
else
  echo "failed; output was [${CLIENT_STATUS_USER_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (SECONDARY) Retrieving device bootstrap code..."
DEVICE_BOOTSTRAP_CODE_RESULT=$(curl -sk -H "${HEADER_JSON}" -H "Authorization: Bearer ${USER_TOKEN}" -X PUT "${SERVER_BOOTSTRAP_URL}/v1/devices/codes/own/for-device/${SECONDARY_DEVICE_ID}")
DEVICE_BOOTSTRAP_CODE=$(jq -r .value <<< "${DEVICE_BOOTSTRAP_CODE_RESULT}")

if [ "${DEVICE_BOOTSTRAP_CODE}" != "" ]
then
  echo "received [${DEVICE_BOOTSTRAP_CODE}] (OK)"
else
  echo "failed: [${DEVICE_BOOTSTRAP_CODE_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (SECONDARY) Executing device bootstrap..."
DEVICE_BOOTSTRAP_COMMAND="stasis-client bootstrap --server ${SERVER_BOOTSTRAP_URL_INTERNAL} --code ${DEVICE_BOOTSTRAP_CODE} --accept-self-signed --user-name ${USER_ID} --user-password ${USER_PASSWORD}"
DEVICE_BOOTSTRAP_RESULT=$(docker exec "${SECONDARY_CLIENT_CONTAINER_ID}" ${DEVICE_BOOTSTRAP_COMMAND} 2>&1)
if [ $? = 0 ]
then
  echo "OK"
else
  echo "failed; output was [${DEVICE_BOOTSTRAP_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (SECONDARY) Starting client service..."
CLIENT_SERVICE_START_COMMAND="stasis-client-cli --json service start --username ${USER_ID} --password ${USER_PASSWORD}"
CLIENT_SERVICE_START_RESULT=$(docker exec "${SECONDARY_CLIENT_CONTAINER_ID}" ${CLIENT_SERVICE_START_COMMAND})
CLIENT_SERVICE_START_SUCCESSFUL="$(echo "${CLIENT_SERVICE_START_RESULT}" | jq '.successful')"
CLIENT_SERVICE_START_FAILURE="$(echo "${CLIENT_SERVICE_START_RESULT}" | jq -r '.failure')"
CLIENT_SERVICE_ALREADY_ACTIVE="Background service is already active"
if [ "${CLIENT_SERVICE_START_SUCCESSFUL}" = "true" ] || [ "${CLIENT_SERVICE_START_FAILURE}" = "${CLIENT_SERVICE_ALREADY_ACTIVE}" ]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_SERVICE_START_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (SECONDARY) Checking user status..."
CLIENT_STATUS_USER_COMMAND="stasis-client-cli --json service status user"
CLIENT_STATUS_USER_RESULT=$(docker exec "${SECONDARY_CLIENT_CONTAINER_ID}" ${CLIENT_STATUS_USER_COMMAND})
if [ "$(echo "${CLIENT_STATUS_USER_RESULT}" | jq '.active')" = "true" ]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_STATUS_USER_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (SECONDARY) Checking device status..."
CLIENT_STATUS_DEVICE_COMMAND="stasis-client-cli --json service status device"
CLIENT_STATUS_DEVICE_RESULT=$(docker exec "${SECONDARY_CLIENT_CONTAINER_ID}" ${CLIENT_STATUS_DEVICE_COMMAND})
if [ "$(echo "${CLIENT_STATUS_DEVICE_RESULT}" | jq '.active')" = "true" ]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_STATUS_DEVICE_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (SECONDARY) Stopping client service..."
CLIENT_SERVICE_STOP_COMMAND="stasis-client-cli --json service stop --confirm"
CLIENT_SERVICE_STOP_RESULT=$(docker exec "${SECONDARY_CLIENT_CONTAINER_ID}" ${CLIENT_SERVICE_STOP_COMMAND})
CLIENT_SERVICE_STOP_SUCCESSFUL="$(echo "${CLIENT_SERVICE_STOP_RESULT}" | jq '.successful')"
if [ "${CLIENT_SERVICE_STOP_SUCCESSFUL}" = "true" ]
then
  echo "OK"
else
  echo "failed; output was [${CLIENT_SERVICE_STOP_RESULT}]"
  exit 1
fi

echo -n "[$(now)] (SECONDARY) Pushing device key..."
DEVICE_KEY_PUSH_COMMAND="stasis-client maintenance --secret push --user-name ${USER_ID} --user-password ${USER_PASSWORD}"
DEVICE_KEY_PUSH_RESULT=$(docker exec "${SECONDARY_CLIENT_CONTAINER_ID}" ${DEVICE_KEY_PUSH_COMMAND} 2>&1)
if [ $? = 0 ]
then
  echo "OK"
else
  echo "failed; output was [${DEVICE_KEY_PUSH_RESULT}]"
  exit 1
fi

echo -n "[$(now)] Retrieving device keys..."
EXPECTED_FINAL_DEVICE_KEYS_COUNT=1
ACTUAL_FINAL_DEVICE_KEYS_COUNT=$(curl -sk -H "Authorization: Bearer ${USER_TOKEN}" -X GET "${SERVER_API_URL}/v1/devices/keys" | jq ". | length")
if [ "${ACTUAL_FINAL_DEVICE_KEYS_COUNT}" = "${EXPECTED_FINAL_DEVICE_KEYS_COUNT}" ]
then
  echo "found [${ACTUAL_FINAL_DEVICE_KEYS_COUNT}] (OK)"
else
  echo "failed; expected [${EXPECTED_FINAL_DEVICE_KEYS_COUNT}] but found [${ACTUAL_FINAL_DEVICE_KEYS_COUNT}]"
fi

TEST_END="$(date +%s)"
TEST_DURATION=$((TEST_END-TEST_START))

echo "Completed in [${TEST_DURATION}] seconds: [$(now)]"
