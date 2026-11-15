# 03 — Findings

## Finding title

Unauthenticated remote management access via JMX over RMI

## Affected laboratory configuration

The **vulnerable** configuration: the JMX RMI connector is exported with
authentication disabled and no transport encryption. (Registry 1099, connector
9090, both plaintext.)

## Technical root cause

The JMX RMI connector server was created without the
`jmx.remote.x.password.file` / `jmx.remote.x.access.file` authenticator and
without a TLS socket factory. With no authenticator configured, the connector
accepts every client and authorizes it for all MBean operations.

## Prerequisites

* Network reachability to the RMI registry (1099) and connector (9090).
* No credentials, certificates, or prior trust are required.

## Attack surface

The `MaintenanceMode` MBean (`com.lab:type=MaintenanceMode`) — its attributes
and its state-changing operations.

## Reproduction steps

```bash
./scripts/lab-up.sh --mode vulnerable --ack-insecure-lab
./scripts/verify.sh --mode vulnerable
```

The client, in sequence: connects without credentials, enumerates the MBean,
reads attributes, invokes `enableMaintenance`, and re-reads
`StateChangeCount`.

## Expected results

All steps succeed, and `StateChangeCount` increases from 0 to 1, proving the
unauthorized operation executed.

## Observed results

```
PASS  enumerate          MaintenanceMode MBean present
PASS  read-state         MaintenanceEnabled=false ApplicationStatus=OPERATIONAL StateChangeCount=0
PASS  invoke             enableMaintenance completed
PASS  verify-state-change StateChangeCount 0 -> 1
```

Full output: `evidence/vulnerable-verify.txt`.

## Verified impact

An unauthenticated remote client can read management attributes and invoke
state-changing operations on the exposed MBean. This is confirmed by the
application's own observable state (the counter increments), not merely by a
successful connection.

## Potential impact (separate from verified impact)

In real deployments, an unauthenticated JMX endpoint exposes the *platform*
MBeans and any application MBeans — far more consequence than this synthetic
MBean. Historically, JMX RMI has been used as a vector for remote code
execution via MLet loading and deserialization attacks. **This laboratory does
not demonstrate, and does not claim, remote code execution.** The potential
impact is noted for context only.

## Evidence references

* `evidence/vulnerable-verify.txt`
* `lab/application/src/main/java/com/lab/application/LabServer.java` (vulnerable
  path: no authenticator, no TLS)
* `lab/client/src/main/java/com/lab/client/Verifier.java`

## Weakness classification

* CWE-306 — Missing Authentication for Critical Function.
* CWE-311 — Missing Encryption of Sensitive Data (transport).
* CWE-862 — Missing Authorization.

These are qualitative mappings. No CVE identifier exists for this laboratory;
none is claimed.

## CVSS

No CVSS score is assigned. A meaningful score requires deployment context
(exposure, asset value, reachability) that a synthetic lab does not provide.
The lab demonstrates the technical condition; it does not assert a
production risk rating.
