package com.footballmanager.adapters.out.redis;

import com.footballmanager.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPreparedMigrationSeparateJvmIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LettuceConnectionFactory redisConnectionFactory;

    @Test
    void preparedStateSurvivesProcessExitAndRecoversInAnIndependentJvmContext() throws Exception {
        var configuration = redisConnectionFactory.getStandaloneConfiguration();
        RedisPassword password = configuration.getPassword();
        String credential = new String(password.get());
        UUID owner = UUID.nameUUIDFromBytes("world-prepared-separate-jvm".getBytes(StandardCharsets.UTF_8));

        ProcessResult processA = run("prepare", configuration.getHostName(), configuration.getPort(),
                configuration.getDatabase(), owner, credential);
        assertEquals(0, processA.exitCode(), processA.safeOutput());
        assertTrue(processA.safeOutput().contains("PREPARED_WRITTEN_BY_PRODUCT_ORCHESTRATOR"));

        ProcessResult processB = run("recover", configuration.getHostName(), configuration.getPort(),
                configuration.getDatabase(), owner, credential);
        assertEquals(0, processB.exitCode(), processB.safeOutput());
        assertTrue(processB.safeOutput().contains("RECOVERY_OK"));
        System.out.printf("[WORLD-PREPARED-TWO-JVM] processA=%d productPath=true processB=%d recovery=true%n",
                processA.pid(), processB.pid());
    }

    private ProcessResult run(String mode, String host, int port, int database, UUID owner, String password)
            throws Exception {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>(List.of(javaExecutable, "-cp", classpath,
                WorldPreparedMigrationProcessHarness.class.getName(), mode, host,
                Integer.toString(port), Integer.toString(database), owner.toString()));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("MANAGER_SEPARATE_JVM_REDIS_PASSWORD", password);
        Process process = builder.start();
        long pid = process.pid();
        boolean exited = process.waitFor(Duration.ofSeconds(45).toMillis(), TimeUnit.MILLISECONDS);
        if (!exited) {
            process.destroyForcibly();
            throw new IllegalStateException("separate JVM migration harness timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(pid, process.exitValue(), sanitize(output));
    }

    private static String sanitize(String output) {
        if (output == null) return "";
        return output.lines()
                .filter(line -> line.contains("PREPARED_WRITTEN_BY_PRODUCT_ORCHESTRATOR")
                        || line.contains("RECOVERY_OK")
                        || line.startsWith("Exception") || line.startsWith("Caused by")
                        || line.startsWith("\tat "))
                .reduce("", (left, right) -> left + right + System.lineSeparator());
    }

    private record ProcessResult(long pid, int exitCode, String safeOutput) { }
}
