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

require "${SERVER_UI_SERVER_API}" "<server API> is required"
require "${SERVER_UI_BOOTSTRAP_API}" "<bootstrap API> is required"
require "${SERVER_UI_AUTHORIZATION_ENDPOINT}" "<authorization endpoint> is required"
require "${SERVER_UI_TOKEN_ENDPOINT}" "<token endpoint> is required"
require "${SERVER_UI_CLIENT_ID}" "<client ID> is required"
require "${SERVER_UI_REDIRECT_URI}" "<redirect URI> is required"
require "${SERVER_UI_SCOPES}" "<scopes> are required"
require "${SERVER_UI_PASSWORD_DERIVATION_ENABLED}" "<password derivation enabled> is required"
require "${SERVER_UI_PASSWORD_DERIVATION_SECRET_SIZE}" "<password derivation secret size> is required"
require "${SERVER_UI_PASSWORD_DERIVATION_ITERATIONS}" "<password derivation iterations> is required"
require "${SERVER_UI_DERIVATION_SALT_PREFIX}" "<derivation salt prefix> is required"

require "${NGINX_SERVER_NAME}" "<nginx server name> is required"
require "${NGINX_SERVER_PORT}" "<nginx server port> is required"
require "${NGINX_CORS_ALLOWED_ORIGIN}" "<nginx CORS allowed origin> is required"

if echo "${NGINX_SERVER_PORT}" | grep -Eq "ssl$"
then
  require "${NGINX_SERVER_SSL_CERTIFICATE}" "<nginx server SSL certificate> is required"
  require "${NGINX_SERVER_SSL_CERTIFICATE_KEY}" "<nginx server SSL certificate key> is required"
  require "${NGINX_SERVER_SSL_PROTOCOLS}" "<nginx server SSL protocols> is required"
  require "${NGINX_SERVER_SSL_CIPHERS}" "<nginx server SSL ciphers> is required"

  export NGINX_SERVER_SSL_CERTIFICATE_LINE="ssl_certificate ${NGINX_SERVER_SSL_CERTIFICATE};"
  export NGINX_SERVER_SSL_CERTIFICATE_KEY_LINE="ssl_certificate_key ${NGINX_SERVER_SSL_CERTIFICATE_KEY};"
  export NGINX_SERVER_SSL_PROTOCOLS_LINE="ssl_protocols ${NGINX_SERVER_SSL_PROTOCOLS};"
  export NGINX_SERVER_SSL_CIPHERS_LINE="ssl_ciphers ${NGINX_SERVER_SSL_CIPHERS};"
else
  export NGINX_SERVER_SSL_CERTIFICATE_LINE=""
  export NGINX_SERVER_SSL_CERTIFICATE_KEY_LINE=""
  export NGINX_SERVER_SSL_PROTOCOLS_LINE=""
  export NGINX_SERVER_SSL_CIPHERS_LINE=""
fi

envsubst \$SERVER_UI_SERVER_API,\$SERVER_UI_BOOTSTRAP_API,\$SERVER_UI_AUTHORIZATION_ENDPOINT,\$SERVER_UI_TOKEN_ENDPOINT,\$SERVER_UI_CLIENT_ID,\$SERVER_UI_REDIRECT_URI,\$SERVER_UI_SCOPES,\$SERVER_UI_PASSWORD_DERIVATION_ENABLED,\$SERVER_UI_PASSWORD_DERIVATION_SECRET_SIZE,\$SERVER_UI_PASSWORD_DERIVATION_ITERATIONS,\$SERVER_UI_DERIVATION_SALT_PREFIX < /opt/stasis-server-ui/templates/.env.template > /usr/share/nginx/html/assets/.env
envsubst \$NGINX_SERVER_NAME,\$NGINX_SERVER_PORT,\$NGINX_SERVER_SSL_CERTIFICATE_LINE,\$NGINX_SERVER_SSL_CERTIFICATE_KEY_LINE,\$NGINX_SERVER_SSL_PROTOCOLS_LINE,\$NGINX_SERVER_SSL_CIPHERS_LINE,\$NGINX_CORS_ALLOWED_ORIGIN < /opt/stasis-server-ui/templates/nginx.template > /etc/nginx/nginx.conf

echo "Config("
cat /etc/nginx/nginx.conf
echo ")"

nginx -g 'daemon off;'
