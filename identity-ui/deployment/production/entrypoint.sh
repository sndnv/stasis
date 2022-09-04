#!/usr/bin/env sh

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

require "${IDENTITY_UI_IDENTITY_SERVER}" "<identity server> is required"
require "${IDENTITY_UI_TOKEN_ENDPOINT}" "<token endpoint> is required"
require "${IDENTITY_UI_CLIENT_ID}" "<client ID> is required"
require "${IDENTITY_UI_REDIRECT_URI}" "<redirect URI> is required"
require "${IDENTITY_UI_SCOPES}" "<scopes> is required"

require "${NGINX_SERVER_NAME}" "<nginx server name> is required"
require "${NGINX_SERVER_PORT}" "<nginx server port> is required"
require "${NGINX_CORS_ALLOWED_ORIGIN}" "<nginx CORS allowed origin> is required"

envsubst \$IDENTITY_UI_IDENTITY_SERVER,\$IDENTITY_UI_TOKEN_ENDPOINT,\$IDENTITY_UI_CLIENT_ID,\$IDENTITY_UI_REDIRECT_URI,\$IDENTITY_UI_SCOPES < /opt/stasis-identity-ui/templates/.env.template > /usr/share/nginx/html/assets/.env
envsubst \$NGINX_SERVER_NAME,\$NGINX_SERVER_PORT,\$NGINX_CORS_ALLOWED_ORIGIN < /opt/stasis-identity-ui/templates/nginx.template > /etc/nginx/nginx.conf

nginx -g 'daemon off;'
