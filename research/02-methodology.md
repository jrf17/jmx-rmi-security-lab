# 02 — Methodology

## Lab setup

Two configurations of one Java application are exercised inside an isolated
Docker network (`internal: true`, no published ports):

* **Vulnerable:** authentication and TLS disabled.
* **Hardened:** password authentication, TLS with mutual authentication, and
  role-based authorization (`readonly` / `operator`).

Secrets (CA, server/client certificates, an untrusted identity, and JMX
credentials) are generated locally by `scripts/generate-secrets.sh` and
mounted read-only into the containers.

## Testing assumptions

1. The attacker can reach the JMX/RMI ports from inside the lab network.
   (The lab models an exposed management endpoint; it does not publish to the
   host.)
2. "Unauthorized" means: not authenticated, or authenticated only as a
   read-only principal, or presenting a certificate the server does not
   trust.
3. A successful `SecurityException` or TLS handshake failure is the *correct*
   security outcome for a negative test; a plain connect failure is not.

## Scope boundaries

* Target: only the lab's own `MaintenanceMode` MBean and the JMX RMI
  connector.
* No scanning of public systems, corporate networks, or third parties.
* No destructive operations, credential harvesting, lateral movement,
  persistence, or exploitation payloads.
* No arbitrary-code-execution demonstration.

## Verification procedure

For each configuration, `scripts/verify.sh` runs the verification client in a
sibling container and asserts specific outcomes:

**Vulnerable mode (positive findings):**

1. unauthenticated connect → must succeed,
2. enumerate `MaintenanceMode` → present,
3. read attributes → values returned,
4. invoke `enableMaintenance` → completes,
5. `StateChangeCount` increases → operation observed.

**Hardened mode (negative + positive controls):**

1. operator + trusted TLS + valid credentials → invoke succeeds;
2. readonly + valid credentials → reads succeed, no write attempted;
3. readonly invokes → `SecurityException` (denied);
4. invalid credentials → `SecurityException` (rejected);
5. anonymous → `SecurityException` (rejected);
6. untrusted TLS client → TLS handshake / PKIX failure (rejected).

## Test inputs

Synthetic credentials (`operator`, `readonly`) and locally generated
certificates. No real credentials, keys, or production identifiers are used.

## Evidence collection

Evidence is the unmodified stdout of the verification client, captured to
`evidence/*.txt` with only password values redacted. Each file records the
host, Docker, Compose, and JDK versions at capture time so results are
reproducible.

## Environment

* Docker Engine 29.9.3, Docker Compose v5.7.1
* Eclipse Temurin 21 (build `maven:3.9-eclipse-temurin-21`, run
  `eclipse-temurin:21-jre`)
* Host: Linux 7.0.0-43-generic (x86_64)
