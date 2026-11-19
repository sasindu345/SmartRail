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

# TODO(M4): build the SAN list from "$@" (dns:name for host names, ip:addr for IPs)
# TODO(M4): keytool -genkeypair  -> certs/server-keystore.p12 (RSA 2048 or EC, validity from an argument/env, -ext SAN=...)
# TODO(M4): keytool -exportcert  -> temporary server certificate
# TODO(M4): keytool -importcert  -> certs/client-truststore.p12 (-noprompt)
# TODO(M4): also create an UNTRUSTED second keystore for the "untrusted certificate is rejected" demo
echo "TODO(M4): implement certificate generation" >&2
exit 1
