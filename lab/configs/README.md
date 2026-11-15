# JMX/RMI Security Research Lab — configuration profiles
#
#   vulnerable/   Deliberately insecure profile (no auth, no TLS).
#   hardened/     Hardened profile (auth + TLS + authorization).
#
# The access-control file and JMX password file used by the hardened profile
# are generated locally by scripts/generate-secrets.sh (the password file is
# never committed). The JMX access file is a static, role-mapping template:
# it is committed because it contains no secrets — it only maps principals to
# authorization levels.
#
# See research/04-remediation.md for how these map to the deployed controls.
