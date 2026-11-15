# Reproduction Guide

This guide lets a third party reproduce every claim in the research report
from a clean clone, with no access to the author's infrastructure.

## 0. Requirements

* Linux host with Docker Engine and Docker Compose v2.
* `keytool` (any JDK) and `openssl` for certificate generation.

```bash
git clone git@github.com:jrf17/jmx-rmi-security-lab.git
cd jmx-rmi-security-lab
```

## 1. Build and run the unit tests

```bash
cd lab && mvn test && cd ..
```

Expected: 5 passing tests for the `MaintenanceMode` MBean.

(If you do not have a local Maven, the Docker build runs the same sources in
`maven:3.9-eclipse-temurin-21`.)

## 2. Vulnerable-mode reproduction

```bash
./scripts/lab-up.sh --mode vulnerable --ack-insecure-lab
./scripts/verify.sh --mode vulnerable
```

This starts the target with authentication and TLS disabled, then:

1. connects unauthenticated,
2. enumerates the `MaintenanceMode` MBean,
3. reads its attributes,
4. invokes `enableMaintenance`, and
5. asserts the `StateChangeCount` increased — proving the operation ran.

Expected result: `ALL VERIFY SCENARIOS PASSED` — the unauthorized access and
state change succeed, which is the demonstrated failure condition.

## 3. Hardened-mode reproduction

```bash
./scripts/lab-down.sh
./scripts/lab-up.sh --mode hardened
./scripts/verify.sh --mode hardened
```

This proves the six hardened controls:

1. operator (valid credentials + trusted TLS client) can invoke — **allowed**.
2. readonly (valid credentials) can read but not write — **allowed read**.
3. readonly attempts a state change — **denied** (`SecurityException`).
4. invalid credentials — **rejected** (`SecurityException`).
5. anonymous — **rejected** (`SecurityException`).
6. untrusted TLS client — **rejected** (TLS handshake / PKIX failure).

Expected result: `ALL VERIFY SCENARIOS PASSED`.

## 4. Regenerating secrets

Certificates and credentials are generated on demand; deleting
`lab/.secrets/` and re-running `lab-up.sh` regenerates them:

```bash
rm -rf lab/.secrets
./scripts/lab-up.sh --mode hardened
```

## 5. Tearing down

```bash
./scripts/lab-down.sh
```

This removes only the project's containers and network. It does not run
`docker system prune` or touch unrelated Docker resources.

## 6. Recording evidence

The `evidence/` files are captured output of `verify.sh`. To regenerate them:

```bash
./scripts/lab-up.sh --mode vulnerable --ack-insecure-lab
./scripts/verify.sh --mode vulnerable | tee evidence/vulnerable-verify.txt
./scripts/lab-down.sh
./scripts/lab-up.sh --mode hardened
./scripts/verify.sh --mode hardened | tee evidence/hardened-verify.txt
```

Redact any password lines before committing (`keyStorePassword=…` /
`trustStorePassword=…`).
