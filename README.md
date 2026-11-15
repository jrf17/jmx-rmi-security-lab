# JMX/RMI Security Research Lab

An isolated, reproducible security research laboratory that demonstrates the
risks of exposing Java Management Extensions (JMX) over RMI without
authentication and transport encryption, and validates the controls that
eliminate those risks.

The lab ships a deliberately vulnerable JMX/RMI target, an original Java
verification client, a hardened configuration of the same target, automated
security regression tests, and a research report backed by captured evidence.

> **Authorized use only.** This project is an intentionally vulnerable
> research environment. Run it only on machines you own or are authorized to
> test, and only inside its own isolated Docker network. See
> [SECURITY.md](SECURITY.md).

---

## 1. Research objective

Demonstrate, with reproducible evidence, that:

1. A JMX/RMI management endpoint configured with authentication and TLS
   disabled is remotely reachable by an unauthenticated client.
2. An unauthenticated client can enumerate MBeans, read attributes, and invoke
   state-changing management operations.
3. Enabling authentication, TLS (with mutual authentication), and
   role-based authorization blocks those same operations for anonymous,
   credential-less, read-only, and untrusted-TLS clients.

The deliverable is not merely a vulnerable Java application. It is a complete
research project with demonstrated attack conditions, validated security
controls, tests, and defensible conclusions.

## 2. Architecture

```
                            ┌──────────────────────────────────────┐
                            │        Docker bridge (internal)       │
                            │      no host/LAN/Internet routing     │
                            │                                        │
  ┌──────────────────────┐  │    ┌────────────────────────────┐     │
  │  verifier (client)    │──┼───▶│  target (JMX/RMI server)    │     │
  │  JMXConnectorFactory  │  │    │  registry :1099 (plain)     │     │
  │  + TLS client identity│  │    │  connector :9090            │     │
  └──────────────────────┘  │    │  MaintenanceMode MBean       │     │
                            │    └────────────────────────────┘     │
                            └──────────────────────────────────────┘
```

* **target** — a standalone Java application exposing a synthetic
  `MaintenanceMode` MBean over the JMX RMI connector.
* **verifier** — an original Java client that connects, enumerates, reads,
  and invokes the controlled laboratory operation, reporting PASS/FAIL per
  assertion with meaningful exit codes.
* Both containers run non-root, drop all Linux capabilities, use a read-only
  root filesystem, and sit on an `internal: true` Docker network with **no
  published ports**.

### Two configurations, one application

| Property          | Vulnerable            | Hardened                        |
|-------------------|-----------------------|---------------------------------|
| Authentication    | disabled              | password file (operator/readonly) |
| Transport         | plaintext             | TLS + mutual auth               |
| Authorization     | none                  | role-based (readonly/readwrite) |
| Registry port     | 1099 (plain)          | 1099 (plain, internal)          |
| Connector port    | 9090 (plain)          | 9090 (TLS)                      |
| Network exposure  | internal only         | internal only                   |

## 3. Threat model

* **Attacker:** an unauthenticated network client that can reach the JMX/RMI
  ports from inside the lab network.
* **Asset:** the synthetic `MaintenanceMode` management state (maintenance
  flag, application status, change counter).
* **Threat:** unauthorized read and modification of management state.
* **Assumption:** the endpoint is reachable. The lab scopes "reachable" to the
  internal Docker network only; it does not model an Internet-exposed
  endpoint.

## 4. Security boundaries

* No JMX/RMI port is ever published to the host, LAN, or Internet.
* The lab network is `internal: true` — no external routing.
* No host networking, no privileged containers, no Docker socket mount, no
  sensitive host directory mounts.
* Containers run as non-root (`lab`), drop all capabilities, use read-only
  root filesystems with a private `noexec,nosuid` tmpfs, and are resource
  limited.
* Only synthetic laboratory data is used; the MBean cannot execute OS
  commands, touch the filesystem, or reach external systems.
* Vulnerable mode requires an explicit `--ack-insecure-lab` opt-in.

## 5. Key features

* One codebase, two configurations (`--mode vulnerable|hardened`).
* Original Java verification client with per-assertion PASS/FAIL output and
  exit codes that distinguish security denials from broken connectivity.
* Mutual-TLS with a locally generated CA, server, client, and untrusted
  identities — trust is never disabled to make tests pass.
* Role-based authorization (read-only vs. operator) via the JDK's
  `jmx.remote.x.access.file` mechanism.
* Reproducible secret generation (`scripts/generate-secrets.sh`); nothing
  secret is committed.
* Automated security regression tests across both configurations.
* GitHub Actions CI (unit tests + isolated Docker integration tests).

## 6. Prerequisites

* Docker Engine + Docker Compose (v2).
* `keytool` and `openssl` (for local certificate generation).
* Linux host (the lab scripts are written for bash).

No local JDK is required: both images build inside `maven:3.9-eclipse-temurin-21`
and run on `eclipse-temurin:21-jre`.

## 7. Quick start

```bash
# Start the hardened lab (default)
./scripts/lab-up.sh --mode hardened

# Verify hardened controls (auth + TLS + authorization)
./scripts/verify.sh --mode hardened

# Stop the lab
./scripts/lab-down.sh
```

## 8. Vulnerable-mode reproduction (opt-in)

```bash
./scripts/lab-up.sh --mode vulnerable --ack-insecure-lab
./scripts/verify.sh --mode vulnerable
```

This demonstrates that an unauthenticated client can connect, enumerate the
`MaintenanceMode` MBean, read its attributes, and invoke `enableMaintenance`,
observing the state change through the MBean's own `StateChangeCount`.

## 9. Hardened-mode reproduction

```bash
./scripts/lab-up.sh --mode hardened
./scripts/verify.sh --mode hardened
```

This proves: anonymous access is rejected, invalid credentials are rejected,
a read-only principal cannot modify state, and an untrusted TLS client fails
certificate validation — while an authorized operator still succeeds.

## 10. Sample verified output

Vulnerable (the finding):

```
PASS  enumerate          MaintenanceMode MBean present
PASS  read-state         MaintenanceEnabled=false ApplicationStatus=OPERATIONAL StateChangeCount=0
PASS  invoke             enableMaintenance completed
PASS  verify-state-change StateChangeCount 0 -> 1
```

Hardened (the controls):

```
PASS  connection-rejected  authentication/authorization rejected: SecurityException: Authentication failed! Credentials required
PASS  invoke denied        SecurityException: Access denied! Invalid access level for requested MBeanServer operation.
PASS  connection-rejected  SSLHandshakeException ... PKIX path building failed ... (untrusted cert)
```

Full captured evidence is under [`evidence/`](evidence/) and is regenerated by
the scripts.

## 11. Test instructions

```bash
# Java unit tests (MBean behavior)
cd lab && mvn test

# Full security regression tests (Docker)
./scripts/lab-up.sh --mode vulnerable --ack-insecure-lab && ./scripts/verify.sh --mode vulnerable
./scripts/lab-up.sh --mode hardened && ./scripts/verify.sh --mode hardened
./scripts/lab-down.sh
```

## 12. Research findings

The complete, evidence-backed findings are in [`research/`](research/):

* `01-background.md` — JMX, MBeans, RMI connector architecture, controls.
* `02-methodology.md` — setup, scope, procedure, evidence collection.
* `03-findings.md` — the formal finding, root cause, and verified impact.
* `04-remediation.md` — the hardening controls and their validation.
* `05-limitations.md` — what the lab does and does not prove.

In short: with authentication and TLS disabled, the JMX RMI connector allows
an unauthenticated remote client to read management attributes and invoke
state-changing operations. Enabling password authentication, TLS with mutual
authentication, and role-based authorization closes each of those paths.

## 13. Remediation summary

1. Enable JMX password authentication (`jmx.remote.x.password.file`).
2. Enable TLS with a proper trust chain and mutual authentication
   (`jmx.remote.x.ssl` / `SslRMIClientSocketFactory`).
3. Restrict authorization by role via `jmx.remote.x.access.file`
   (`readonly` vs `readwrite`).
4. Bind only to trusted interfaces and never expose the RMI registry or
   connector to untrusted networks.
5. Protect credential and key files with restrictive permissions.
6. Validate the controls with negative tests that distinguish denial from
   outage.

## 14. Project structure

```
jmx-rmi-security-lab/
├── lab/
│   ├── docker-compose.yml
│   ├── application/        # target (MBean + JMX/RMI server)
│   ├── client/             # verification client
│   └── configs/            # hardened access template, profile docs
├── research/               # background, methodology, findings, remediation, limitations
├── evidence/               # sanitized, reproducible verification output
├── scripts/                # lab-up.sh, verify.sh, lab-down.sh, generate-secrets.sh
├── docs/                   # architecture + reproduction guide
└── .github/workflows/ci.yml
```

## 15. Limitations

* The lab demonstrates unauthorized JMX *management access*, not arbitrary
  code execution. It does not implement MLet class loading, deserialization
  gadget chains, or any exploit payload.
* Findings are validated on OpenJDK/Temurin 21 inside a specific container
  topology and do not generalize unchanged to every JMX deployment.
* See [`research/05-limitations.md`](research/05-limitations.md) for a full
  accounting.

## 16. Safety and authorized-use statement

This software is provided for defensive security research and education only.
Use it solely on systems you own or are explicitly authorized to test, and
solely inside the provided isolated Docker network. The author assumes no
liability for misuse.

## 17. License

[MIT](LICENSE)
