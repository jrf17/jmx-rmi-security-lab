# 01 — Background

## JMX and MBeans

Java Management Extensions (JMX) is the JVM's standard management and
monitoring API. A managed resource is exposed as a **Managed Bean (MBean)** —
a Java object with a defined management interface of attributes and
operations, registered with an **MBean Server** under an `ObjectName`.

Applications expose operational state (flags, counters, status) and
administrative operations (enable/disable, reset) through MBeans so that
operators and monitoring tools can drive them at runtime.

## The RMI connector

Remote management uses a **JMX connector**. The standard remote connector is
the **RMI connector**: the connector server is exported over Java RMI and its
stub is registered in an **RMI registry** under a well-known path
(`jmxrmi`). A client:

1. contacts the registry at `jndi/rmi://HOST:PORT`,
2. looks up the connector stub,
3. uses that stub to establish the actual management channel to the
   connector's RMI server (a second port), and
4. authenticates and performs operations through the resulting
   `MBeanServerConnection`.

The registry is therefore a *directory*, not the data channel; the connector's
RMI server carries the real management traffic.

## Security controls

JMX remote access supports three independent controls:

1. **Authentication** — the connector can require credentials
   (`jmx.remote.x.password.file`, holding `username password` pairs).
2. **Authorization** — an access file (`jmx.remote.x.access.file`) maps
   principals to `readonly` or `readwrite` levels. `readonly` permits reading
   attributes and metadata but denies writes and operation invocations.
3. **Transport security** — TLS (`SslRMIClientSocketFactory` /
   `SslRMIServerSocketFactory`) encrypts the channel and, with mutual
   authentication, cryptographically identifies each peer.

These are opt-in. A JMX RMI connector configured without them accepts
unauthenticated, plaintext connections that are authorized for everything.

## Why insecure JMX is risky

A management interface is, by design, a control plane. Leaving it
unauthenticated lets any network client that can reach the registry read
operational state and invoke management operations. In real deployments,
MBeans exposed by the platform and by application servers can expose far more
consequence than this lab's synthetic MBean: stopping components, changing
configuration, and — in the worst, historically documented cases — invoking
methods that lead to code execution.

## Historical exploitation — separate from what this lab demonstrates

Historical JMX attacks have leveraged:

* **MLet / `getMBeansFromURL`** to force a target to load a remote
  `MLet` (management applet) and register attacker-supplied MBeans, a
  vector for remote code execution.
* **Java deserialization** over the RMI transport to trigger gadget chains.

This laboratory **does not implement either**. It demonstrates only
unauthorized management access: enumeration, attribute reads, and a
controlled, harmless state-changing operation. It does not claim, and must
not be read as claiming, remote code execution. The historical material is
included solely to explain why the demonstrated weakness matters in
practice.
