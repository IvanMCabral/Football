package com.footballmanager.testinfra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PostgresTestEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile PostgresProcess postgresProcess;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if ("false".equalsIgnoreCase(environment.getProperty("manager.test.postgres.bootstrap", "true"))) {
            return;
        }

        PostgresProcess process = ensureRunning();
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", process.jdbcUrl());
        properties.put("spring.datasource.username", process.username());
        properties.put("spring.datasource.password", process.password());
        properties.put("spring.r2dbc.url", process.r2dbcUrl());
        properties.put("spring.r2dbc.username", process.username());
        properties.put("spring.r2dbc.password", process.password());
        properties.put("spring.flyway.enabled", "true");
        properties.put("spring.flyway.url", process.jdbcUrl());
        properties.put("spring.flyway.user", process.username());
        properties.put("spring.flyway.password", process.password());
        properties.put("DB_HOST", "127.0.0.1");
        properties.put("DB_PORT", String.valueOf(process.port()));
        properties.put("DB_NAME_TEST", "postgres");
        properties.put("DB_USER", process.username());
        properties.put("DB_PASSWORD", process.password());

        environment.getPropertySources().addFirst(
            new MapPropertySource("managerPostgresTestBootstrap", properties));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    public static PostgresProcess ensureRunning() {
        PostgresProcess existing = postgresProcess;
        if (existing != null && existing.process().isAlive()) {
            return existing;
        }
        synchronized (PostgresTestEnvironmentPostProcessor.class) {
            existing = postgresProcess;
            if (existing != null && existing.process().isAlive()) {
                return existing;
            }
            postgresProcess = startPostgres();
            Runtime.getRuntime().addShutdownHook(new Thread(PostgresTestEnvironmentPostProcessor::stopPostgres,
                "manager-test-postgres-shutdown"));
            return postgresProcess;
        }
    }

    private static PostgresProcess startPostgres() {
        try {
            int port = freePort();
            String username = "manager_test";
            String password = ephemeralPassword();
            Path baseDir = Files.createTempDirectory("manager-test-postgres-");
            Path dataDir = baseDir.resolve("data");
            Path passwordFile = baseDir.resolve("pwfile.txt");
            Path logFile = baseDir.resolve("postgres.log");
            Files.writeString(passwordFile, password, StandardCharsets.UTF_8);

            runChecked(new ProcessBuilder(
                "initdb",
                "-D", dataDir.toString(),
                "-U", username,
                "-A", "scram-sha-256",
                "--pwfile", passwordFile.toString(),
                "-E", "UTF8"
            ), "initdb");

            Files.writeString(dataDir.resolve("postgresql.conf"),
                System.lineSeparator()
                    + "listen_addresses = '127.0.0.1'" + System.lineSeparator()
                    + "fsync = off" + System.lineSeparator()
                    + "synchronous_commit = off" + System.lineSeparator()
                    + "full_page_writes = off" + System.lineSeparator(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);

            ProcessBuilder postgres = new ProcessBuilder(
                "postgres",
                "-D", dataDir.toString(),
                "-h", "127.0.0.1",
                "-p", String.valueOf(port)
            );
            postgres.redirectErrorStream(true);
            postgres.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            Process process = postgres.start();

            PostgresProcess postgresProcess = new PostgresProcess(
                process,
                port,
                username,
                password,
                baseDir,
                "jdbc:postgresql://127.0.0.1:" + port + "/postgres",
                "r2dbc:postgresql://127.0.0.1:" + port + "/postgres"
            );
            waitForJdbc(postgresProcess);
            return postgresProcess;
        } catch (IOException e) {
            throw new IllegalStateException(
                "PostgreSQL test bootstrap could not start postgres/initdb from PATH. "
                    + "Install PostgreSQL binaries or set manager.test.postgres.bootstrap=false "
                    + "and provide isolated test database properties.",
                e);
        }
    }

    private static void runChecked(ProcessBuilder builder, String name) throws IOException {
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                throw new IllegalStateException(name + " failed while preparing PostgreSQL test runtime");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Interrupted while running " + name, interrupted);
        }
    }

    private static void waitForJdbc(PostgresProcess process) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        SQLException lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            if (!process.process().isAlive()) {
                throw new IllegalStateException("PostgreSQL test process exited during startup");
            }
            try (Connection ignored = DriverManager.getConnection(
                process.jdbcUrl(), process.username(), process.password())) {
                return;
            } catch (SQLException e) {
                lastFailure = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for PostgreSQL test runtime", interrupted);
                }
            }
        }
        throw new IllegalStateException("PostgreSQL test runtime did not become ready", lastFailure);
    }

    private static void stopPostgres() {
        PostgresProcess process = postgresProcess;
        if (process == null) {
            return;
        }
        Process running = process.process();
        if (running.isAlive()) {
            running.destroy();
            try {
                if (!running.waitFor(5, TimeUnit.SECONDS)) {
                    running.destroyForcibly();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                running.destroyForcibly();
            }
        }
        try {
            deleteRecursively(process.baseDir());
        } catch (IOException ignored) {
            // Temporary test directories are best-effort cleanup on Windows.
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                .forEach(current -> {
                    try {
                        Files.deleteIfExists(current);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not allocate PostgreSQL test port", e);
        }
    }

    private static String ephemeralPassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record PostgresProcess(
        Process process,
        int port,
        String username,
        String password,
        Path baseDir,
        String jdbcUrl,
        String r2dbcUrl
    ) {
    }
}
