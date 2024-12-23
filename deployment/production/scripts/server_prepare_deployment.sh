#!/usr/bin/env bash

DOWNLOAD_DIR_BASE="/tmp/stasis-download-"
REPO="sndnv/stasis"

HELP="Downloads and prepares all configuration files needed for a stasis server deployment\n\tDownloads are stored under [${DOWNLOAD_DIR_BASE}<version>/]\n\tSource Github repo is [${REPO}]"
USAGE="Usage: $0 [-v|--verbose] [-h|--help] [-s|--skip-download] [-o|--overwrite-existing] [--version=<version>] [--target=<path to target deployment configuration>]"

function now() {
  timestamp="$(date +"%Y-%m-%dT%H:%M:%SZ")"
  echo "${timestamp}"
}

function log_info() {
    echo "[$(now)] [ INFO] $1"
}

function log_warn() {
    echo "[$(now)] [ WARN] $1"
}

function log_error() {
    echo "[$(now)] [ERROR] $1"
}

function log_debug() {
    if [[ "${VERBOSE_FLAG}" == "YES" ]]; then
      echo "[$(now)] [DEBUG] $1"
    fi
}

function failed() {
  echo "[$(now)] ... failed."
  exit 1
}

function file_name_from_path() {
  local FILE="${1##*/}"
  echo "${FILE%.*}"
}

function generate_uuid() {
    echo $(uuidgen | tr '[:upper:]' '[:lower:]')
}

function generate_random() {
    echo $(LC_ALL=C tr -dc a-zA-Z0-9 </dev/urandom | head -c $1)
}

for i in "$@"; do
  case $i in
    -h|--help)
      HELP_FLAG=YES
      shift
      ;;
    -v|--verbose)
      VERBOSE_FLAG=YES
      shift
      ;;
    -s|--skip-download)
      SKIP_DOWNLOAD_FLAG=YES
      shift
      ;;
    -o|--overwrite-existing)
      OVERWRITE_EXISTING_FLAG=YES
      shift
      ;;
    --target=*)
      TARGET_OUTPUT="${i#*=}"
      shift
      ;;
    --version=*)
      VERSION="${i#*=}"
      shift
      ;;
    *)
      log_error "Unknown argument $i"
      exit 1
      ;;
  esac
done

if [ "$HELP_FLAG" == "YES" ]; then
  echo -e "${HELP}"
  echo -e "${USAGE}"
  exit 0
fi

if ((BASH_VERSINFO[0] < 4))
then
  echo "Error: Bash version 4 or above is required but [${BASH_VERSINFO[0]}.${BASH_VERSINFO[1]}] found"
  exit 1
fi

if ! command -v jq &> /dev/null; then
  log_error "[jq] is required for the installation process; see [https://jqlang.github.io/jq/download/] for installation instructions"
  exit 1
fi

if [[ -z "${TARGET_OUTPUT}" ]]; then
    log_error "A target directory for the deployment configuration is required but none was provided"
    echo -e "${USAGE}"
    exit 1
fi

if [ "$SKIP_DOWNLOAD_FLAG" == "YES" ]; then
  log_info "Skipping assets download"

  if [[ -z "${VERSION}" ]]; then
    DOWNLOAD_DIRS=$(ls -d1 ${DOWNLOAD_DIR_BASE}*)
  else
    DOWNLOAD_DIRS=$(ls -d1 ${DOWNLOAD_DIR_BASE}${VERSION})
  fi

  IFS=$'\n' read -rd '' -a DOWNLOADED_VERSIONS <<< "$DOWNLOAD_DIRS"

  if [ ${#DOWNLOADED_VERSIONS[@]} -eq 0 ]; then
    log_error "No matching local assets found"
    exit 1
  elif [ ${#DOWNLOADED_VERSIONS[@]} -eq 1 ]; then
    ACTUAL_VERSION=${DOWNLOADED_VERSIONS[0]#*${DOWNLOAD_DIR_BASE}}

    STASIS_DEPLOYMENT_FILE="${DOWNLOADED_VERSIONS[0]}/stasis-deployment-${ACTUAL_VERSION}.tar.gz"

    if [ ! -f ${STASIS_DEPLOYMENT_FILE} ]; then
        log_error "Client file missing - [${STASIS_DEPLOYMENT_FILE}]"
        FILE_MISSING=YES
    else
      log_debug "Using [${STASIS_DEPLOYMENT_FILE}] to prepare deployment"
    fi

    if [ "$FILE_MISSING" == "YES" ]; then
      exit 1
    else
      DOWNLOAD_DIR=${DOWNLOADED_VERSIONS[0]}
    fi
  else
    log_error "Found too many matching local assets (${#DOWNLOADED_VERSIONS[@]}): [${DOWNLOAD_DIRS//$'\n'/, }]"
    exit 1
  fi
else
  RELEASES_API="https://api.github.com/repos/${REPO}/releases"

  function get_latest_version() {
      echo $(curl -s "${RELEASES_API}" | jq -r ".[0] | .tag_name, .id")
  }

  function get_requested_version() {
      echo $(curl -s "${RELEASES_API}" | jq -r ".[] | select(.tag_name == \"${VERSION}\") | .tag_name, .id")
  }

  function get_asset() {
    ASSET=$1
    RELEASE_VERSION=$2
    OUTPUT_DIR=$3

    URL="https://github.com/${REPO}/releases/download/${RELEASE_VERSION}/${ASSET}"

    log_debug "Downloading [${URL}] to [${OUTPUT_DIR}]"
    $(curl -sOL --output-dir ${OUTPUT_DIR} ${URL})
  }

  log_debug "Loading version information from [${RELEASES_API}]"

  if [[ -z "${VERSION}" ]]; then
    REPORTED_VERSION=$(get_latest_version)
    ACTUAL_VERSION=${REPORTED_VERSION% *}
    ACTUAL_RELEASE_ID=${REPORTED_VERSION#* }
    log_debug "Found release with ID [${ACTUAL_RELEASE_ID}] for version [${ACTUAL_VERSION}]"
    log_info "Starting deployment preparation for version: [${ACTUAL_VERSION} (latest)]"
  else
    REQUESTED_VERSION=$(get_requested_version)
    if [[ -z "${REQUESTED_VERSION}" ]]; then
      log_error "Version [${VERSION}] was not found"
      exit 1
    else
      ACTUAL_VERSION=${REQUESTED_VERSION% *}
      ACTUAL_RELEASE_ID=${REQUESTED_VERSION#* }
      log_debug "Found release with ID [${ACTUAL_RELEASE_ID}] for version [${ACTUAL_VERSION}]"
      log_info "Starting deployment preparation for version: [${ACTUAL_VERSION}]"
    fi
  fi

  DOWNLOAD_DIR="${DOWNLOAD_DIR_BASE}${ACTUAL_VERSION}"
  mkdir -p ${DOWNLOAD_DIR} || failed

  get_asset "stasis-deployment-${ACTUAL_VERSION}.tar.gz" ${ACTUAL_VERSION} ${DOWNLOAD_DIR}
fi

DEPLOYMENT_ARCHIVE="${DOWNLOAD_DIR}/stasis-deployment-${ACTUAL_VERSION}.tar.gz"

if [[ -d "${TARGET_OUTPUT}" ]]
then
    log_debug "Found existing deployment configuration directory [${TARGET_OUTPUT}]"
    if [ "$(ls -A ${TARGET_OUTPUT})" -a "$OVERWRITE_EXISTING_FLAG" != "YES" ]
    then
      log_error "Target directory [${TARGET_OUTPUT}] is not empty; set '--overwrite-existing' flag to overwrite"
      exit 1
    fi
else
    log_debug "Creating deployment configuration directory [${TARGET_OUTPUT}]"
    mkdir -p "${TARGET_OUTPUT}"
fi

log_info "Extracting deployment configuration [${DEPLOYMENT_ARCHIVE}] to [${TARGET_OUTPUT}]..."

if [[ "${VERBOSE_FLAG}" == "YES" ]]
then
  tar -xvzf ${DEPLOYMENT_ARCHIVE} --exclude='.gitignore' -C ${TARGET_OUTPUT}
else
  tar -xzf ${DEPLOYMENT_ARCHIVE} --exclude='.gitignore' -C ${TARGET_OUTPUT}
fi

TEMPLATES_DIR="${TARGET_OUTPUT}/secrets/templates"
SECRETS_DIR="${TARGET_OUTPUT}/secrets"
STORAGE_DIR="${TARGET_OUTPUT}/local"

TEMPLATE_FILES=()
TEMPLATE_FILES+=("db-identity.env.template")
TEMPLATE_FILES+=("db-identity-exporter.env.template")
TEMPLATE_FILES+=("db-server.env.template")
TEMPLATE_FILES+=("db-server-exporter.env.template")
TEMPLATE_FILES+=("identity.bootstrap.env.template")
TEMPLATE_FILES+=("identity.env.template")
TEMPLATE_FILES+=("identity-ui.env.template")
TEMPLATE_FILES+=("server.bootstrap.env.template")
TEMPLATE_FILES+=("server.env.template")
TEMPLATE_FILES+=("server-ui.env.template")

declare -A GENERATED_VALUES

# UUIDs
GENERATED_VALUES+=(["DEFAULT_USER_ID"]="$(generate_uuid)")
GENERATED_VALUES+=(["IDENTITY_UI_CLIENT_ID"]="$(generate_uuid)")
GENERATED_VALUES+=(["NODES_LOCAL_PRIMARY_ID"]="$(generate_uuid)")
GENERATED_VALUES+=(["NODES_LOCAL_SECONDARY_ID"]="$(generate_uuid)")
GENERATED_VALUES+=(["SCHEDULES_DAILY_ID"]="$(generate_uuid)")
GENERATED_VALUES+=(["SCHEDULES_HALF_DAILY_ID"]="$(generate_uuid)")
GENERATED_VALUES+=(["SERVER_INSTANCE_CLIENT_ID"]="$(generate_uuid)")
GENERATED_VALUES+=(["SERVER_NODE_CLIENT_ID"]="$(generate_uuid)")
GENERATED_VALUES+=(["SERVER_UI_CLIENT_ID"]="$(generate_uuid)")

# Secrets
GENERATED_VALUES+=(["DEFAULT_USER_PASSWORD"]="$(generate_random 24)")
GENERATED_VALUES+=(["DEFAULT_USER_SALT"]="$(generate_random 16)")
GENERATED_VALUES+=(["DERIVATION_SALT_PREFIX"]="$(generate_random 16)")
GENERATED_VALUES+=(["ENCRYPTION_SALT_PREFIX"]="$(generate_random 16)")
GENERATED_VALUES+=(["IDENTITY_DB_PASSWORD"]="$(generate_random 24)")
GENERATED_VALUES+=(["IDENTITY_UI_CLIENT_SECRET"]="$(generate_random 24)")
GENERATED_VALUES+=(["SERVER_DB_PASSWORD"]="$(generate_random 24)")
GENERATED_VALUES+=(["SERVER_INSTANCE_CLIENT_SECRET"]="$(generate_random 24)")
GENERATED_VALUES+=(["SERVER_NODE_CLIENT_SECRET"]="$(generate_random 24)")
GENERATED_VALUES+=(["SERVER_MANAGEMENT_USER_PASSWORD"]="$(generate_random 24)")
GENERATED_VALUES+=(["SERVER_UI_CLIENT_SECRET"]="$(generate_random 24)")

# Other
GENERATED_VALUES+=(["DEFAULT_USER"]="default-user")
GENERATED_VALUES+=(["IDENTITY_DB_USER"]="$(generate_random 8)")
GENERATED_VALUES+=(["NODES_LOCAL_PRIMARY_PARENT_DIRECTORY"]="${STORAGE_DIR}/server/primary")
GENERATED_VALUES+=(["NODES_LOCAL_SECONDARY_PARENT_DIRECTORY"]="${STORAGE_DIR}/server/secondary")
GENERATED_VALUES+=(["SERVER_DB_USER"]="$(generate_random 8)")
GENERATED_VALUES+=(["SERVER_MANAGEMENT_USER"]="$(generate_random 16)")
GENERATED_VALUES+=(["DEFAULT_ROUTER_ID"]="$(generate_uuid)")

log_info "Generating configuration..."
REPLACEMENT_VAR_PREFIX='$${'
REPLACEMENT_VAR_SUFFIX='}'
REPLACEMENT_VALUES=""
for env_var in "${!GENERATED_VALUES[@]}"
do
  log_debug "    ${env_var}: ${GENERATED_VALUES[$env_var]}"
  REPLACEMENT_VALUES="${REPLACEMENT_VALUES}s,${REPLACEMENT_VAR_PREFIX}${env_var}${REPLACEMENT_VAR_SUFFIX},${GENERATED_VALUES[$env_var]},g;"
done

log_info "Expanding templates..."
for template_file in "${!TEMPLATE_FILES[@]}"
do
  source_file="${TEMPLATES_DIR}/${TEMPLATE_FILES[template_file]}"
  target_file="${SECRETS_DIR}/${TEMPLATE_FILES[template_file]%".template"}"
  log_debug "    Expanding [${source_file}] to [${target_file}]"
  $(sed -e "${REPLACEMENT_VALUES}" "${source_file}" > "${target_file}")

  if [ $? != 0 ]
  then
    log_error "Failed to expand template file [${source_file}]"
    exit 1
  fi
done

SIGNATURE_KEY_PATH="${SECRETS_DIR}/identity-signature-key.jwk.json"
log_debug "Creating signature key placeholder [${SIGNATURE_KEY_PATH}]..."
touch "${SIGNATURE_KEY_PATH}"

log_info "... done."

log_info ""

log_info "Manual actions required:"
log_info "    - Provide TLS certificates and signature keys:"
log_info "        Keystore - identity: ${SECRETS_DIR}/identity.p12"
log_info "        Keystore - server:   ${SECRETS_DIR}/server.p12"
log_info "        TLS cert - identity: ${SECRETS_DIR}/identity.cert.pem"
log_info "        TLS key - identity:  ${SECRETS_DIR}/identity.key.pem"
log_info "        TLS cert - server:   ${SECRETS_DIR}/server.cert.pem"
log_info "        TLS key - server:    ${SECRETS_DIR}/server.key.pem"
log_info "    - Create local storage directories:"
log_info "        PostgreSQL DB - identity: ${STORAGE_DIR}/postgres/identity"
log_info "        PostgreSQL DB - server:   ${STORAGE_DIR}/postgres/server"
log_info "        Data storage - primary:   ${GENERATED_VALUES['NODES_LOCAL_PRIMARY_PARENT_DIRECTORY']}"
log_info "        Data storage - secondary: ${GENERATED_VALUES['NODES_LOCAL_SECONDARY_PARENT_DIRECTORY']}"
log_info "    - Update compose file with correct/desired image versions"
log_info "    - Bootstrap services (for more information, consult the provided README file)"

log_info ""

log_info "After all services are up and running, the default user credentials can be used to login:"
log_info "    Username: ${GENERATED_VALUES['DEFAULT_USER']}"
log_info "    Password: ${GENERATED_VALUES['DEFAULT_USER_PASSWORD']}"
log_info "    Salt:     ${GENERATED_VALUES['DEFAULT_USER_SALT']}"
