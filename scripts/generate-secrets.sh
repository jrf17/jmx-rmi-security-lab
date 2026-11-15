#!/usr/bin/env bash
#
# generate-secrets.sh — reproducible local secret generation for the lab.
#
# Creates, under lab/.secrets/ (gitignored):
#   * A local CA, a server certificate (CN/SAN = target, localhost), and a
#     client certificate, all signed by that CA.
#   * server keystore/truststore and client keystore/truststore (PKCS12).
#   * A separate "untrusted" CA + client identity used only to prove that a
#     client not trusted by the server is rejected during TLS.
#   * jmx.password (operator/readonly) with random passwords, and the env
#     file consumed by docker-compose.
#
# Never commit anything under lab/.secrets/.
set -euo pipefail

LAB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS_DIR="${LAB_DIR}/lab/.secrets"
CONFIG_DIR="${LAB_DIR}/lab/configs/hardened"

mkdir -p "${SECRETS_DIR}"

STORE_PASS="$(openssl rand -hex 24)"
KEY_PASS="${STORE_PASS}"
OPERATOR_PASS="$(openssl rand -hex 16)"
READONLY_PASS="$(openssl rand -hex 16)"
TRUST_PASS="${STORE_PASS}"

# ---- CA ---------------------------------------------------------------------
keytool -genkeypair -alias lab-ca -keyalg RSA -keysize 2048 \
  -dname "CN=JMX Lab Root CA, O=JMX-RMI Security Lab, C=US" \
  -validity 365 -keypass "${KEY_PASS}" -storepass "${STORE_PASS}" \
  -keystore "${SECRETS_DIR}/ca.p12" -storetype PKCS12 -ext bc:c \
  >/dev/null 2>&1

keytool -exportcert -alias lab-ca -storepass "${STORE_PASS}" \
  -keystore "${SECRETS_DIR}/ca.p12" -rfc -file "${SECRETS_DIR}/ca.crt" \
  >/dev/null 2>&1

# ---- server identity --------------------------------------------------------
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 \
  -dname "CN=target, O=JMX-RMI Security Lab, C=US" \
  -validity 365 -keypass "${KEY_PASS}" -storepass "${STORE_PASS}" \
  -keystore "${SECRETS_DIR}/server.p12" -storetype PKCS12 \
  >/dev/null 2>&1

keytool -certreq -alias server -keypass "${KEY_PASS}" \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/server.p12" \
  -file "${SECRETS_DIR}/server.csr" >/dev/null 2>&1

keytool -gencert -alias lab-ca -keypass "${KEY_PASS}" \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/ca.p12" \
  -infile "${SECRETS_DIR}/server.csr" -outfile "${SECRETS_DIR}/server.crt" \
  -validity 365 \
  -ext "SAN=dns:target,dns:localhost,ip:127.0.0.1" \
  -ext "KU=digitalSignature,keyEncipherment" -ext "EKU=serverAuth" \
  >/dev/null 2>&1

keytool -importcert -alias lab-ca -noprompt \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/server.p12" \
  -file "${SECRETS_DIR}/ca.crt" >/dev/null 2>&1

keytool -importcert -alias server -noprompt -keypass "${KEY_PASS}" \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/server.p12" \
  -file "${SECRETS_DIR}/server.crt" >/dev/null 2>&1

# server truststore trusts the lab CA (for mutual-TLS client certs)
keytool -importcert -alias lab-ca -noprompt \
  -storepass "${TRUST_PASS}" -keystore "${SECRETS_DIR}/server-trust.p12" \
  -storetype PKCS12 -file "${SECRETS_DIR}/ca.crt" >/dev/null 2>&1

# ---- client identity --------------------------------------------------------
keytool -genkeypair -alias client -keyalg RSA -keysize 2048 \
  -dname "CN=jmx-lab-client, O=JMX-RMI Security Lab, C=US" \
  -validity 365 -keypass "${KEY_PASS}" -storepass "${STORE_PASS}" \
  -keystore "${SECRETS_DIR}/client.p12" -storetype PKCS12 \
  >/dev/null 2>&1

keytool -certreq -alias client -keypass "${KEY_PASS}" \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/client.p12" \
  -file "${SECRETS_DIR}/client.csr" >/dev/null 2>&1

keytool -gencert -alias lab-ca -keypass "${KEY_PASS}" \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/ca.p12" \
  -infile "${SECRETS_DIR}/client.csr" -outfile "${SECRETS_DIR}/client.crt" \
  -validity 365 \
  -ext "KU=digitalSignature,keyEncipherment" -ext "EKU=clientAuth" \
  >/dev/null 2>&1

keytool -importcert -alias lab-ca -noprompt \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/client.p12" \
  -file "${SECRETS_DIR}/ca.crt" >/dev/null 2>&1

keytool -importcert -alias client -noprompt -keypass "${KEY_PASS}" \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/client.p12" \
  -file "${SECRETS_DIR}/client.crt" >/dev/null 2>&1

# client truststore trusts the lab CA (to verify the server cert)
keytool -importcert -alias lab-ca -noprompt \
  -storepass "${TRUST_PASS}" -keystore "${SECRETS_DIR}/client-trust.p12" \
  -storetype PKCS12 -file "${SECRETS_DIR}/ca.crt" >/dev/null 2>&1

# ---- untrusted client identity (separate CA) --------------------------------
keytool -genkeypair -alias untrusted-ca -keyalg RSA -keysize 2048 \
  -dname "CN=Untrusted CA, O=JMX-RMI Security Lab, C=US" \
  -validity 365 -keypass "${KEY_PASS}" -storepass "${STORE_PASS}" \
  -keystore "${SECRETS_DIR}/untrusted-ca.p12" -storetype PKCS12 -ext bc:c \
  >/dev/null 2>&1

keytool -exportcert -alias untrusted-ca -storepass "${STORE_PASS}" \
  -keystore "${SECRETS_DIR}/untrusted-ca.p12" -rfc \
  -file "${SECRETS_DIR}/untrusted-ca.crt" >/dev/null 2>&1

keytool -genkeypair -alias untrusted-client -keyalg RSA -keysize 2048 \
  -dname "CN=untrusted-client, O=JMX-RMI Security Lab, C=US" \
  -validity 365 -keypass "${KEY_PASS}" -storepass "${STORE_PASS}" \
  -keystore "${SECRETS_DIR}/untrusted-client.p12" -storetype PKCS12 \
  >/dev/null 2>&1

keytool -certreq -alias untrusted-client -keypass "${KEY_PASS}" \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/untrusted-client.p12" \
  -file "${SECRETS_DIR}/untrusted-client.csr" >/dev/null 2>&1

keytool -gencert -alias untrusted-ca -keypass "${KEY_PASS}" \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/untrusted-ca.p12" \
  -infile "${SECRETS_DIR}/untrusted-client.csr" \
  -outfile "${SECRETS_DIR}/untrusted-client.crt" -validity 365 \
  -ext "KU=digitalSignature,keyEncipherment" -ext "EKU=clientAuth" \
  >/dev/null 2>&1

keytool -importcert -alias untrusted-ca -noprompt \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/untrusted-client.p12" \
  -file "${SECRETS_DIR}/untrusted-ca.crt" >/dev/null 2>&1

keytool -importcert -alias untrusted-client -noprompt -keypass "${KEY_PASS}" \
  -storepass "${STORE_PASS}" -keystore "${SECRETS_DIR}/untrusted-client.p12" \
  -file "${SECRETS_DIR}/untrusted-client.crt" >/dev/null 2>&1

# untrusted client's own truststore (trusts untrusted-ca, NOT the lab CA)
keytool -importcert -alias untrusted-ca -noprompt \
  -storepass "${TRUST_PASS}" -keystore "${SECRETS_DIR}/untrusted-trust.p12" \
  -storetype PKCS12 -file "${SECRETS_DIR}/untrusted-ca.crt" >/dev/null 2>&1

# ---- JMX credentials --------------------------------------------------------
JMX_PASSWORD_FILE="${SECRETS_DIR}/jmx.password"
{
  echo "operator ${OPERATOR_PASS}"
  echo "readonly ${READONLY_PASS}"
} > "${JMX_PASSWORD_FILE}"

# The access file (static, committed) is copied into the secrets dir so the
# container mounts one directory.
cp "${CONFIG_DIR}/jmx.access" "${SECRETS_DIR}/jmx.access"

# ---- env file for docker-compose --------------------------------------------
ENV_FILE="${SECRETS_DIR}/lab.env"
{
  echo "STORE_PASS=${STORE_PASS}"
  echo "KEY_PASS=${KEY_PASS}"
  echo "TRUST_PASS=${TRUST_PASS}"
  echo "OPERATOR_PASS=${OPERATOR_PASS}"
  echo "READONLY_PASS=${READONLY_PASS}"
  echo "SECRETS_DIR=${SECRETS_DIR}"
  echo "ACCESS_FILE=${CONFIG_DIR}/jmx.access"
  echo "PASSWORD_FILE=${JMX_PASSWORD_FILE}"
} > "${ENV_FILE}"

# Permissions: synthetic lab material only (never committed). World-readable
# so the non-root container user (uid 10001) can read it over the bind mount.
chmod 644 "${SECRETS_DIR}"/*.p12 "${SECRETS_DIR}"/*.crt "${SECRETS_DIR}"/*.csr \
          "${SECRETS_DIR}/jmx.password" "${SECRETS_DIR}/jmx.access"
chmod 600 "${ENV_FILE}"

echo "[secrets] generated under ${SECRETS_DIR}"
echo "[secrets] JMX password file: ${JMX_PASSWORD_FILE}"
echo "[secrets] env file: ${ENV_FILE}"
