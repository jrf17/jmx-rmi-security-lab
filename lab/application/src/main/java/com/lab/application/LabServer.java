package com.lab.application;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.rmi.ssl.SslRMIClientSocketFactory;

/**
 * Standalone JMX/RMI management endpoint for the laboratory target.
 *
 * <p>Configuration is read from system properties so that a single artifact
 * can run in either the deliberately vulnerable or the hardened profile:</p>
 *
 * <ul>
 *   <li>{@code lab.mode} — {@code vulnerable} (no auth, no TLS) or
 *       {@code hardened} (auth + TLS). Default {@code hardened}.</li>
 *   <li>{@code lab.hostname} — hostname advertised in RMI stubs. Default
 *       {@code localhost}.</li>
 *   <li>{@code javax.net.ssl.keyStore} / {@code keyStorePassword} — server
 *       identity (hardened only).</li>
 *   <li>{@code javax.net.ssl.trustStore} / {@code trustStorePassword} — CA
 *       trust for mutual-TLS client certs (hardened only).</li>
 *   <li>{@code lab.password.file} / {@code lab.access.file} — JMX credentials
 *       and authorization (hardened only).</li>
 * </ul>
 *
 * <p>The RMI registry listens on a fixed port ({@link #REGISTRY_PORT}) and the
 * JMX RMI connector server on a fixed port ({@link #CONNECTOR_PORT}) so that
 * container-network routing is deterministic and no random ports are used.</p>
 */
public final class LabServer {

    public static final int REGISTRY_PORT = 1099;
    public static final int CONNECTOR_PORT = 9090;
    public static final String JMX_PATH = "jmxrmi";
    public static final String MBEAN_NAME = "com.lab:type=MaintenanceMode";

    private LabServer() {
    }

    public static void main(String[] args) throws Exception {
        applyCliArgs(args);
        Config cfg = Config.fromSystemProperties();
        System.out.printf(
                "[lab] starting JMX/RMI target mode=%s hostname=%s auth=%s tls=%s%n",
                cfg.mode(), cfg.hostname(), cfg.auth(), cfg.tls());
        System.out.flush();

        System.setProperty("java.rmi.server.hostname", cfg.hostname());

        if (cfg.tls()) {
            System.setProperty("javax.net.ssl.needClientAuth", "true");
        }

        MBeanServer mbs = MBeanServerFactory.createMBeanServer();
        MaintenanceMode bean = new MaintenanceMode();
        mbs.registerMBean(bean, new ObjectName(MBEAN_NAME));

        RMIClientSocketFactory csf = cfg.tls() ? new SslRMIClientSocketFactory() : null;
        // The registry is always plain JRMP in both modes: the connector
        // server binds its stub into it over a plain channel, and only the
        // connector's RMI server (port 9090) is TLS-protected in hardened
        // mode. A TLS registry would break the connector's internal bind.
        RMIServerSocketFactory registrySsf = new PlainServerSocketFactory();
        RMIServerSocketFactory ssf = cfg.tls()
                ? new FixedPortSslServerSocketFactory()
                : new FixedPortServerSocketFactory();

        LocateRegistry.createRegistry(REGISTRY_PORT, null, registrySsf);

        Map<String, Object> env = new HashMap<>();
        if (cfg.auth()) {
            env.put("jmx.remote.x.password.file", cfg.passwordFile());
            env.put("jmx.remote.x.access.file", cfg.accessFile());
        }
        env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE,
                cfg.tls() ? new SslRMIClientSocketFactory() : null);
        env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, ssf);

        JMXServiceURL url = new JMXServiceURL(
                "service:jmx:rmi:///jndi/rmi://" + cfg.hostname()
                        + ":" + REGISTRY_PORT + "/" + JMX_PATH);

        JMXConnectorServer connector =
                JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);
        connector.start();

        System.out.printf(
                "[lab] MBean registered: %s%n"
                + "[lab] RMI registry listening on %d%n"
                + "[lab] JMX connector listening on %d%n"
                + "[lab] auth=%s tls=%s%n",
                MBEAN_NAME, REGISTRY_PORT, CONNECTOR_PORT,
                cfg.auth() ? "enabled" : "DISABLED",
                cfg.tls() ? "enabled" : "DISABLED");
        System.out.flush();

        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                connector.stop();
                mbs.unregisterMBean(new ObjectName(MBEAN_NAME));
            } catch (Exception e) {
                System.err.println("[lab] shutdown cleanup error: " + e.getMessage());
            }
            stop.countDown();
        }, "lab-shutdown"));
        stop.await();
    }

    /**
     * Translates {@code --key value} CLI arguments into system properties so
     * the same config path serves both {@code java -jar} and container runs.
     */
    private static void applyCliArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mode" -> System.setProperty("lab.mode", requireArg(args, ++i, "--mode"));
                case "--hostname" -> System.setProperty("lab.hostname", requireArg(args, ++i, "--hostname"));
                case "--password-file" -> System.setProperty("lab.password.file", requireArg(args, ++i, "--password-file"));
                case "--access-file" -> System.setProperty("lab.access.file", requireArg(args, ++i, "--access-file"));
                default -> throw new IllegalArgumentException(
                        "unknown option: " + args[i] + " (expected --mode/--hostname/--password-file/--access-file)");
            }
        }
    }

    private static String requireArg(String[] args, int i, String opt) {
        if (i >= args.length) {
            throw new IllegalArgumentException(opt + " requires a value");
        }
        return args[i];
    }

    /** Parsed, validated server configuration. */
    record Config(String mode, String hostname, boolean auth, boolean tls,
                  String passwordFile, String accessFile) {

        static Config fromSystemProperties() {
            String mode = System.getProperty("lab.mode", "hardened");
            boolean vulnerable;
            boolean hardened;
            switch (mode.toLowerCase()) {
                case "vulnerable" -> { vulnerable = true; hardened = false; }
                case "hardened" -> { vulnerable = false; hardened = true; }
                default -> throw new IllegalArgumentException(
                        "lab.mode must be 'vulnerable' or 'hardened', got: " + mode);
            }
            String hostname = System.getProperty("lab.hostname", "localhost");
            if (hostname == null || hostname.isBlank()) {
                throw new IllegalArgumentException("lab.hostname must not be blank");
            }
            String passwordFile = System.getProperty("lab.password.file");
            String accessFile = System.getProperty("lab.access.file");
            if (hardened && (passwordFile == null || accessFile == null)) {
                throw new IllegalArgumentException(
                        "hardened mode requires lab.password.file and lab.access.file");
            }
            return new Config(mode.toLowerCase(), hostname, hardened, hardened,
                    passwordFile, accessFile);
        }
    }

    /**
     * Binds to whatever port RMI requests (used by the registry, which must
     * listen on 1099 and must not force a second fixed bind).
     */
    static final class PlainServerSocketFactory implements RMIServerSocketFactory {
        @Override
        public ServerSocket createServerSocket(int port) throws IOException {
            return new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"));
        }
    }

    /**
     * Binds an {@code RMIServerSocket} to a fixed port rather than an
     * ephemeral one, so the connector's advertised address is deterministic.
     */
    static final class FixedPortServerSocketFactory implements RMIServerSocketFactory {
        @Override
        public ServerSocket createServerSocket(int port) throws IOException {
            return new ServerSocket(CONNECTOR_PORT, 0, InetAddress.getByName("0.0.0.0"));
        }
    }

    /**
     * Same fixed-port binding, but returns an {@link SSLServerSocket} so the
     * connector speaks TLS on a deterministic port in hardened mode.
     */
    static final class FixedPortSslServerSocketFactory implements RMIServerSocketFactory {
        @Override
        public ServerSocket createServerSocket(int port) throws IOException {
            SSLServerSocket ssl = (SSLServerSocket) SSLServerSocketFactory.getDefault()
                    .createServerSocket(CONNECTOR_PORT, 0, InetAddress.getByName("0.0.0.0"));
            return ssl;
        }
    }
}
