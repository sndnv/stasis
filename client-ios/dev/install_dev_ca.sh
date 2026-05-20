#!/usr/bin/env bash

HELP="Build a .mobileconfig that bundles the dev server + identity self-signed CAs for iOS"
USAGE="Usage: $0"

if [ "$1" = "-h" ] || [ "$1" = "--help" ]
then
  echo "${HELP}"
  echo "${USAGE}"
  exit
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRETS_DIR="${SCRIPT_DIR}/../../deployment/dev/secrets"

SERVER_CERT="${SECRETS_DIR}/server.cert.pem"
IDENTITY_CERT="${SECRETS_DIR}/identity.cert.pem"

OUTPUT="${SCRIPT_DIR}/stasis-dev-ca.mobileconfig"

for cert in "${SERVER_CERT}" "${IDENTITY_CERT}"
do
  if [ ! -f "${cert}" ]
  then
    echo "Missing certificate: ${cert}"
    echo "Run \`deployment/dev/scripts/prepare_deployment.sh\` to generate all deployment config"
    exit 1
  fi
done

function cert_b64() {
  openssl x509 -in "$1" -outform DER | base64
}

function uuid() {
  uuidgen
}

SERVER_B64="$(cert_b64 "${SERVER_CERT}")"
IDENTITY_B64="$(cert_b64 "${IDENTITY_CERT}")"

SERVER_UUID="$(uuid)"
IDENTITY_UUID="$(uuid)"
PROFILE_UUID="$(uuid)"

cat > "${OUTPUT}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>PayloadContent</key>
    <array>
        <dict>
            <key>PayloadCertificateFileName</key>
            <string>server.cert.pem</string>
            <key>PayloadContent</key>
            <data>
${SERVER_B64}
            </data>
            <key>PayloadDescription</key>
            <string>stasis dev server self-signed certificate</string>
            <key>PayloadDisplayName</key>
            <string>stasis dev :: server</string>
            <key>PayloadIdentifier</key>
            <string>stasis.client.ios.dev.ca.server</string>
            <key>PayloadType</key>
            <string>com.apple.security.root</string>
            <key>PayloadUUID</key>
            <string>${SERVER_UUID}</string>
            <key>PayloadVersion</key>
            <integer>1</integer>
        </dict>
        <dict>
            <key>PayloadCertificateFileName</key>
            <string>identity.cert.pem</string>
            <key>PayloadContent</key>
            <data>
${IDENTITY_B64}
            </data>
            <key>PayloadDescription</key>
            <string>stasis dev identity self-signed certificate</string>
            <key>PayloadDisplayName</key>
            <string>stasis dev :: identity</string>
            <key>PayloadIdentifier</key>
            <string>stasis.client.ios.dev.ca.identity</string>
            <key>PayloadType</key>
            <string>com.apple.security.root</string>
            <key>PayloadUUID</key>
            <string>${IDENTITY_UUID}</string>
            <key>PayloadVersion</key>
            <integer>1</integer>
        </dict>
    </array>
    <key>PayloadDisplayName</key>
    <string>stasis dev CAs</string>
    <key>PayloadIdentifier</key>
    <string>stasis.client.ios.dev.ca</string>
    <key>PayloadType</key>
    <string>Configuration</string>
    <key>PayloadUUID</key>
    <string>${PROFILE_UUID}</string>
    <key>PayloadVersion</key>
    <integer>1</integer>
</dict>
</plist>
EOF

echo "Created [${OUTPUT}]"
echo ""
echo "To install on iOS Simulator:"
echo "  xcrun simctl boot 'iPhone 16' # if not already booted"
echo "  xcrun simctl openurl booted file://${OUTPUT}"
echo "  Then in Simulator: Settings > Profile Downloaded > Install"
echo "  Then: Settings > General > About > Certificate Trust Settings"
echo "    -> enable full trust for 'stasis dev :: server' and 'stasis dev :: identity'"
echo ""
echo "To install on a physical device:"
echo "  Email the .mobileconfig to yourself, or AirDrop it from the Mac"
echo "  Open on device: Settings > Profile Downloaded > Install"
echo "  Then enable full trust as above"
