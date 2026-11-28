#!/usr/bin/env bash
# OWNER: Member 4 - generates DEVELOPMENT-ONLY TLS material for SmartRail with the JDK's keytool.
#
# Output (git-ignored):  certs/server-keystore.p12   (server private key + self-signed cert)
#                        certs/client-truststore.p12 (server cert only, trusted by clients)
# Passwords come from the environment - nothing is hard-coded:
#   export SMARTRAIL_KEYSTORE_PASSWORD=...  SMARTRAIL_TRUSTSTORE_PASSWORD=...
# Server host names/IPs for the certificate SAN come from arguments:
#   scripts/generate-dev-certs.sh localhost 192.168.1.20
set -euo pipefail
cd "$(dirname "$0")/.."

: "${SMARTRAIL_KEYSTORE_PASSWORD:?export SMARTRAIL_KEYSTORE_PASSWORD first}"
: "${SMARTRAIL_TRUSTSTORE_PASSWORD:?export SMARTRAIL_TRUSTSTORE_PASSWORD first}"
[ $# -ge 1 ] || { echo "usage: $0 <host-or-ip> [more hosts/ips...]" >&2; exit 1; }

# Build SAN list: dns:name for hostnames, ip:addr for IPv4/IPv6 literals
SAN=""
for arg in "$@"; do
    if [[ $arg =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || [[ $arg =~ .*:.* ]]; then
        entry="ip:$arg"
    else
        entry="dns:$arg"
    fi
    SAN="${SAN:+$SAN,}$entry"
done

mkdir -p certs

echo "[M4] Generating server keystore (SAN=$SAN)..."
keytool -genkeypair \
  -alias server \
  -keyalg RSA -keysize 2048 \
  -validity 365 \
  -dname "CN=SmartRail Dev, O=SmartRail, C=LK" \
  -ext "SAN=$SAN" \
  -storetype PKCS12 \
  -keystore certs/server-keystore.p12 \
  -storepass "${SMARTRAIL_KEYSTORE_PASSWORD}" \
  -keypass  "${SMARTRAIL_KEYSTORE_PASSWORD}" 2>/dev/null

echo "[M4] Exporting server cert..."
keytool -exportcert \
  -alias server \
  -keystore certs/server-keystore.p12 \
  -storepass "${SMARTRAIL_KEYSTORE_PASSWORD}" \
  -file certs/server.cer 2>/dev/null

echo "[M4] Building client truststore..."
keytool -importcert \
  -alias server \
  -file certs/server.cer \
  -storetype PKCS12 \
  -keystore certs/client-truststore.p12 \
  -storepass "${SMARTRAIL_TRUSTSTORE_PASSWORD}" \
  -noprompt 2>/dev/null

rm -f certs/server.cer

echo "[M4] Generating untrusted keystore (for rejection demo)..."
keytool -genkeypair \
  -alias untrusted \
  -keyalg RSA -keysize 2048 \
  -validity 365 \
  -dname "CN=Untrusted Rogue CA, O=Rogue, C=LK" \
  -storetype PKCS12 \
  -keystore certs/untrusted-keystore.p12 \
  -storepass "${SMARTRAIL_KEYSTORE_PASSWORD}" \
  -keypass  "${SMARTRAIL_KEYSTORE_PASSWORD}" 2>/dev/null

echo "[M4] Done. Files in certs/: server-keystore.p12, client-truststore.p12, untrusted-keystore.p12"
