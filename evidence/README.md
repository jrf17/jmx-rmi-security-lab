# Evidence

Captured, sanitized verification output from actual laboratory runs.

* [`vulnerable-verify.txt`](vulnerable-verify.txt) — vulnerable mode: the
  unauthenticated connection, enumeration, read, and state-changing operation
  all succeed (the demonstrated failure condition).
* [`hardened-verify.txt`](hardened-verify.txt) — hardened mode: anonymous and
  invalid-credential rejection, read-only denial, and untrusted-TLS rejection,
  with an authorized operator succeeding. Passwords are redacted.

The files are the unmodified stdout of `scripts/verify.sh`, with only the
keystore/truststore password values redacted in the hardened file. They can be
regenerated exactly as described in
[`docs/reproduction-guide.md`](../docs/reproduction-guide.md).

Environment at capture time (recorded inside each file):

* Host: Linux 7.0.0-43-generic (x86_64)
* Docker Engine 29.9.3, Docker Compose v5.7.1
* JDK: Eclipse Temurin 21 (build image `maven:3.9-eclipse-temurin-21`,
  runtime `eclipse-temurin:21-jre`)

Nothing under `lab/.secrets/` (certificates, keys, credentials) is committed.
