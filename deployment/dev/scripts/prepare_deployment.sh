#!/usr/bin/env bash

HELP="Generate artifacts and certificates for deployed services (dev)"
USAGE="Usage: $0 <country> <location> <organization> [<identity-hostname> <server-hostname> <client-hostname>]"

if [ "$1" = "-h" ] || [ "$1" = "--help" ]
then
  echo "${HELP}"
  echo "${USAGE}"
  exit
fi

COUNTRY=$1
LOCATION=$2
ORGANIZATION=$3

if [ "${COUNTRY}" = "" ]
then
  echo "<country> is required"
  echo "${USAGE}"
  exit
fi

if [ "${LOCATION}" = "" ]
then
  echo "<location> is required"
  echo "${USAGE}"
  exit
fi

if [ "${ORGANIZATION}" = "" ]
then
  echo "<organization> is required"
  echo "${USAGE}"
  exit
fi

SCRIPTS_DIR=$(unset CDPATH && cd "$(dirname "$0")" && echo "${PWD}")
CERTS_DIR="$(dirname "${SCRIPTS_DIR}")/secrets"

IDENTITY_NAME=${4:-identity}
SERVER_NAME=${5:-server}
CLIENT_NAME=${6:-localhost}
PRIMARY_USER_ID=${7:-b21c3f0c-0133-4fb5-883d-95ad3aaa1740}
PRIMARY_USER_SALT=${8:-d92beb8f7c3b}
PRIMARY_USER_PASSWORD=${9:-passw0rd}
EXTRA_NAME="10.0.2.2"

"${SCRIPTS_DIR}/generate_self_signed_cert.py" -c "${COUNTRY}" -l "${LOCATION}" -o "${ORGANIZATION}" -p "${CERTS_DIR}" -e "${EXTRA_NAME}" "${IDENTITY_NAME}"
"${SCRIPTS_DIR}/generate_self_signed_cert.py" -c "${COUNTRY}" -l "${LOCATION}" -o "${ORGANIZATION}" -p "${CERTS_DIR}" -e "${EXTRA_NAME}" "${SERVER_NAME}"
"${SCRIPTS_DIR}/generate_self_signed_cert.py" -c "${COUNTRY}" -l "${LOCATION}" -o "${ORGANIZATION}" -p "${CERTS_DIR}" "${CLIENT_NAME}"

"${SCRIPTS_DIR}/generate_device_secret.py" --user-id "${PRIMARY_USER_ID}" --user-salt "${PRIMARY_USER_SALT}" --user-password "${PRIMARY_USER_PASSWORD}" --output-path "${CERTS_DIR}/client.secret"

chmod 644 ${CERTS_DIR}/${IDENTITY_NAME}.*
chmod 644 ${CERTS_DIR}/${SERVER_NAME}.*
chmod 644 ${CERTS_DIR}/${CLIENT_NAME}.*

"${SCRIPTS_DIR}/generate_artifacts.py"
