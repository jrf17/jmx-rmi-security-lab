# Vulnerable profile — configuration notes (no files required).
#
# The vulnerable profile intentionally disables both JMX authentication and
# transport encryption. It requires no access file, password file, keystore,
# or truststore: the target runs with lab.mode=vulnerable, which sets
# auth=disabled and tls=disabled.
#
# This profile exists solely to demonstrate the security failure condition
# in an isolated network. It is never the default and is never exposed to
# the host, LAN, or Internet.
