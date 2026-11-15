package com.lab.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Original Java security-verification client for the laboratory.
 *
 * <p>Each invocation runs one scenario against the JMX endpoint and reports
 * PASS/FAIL per assertion. Scenarios are parameterized by command-line options
 * (host, port, credentials, TLS trust/identity) and are deliberately limited
 * to the local laboratory: the tool targets a single, operator-supplied
 * endpoint and performs only enumeration, attribute reads, and the controlled
 * laboratory operations on the {@code MaintenanceMode} MBean.</p>
 *
 * <p>{@code --expect-fail} flips the connection assertion so that a rejected
 * connection is reported as a PASS (with the rejection reason as evidence) and
 * an unexpected successful connection is reported as a FAIL. This lets the
 * negative tests prove that a control actually denied access, and it
 * distinguishes a genuine security denial (a {@code SecurityException} or TLS
 * handshake failure) from a broken service (a plain connect failure).</p>
 */
public final class Verifier {

    private Verifier() {
    }

    public static void main(String[] args) {
        Args parsed;
        try {
            parsed = Args.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("usage error: " + e.getMessage());
            System.err.println(Args.USAGE);
            System.exit(2);
            return;
        }

        List<Check> checks = new ArrayList<>();
        ConnectOutcome outcome = attemptConnect(parsed, checks);

        if (!outcome.connected) {
            if (parsed.expectFail) {
                checks.add(Check.pass("connection-rejected", outcome.detail));
            } else {
                checks.add(Check.fail("connect", outcome.detail));
            }
        } else if (parsed.expectFail) {
            checks.add(Check.fail("connection-rejected",
                    "connection unexpectedly succeeded — control did not deny access"));
        }

        boolean allPass = true;
        for (Check c : checks) {
            System.out.printf("%-4s  %-32s %s%n",
                    c.pass() ? "PASS" : "FAIL", c.name(), c.detail());
            if (!c.pass()) {
                allPass = false;
            }
        }
        System.out.printf("RESULT: %s (%d/%d assertions passed)%n",
                allPass ? "PASS" : "FAIL",
                checks.stream().filter(Check::pass).count(),
                checks.size());
        System.exit(allPass ? 0 : 1);
    }

    /**
     * Attempts the JMX connection and, on success, runs the enumeration /
     * attribute / invocation steps. Never exits; returns the outcome so the
     * caller can apply {@code --expect-fail} semantics.
     */
    private static ConnectOutcome attemptConnect(Args a, List<Check> checks) {
        Map<String, Object> env = new HashMap<>();
        if (a.user != null) {
            env.put(JMXConnector.CREDENTIALS,
                    new String[] {a.user, a.password == null ? "" : a.password});
        }
        // TLS trust/key material is supplied via the standard JSSE system
        // properties (javax.net.ssl.trustStore / keyStore) set by the caller.

        JMXServiceURL url;
        try {
            url = new JMXServiceURL(
                    "service:jmx:rmi:///jndi/rmi://" + a.host + ":" + a.port + "/jmxrmi");
        } catch (Exception e) {
            return ConnectOutcome.failed("invalid JMX service URL: " + e);
        }

        try (JMXConnector connector = JMXConnectorFactory.connect(url, env)) {
            MBeanServerConnection mbsc = connector.getMBeanServerConnection();
            ObjectName name = new ObjectName("com.lab:type=MaintenanceMode");
            boolean present = !mbsc.queryNames(name, null).isEmpty();
            checks.add(present
                    ? Check.pass("enumerate", "MaintenanceMode MBean present")
                    : Check.fail("enumerate", "MaintenanceMode MBean missing"));

            boolean maintenance = (Boolean) mbsc.getAttribute(name, "MaintenanceEnabled");
            String status = (String) mbsc.getAttribute(name, "ApplicationStatus");
            long count = (Long) mbsc.getAttribute(name, "StateChangeCount");
            checks.add(Check.pass("read-state",
                    "MaintenanceEnabled=" + maintenance
                    + " ApplicationStatus=" + status
                    + " StateChangeCount=" + count));

            if (a.invokeOp != null) {
                Object[] params = a.invokeArg == null ? null : new Object[] {a.invokeArg};
                String[] sig = a.invokeArg == null ? null : new String[] {String.class.getName()};
                try {
                    mbsc.invoke(name, a.invokeOp, params, sig);
                    checks.add(Check.pass("invoke", a.invokeOp + " completed"));
                } catch (SecurityException se) {
                    checks.add(Check.fail("invoke",
                            a.invokeOp + " denied: " + brief(se)));
                    return ConnectOutcome.ok();
                }
            }

            if (a.expectStateChange != null) {
                long after = (Long) mbsc.getAttribute(name, "StateChangeCount");
                boolean changed = after > count;
                checks.add(changed
                        ? Check.pass("verify-state-change",
                            "StateChangeCount " + count + " -> " + after)
                        : Check.fail("verify-state-change",
                            "StateChangeCount did not increase (was " + count + ", is " + after + ")"));
            }
            return ConnectOutcome.ok();
        } catch (SecurityException se) {
            return ConnectOutcome.failed("authentication/authorization rejected: " + brief(se));
        } catch (IOException ioe) {
            Throwable cause = rootCause(ioe);
            String detail = brief(ioe);
            if (isTlsFailure(cause)) {
                detail = "TLS handshake failed (untrusted or invalid cert): " + brief(cause);
            }
            return ConnectOutcome.failed(detail);
        } catch (Exception e) {
            return ConnectOutcome.failed("connection error: " + brief(e));
        }
    }

    private static boolean isTlsFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String name = c.getClass().getName();
            if (name.contains("SSL") || name.contains("Handshake")
                    || name.contains("Certificate")) {
                return true;
            }
            String msg = String.valueOf(c.getMessage());
            if (msg != null && (msg.contains("certificate") || msg.contains("handshake")
                    || msg.contains("SSL") || msg.contains("TLS"))) {
                return true;
            }
        }
        return false;
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }

    private static String brief(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName()
                : t.getClass().getSimpleName() + ": " + msg;
    }

    record ConnectOutcome(boolean connected, String detail) {
        static ConnectOutcome ok() {
            return new ConnectOutcome(true, null);
        }

        static ConnectOutcome failed(String detail) {
            return new ConnectOutcome(false, detail);
        }
    }

    record Check(boolean pass, String name, String detail) {
        static Check pass(String name, String detail) {
            return new Check(true, name, detail);
        }

        static Check fail(String name, String detail) {
            return new Check(false, name, detail);
        }
    }

    record Args(String host, int port, boolean tls, String user, String password,
                String invokeOp, String invokeArg, Boolean expectStateChange,
                boolean expectFail) {

        static final String USAGE =
                "usage: Verifier --host H --port P [--tls] [--user U --password P] "
                + "[--invoke OP [--arg V]] [--expect-change] [--expect-fail]";

        static Args parse(String[] args) {
            String host = "localhost";
            int port = 1099;
            boolean tls = false;
            String user = null;
            String password = null;
            String invokeOp = null;
            String invokeArg = null;
            Boolean expectChange = null;
            boolean expectFail = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--host" -> host = require(args, ++i, "--host");
                    case "--port" -> port = Integer.parseInt(require(args, ++i, "--port"));
                    case "--tls" -> tls = true;
                    case "--user" -> user = require(args, ++i, "--user");
                    case "--password" -> password = require(args, ++i, "--password");
                    case "--invoke" -> invokeOp = require(args, ++i, "--invoke");
                    case "--arg" -> invokeArg = require(args, ++i, "--arg");
                    case "--expect-change" -> expectChange = Boolean.TRUE;
                    case "--expect-fail" -> expectFail = true;
                    default -> throw new IllegalArgumentException("unknown option: " + args[i]);
                }
            }
            if (user != null && password == null) {
                throw new IllegalArgumentException("--user requires --password");
            }
            return new Args(host, port, tls, user, password,
                    invokeOp, invokeArg, expectChange, expectFail);
        }

        private static String require(String[] args, int i, String opt) {
            if (i >= args.length) {
                throw new IllegalArgumentException(opt + " requires a value");
            }
            return args[i];
        }
    }
}
