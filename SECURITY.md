# Security Policy

## Scope

This repository is an **intentionally vulnerable** security research
laboratory. Its purpose is to demonstrate, in an isolated environment, the
risks of unauthenticated and unencrypted JMX/RMI management endpoints, and to
validate the controls that remediate them.

## Safety rules for operating the lab

1. **Run only inside the provided Docker network.** The lab's containers are
   joined to an `internal: true` bridge network with no external routing. Do
   not modify `lab/docker-compose.yml` to publish ports.
2. **Never point the verifier at a system you do not own.** The verification
   client targets one operator-supplied endpoint and performs only
   enumeration, attribute reads, and the controlled `MaintenanceMode`
   operations. It is not a general-purpose scanner or exploitation tool.
3. **Vulnerable mode is opt-in.** `scripts/lab-up.sh` refuses to start the
   unauthenticated configuration unless `--ack-insecure-lab` is passed.
4. **No secret material is committed.** Certificates, keys, and credentials
   are generated under `lab/.secrets/` (gitignored) by
   `scripts/generate-secrets.sh`. Never commit anything under that directory.
5. **Only synthetic data.** The `MaintenanceMode` MBean holds in-memory state
   only; it cannot execute OS commands or touch external systems.

## What this project does NOT do

* It does not implement arbitrary code execution, MLet class loading,
  deserialization gadget chains, reverse shells, or payload delivery.
* It does not scan public systems, corporate networks, or arbitrary
  discovered targets.
* It does not perform destructive operations or credential harvesting.

## Reporting a problem

If you find a defect in the laboratory itself (a broken build, an incorrect
claim, a control that does not behave as documented), open an issue describing
the defect and how to reproduce it. Please do not report the deliberately
vulnerable behavior as a vulnerability — that is the point of the lab.
