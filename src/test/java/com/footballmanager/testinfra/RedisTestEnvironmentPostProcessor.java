package com.footballmanager.testinfra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisTestEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile RedisProcess redisProcess;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("test"))) {
            return;
        }
        if ("false".equalsIgnoreCase(environment.getProperty("manager.test.redis.bootstrap", "true"))) {
            return;
        }
        RedisProcess process = ensureRedis();
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.data.redis.host", "127.0.0.1");
        properties.put("spring.data.redis.port", process.port());
        properties.put("spring.data.redis.password", process.password());
        properties.put("spring.data.redis.database", "15");
        properties.put("spring.data.redis.ssl.enabled", "false");
        environment.getPropertySources().addFirst(
            new MapPropertySource("managerRedisTestBootstrap", properties));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private static RedisProcess ensureRedis() {
        RedisProcess existing = redisProcess;
        if (existing != null && existing.process().isAlive()) {
            return existing;
        }
        synchronized (RedisTestEnvironmentPostProcessor.class) {
            existing = redisProcess;
            if (existing != null && existing.process().isAlive()) {
                return existing;
            }
            redisProcess = startRedis();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Process process = redisProcess != null ? redisProcess.process() : null;
                if (process != null && process.isAlive()) {
                    process.destroy();
                    try {
                        if (!process.waitFor(3, TimeUnit.SECONDS)) {
                            process.destroyForcibly();
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        process.destroyForcibly();
                    }
                }
            }, "manager-test-redis-shutdown"));
            return redisProcess;
        }
    }

    private static RedisProcess startRedis() {
        int port = freePort();
        String password = ephemeralPassword();
        ProcessBuilder builder = new ProcessBuilder(
            "redis-server",
            "--bind", "127.0.0.1",
            "--port", String.valueOf(port),
            "--save", "",
            "--appendonly", "no",
            "--databases", "16",
            "--protected-mode", "yes",
            "--requirepass", password);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            waitForRedisToStayAlive(process);
            return new RedisProcess(process, port, password);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Redis test bootstrap could not start redis-server from PATH. "
                    + "Install redis-server or set manager.test.redis.bootstrap=false and provide Redis test properties.",
                e);
        }
    }

    private static void waitForRedisToStayAlive(Process process) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting Redis test process", interrupted);
        }
        if (!process.isAlive()) {
            throw new IllegalStateException("Redis test bootstrap process exited during startup");
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not allocate Redis test port", e);
        }
    }

    private static String ephemeralPassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record RedisProcess(Process process, int port, String password) {
    }
}
