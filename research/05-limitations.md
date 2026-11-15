# 05 — Limitations

## What the laboratory proves

* That a JMX RMI connector configured without authentication and TLS accepts
  an unauthenticated remote client.
* That such a client can enumerate an MBean, read attributes, and invoke a
  state-changing operation, with the effect confirmed by observable state.
* That enabling password authentication, mutual TLS, and role-based
  authorization rejects anonymous, credential-less, read-only-mutation, and
  untrusted-TLS access while allowing an authorized operator.

## What the laboratory does NOT prove

* **It does not demonstrate remote code execution.** Unauthorized MBean
  access is a real and serious weakness, but it is categorically different
  from arbitrary code execution, which would require a further mechanism
  (MLet loading, a deserialization gadget chain, or a vulnerable MBean
  operation). None is implemented or claimed here.
* It does not demonstrate exploitation of the platform (JVM) MBeans or any
  application server MBeans; only the synthetic `MaintenanceMode` MBean is
  exercised.
* It does not quantify real-world impact; no CVSS score or CVE is assigned
  because those require deployment context.

## JDK-specific behavior

* Findings are validated on Eclipse Temurin 21 (OpenJDK 21). JMX
  authentication, access-control file syntax, and TLS socket-factory behavior
  have varied across JDK versions; results are not guaranteed identical on
  other versions.
* The access-control file uses the `username=accesslevel` properties format
  as implemented by this JDK's `MBeanServerFileAccessController`. Other JDK
  versions or vendors may document a different principal syntax.

## Deployment-specific assumptions

* The target runs inside a container network where the RMI hostname (`target`)
  resolves and the registry (1099) and connector (9090) ports are fixed.
  Real deployments differ in hostname advertisement, firewalling, and port
  allocation, and those differences affect reachability and the failure modes
  observed.
* The registry is left plaintext (see `docs/architecture.md`). A deployment
  with additional requirements would also protect or bind the registry.

## Why findings cannot be generalized to every JMX deployment

Whether an unauthenticated JMX endpoint is exploitable in practice depends on
network exposure, the MBeans registered, their operations, and the JMX
version. The lab demonstrates the *technical condition* — missing
authentication and encryption permit unauthorized management access — under
one controlled topology. It is a reproducible case study, not a universal
claim about all JMX deployments.

## Scope of the verification tooling

The verification client targets one operator-supplied endpoint and performs
only enumeration, attribute reads, and the controlled laboratory operations.
It is not a general-purpose scanner or exploitation framework.
