# Architecture

## Components

### Target application (`lab/application`)

A standalone Java 21 application (`LabServer`) that:

1. Creates a platform `MBeanServer` and registers a `MaintenanceMode` MBean
   (`com.lab:type=MaintenanceMode`).
2. Creates an RMI registry on a fixed port (`1099`).
3. Exports the JMX RMI connector server on a fixed port (`9090`).
4. Applies the configuration selected by `--mode`:
   * `vulnerable` — no authentication, no TLS.
   * `hardened` — password authentication, TLS with mutual authentication,
     and role-based authorization.

The MBean is pure in-memory state:

| Attribute / operation     | Type          | Purpose                                  |
|---------------------------|---------------|------------------------------------------|
| `MaintenanceEnabled`      | boolean (RO)  | synthetic maintenance flag               |
| `ApplicationStatus`       | String  (RO)  | synthetic application status             |
| `StateChangeCount`        | long    (RO)  | count of state-changing operations       |
| `LastStateChange`         | String  (RO)  | timestamp of last change                 |
| `enableMaintenance()`     | operation     | sets the flag, increments the counter    |
| `disableMaintenance()`    | operation     | clears the flag, increments the counter  |
| `setApplicationStatus(s)` | operation     | sets the status, increments the counter  |
| `resetCounters()`         | operation     | resets counters                          |

### Verification client (`lab/client`)

`Verifier` connects to a JMX service URL
(`service:jmx:rmi:///jndi/rmi://HOST:1099/jmxrmi`), then enumerates the MBean,
reads its attributes, and optionally invokes an operation and asserts a state
change. It reports one PASS/FAIL line per assertion and exits non-zero on any
failure.

`--expect-fail` flips the connection assertion so a rejected connection is a
PASS and an unexpected successful connection is a FAIL. The client classifies
failures as either a *security denial* (`SecurityException`, TLS handshake
failure) or a *connection error*, so a timed-out or broken service is never
mistaken for a working control.

## Network model

Both services share an `internal: true` bridge network. The target advertises
its RMI hostname as `target`, so the verifier reaches it by that name. No
ports are published to the host.

## TLS and trust model

`scripts/generate-secrets.sh` creates a local CA and issues:

* a server certificate (CN/SAN `target`) — for the target's identity;
* a client certificate — for the verifier's identity under mutual TLS;
* a separate untrusted CA + client identity — used only to prove that a
  client the server does not trust is rejected.

The server requires client authentication (`needClientAuth=true`) and trusts
only the lab CA. The client trusts only the lab CA to validate the server.
The untrusted client trusts a different CA and carries a certificate the
server's truststore does not contain, so the handshake fails.

## Authentication and authorization

Hardened mode enables the JDK's password-file authenticator
(`jmx.remote.x.password.file`) and access controller
(`jmx.remote.x.access.file`). Two principals exist:

* `readonly` — `readonly` access (reads only; invocations and writes denied).
* `operator` — `readwrite` access (the controlled operation allowed).

## Container hardening

Both containers: non-root user (`lab`, uid 10001), `cap_drop: ALL`,
`no-new-privileges`, read-only root filesystem, private `noexec,nosuid` tmpfs
for `/tmp`, `mem_limit` and `cpus` limits. The target mounts generated secret
material read-only under `/secrets`.

## Why a plain registry?

The RMI registry (1099) is plaintext in both modes. It serves only as a stub
directory: the client looks up the connector stub and then speaks to the
connector (9090), which is TLS-protected in hardened mode. Securing the
registry would not add confidentiality to the management channel and would
break the connector server's own internal bind; the meaningful transport
security boundary is the connector, which carries the real data and
credentials.
