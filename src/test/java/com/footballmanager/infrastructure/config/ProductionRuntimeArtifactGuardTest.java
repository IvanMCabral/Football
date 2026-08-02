package com.footballmanager.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class ProductionRuntimeArtifactGuardTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void logbackUsesConsoleOnlyAndDoesNotReferenceLocalFiles() throws IOException {
        String logback = read("src/main/resources/logback.xml");

        assertThat(logback).contains("ConsoleAppender");
        assertThat(logback).contains("%X{requestId:-no-request-id}");
        assertThat(logback).contains("UTF-8");
        assertThat(logback).doesNotContain("FileAppender");
        assertThat(logback).doesNotContain("RollingFileAppender");
        assertThat(logback).doesNotContain("<file>");
        assertThat(logback).doesNotContain("D:/");
        assertThat(logback).doesNotContain("C:/");
        assertThat(logback).doesNotContain("level=\"DEBUG\"");
    }

    @Test
    void applicationBindsServerAddressAndUsesOfficialRedisSslVariable() throws IOException {
        String application = read("src/main/resources/application.yaml");
        String production = read("src/main/resources/application-prod.yml");

        assertThat(application).contains("address: ${SERVER_ADDRESS:0.0.0.0}");
        assertThat(application).contains("port: ${PORT:${SERVER_PORT:8080}}");
        assertThat(application).contains("enabled: ${REDIS_SSL:false}");
        assertThat(production).contains("enabled: ${REDIS_SSL:true}");
        assertThat(application + production).doesNotContain("REDIS_SSL_ENABLED");
    }

    @Test
    void dockerfileUsesExplicitHardenedRuntimeNonRootAndCurlHealthcheck() throws IOException {
        String dockerfile = read("Dockerfile");

        assertThat(dockerfile).contains("FROM maven:3.9.11-eclipse-temurin-21@sha256:463a1849665463254b2dd56e3a5b316f1596bc93d0571065c06ea05bb48ab8f4 AS build");
        assertThat(dockerfile).contains("FROM eclipse-temurin:21.0.11_10-jre-alpine-3.23@sha256:426401268a42785be73823f6115ee0e721bdb59c12c779947b83fcead1a66645");
        assertThat(dockerfile).contains("apk add --no-cache curl ca-certificates tzdata shadow findutils");
        assertThat(dockerfile).contains("apk upgrade --no-cache");
        assertThat(dockerfile).contains("USER manager");
        assertThat(dockerfile).contains("curl --fail --silent --show-error --max-time 3");
        assertThat(dockerfile).contains("http://127.0.0.1:${PORT}/api/v1/health/liveness");
        assertThat(dockerfile).doesNotContain("latest");
        assertThat(dockerfile).doesNotContain("wget");
    }

    @Test
    void dockerSmokeSupplyChainAndArtifactHygieneAreFailClosed() throws IOException {
        String dockerfile = read("Dockerfile");
        String workflow = read(".github/workflows/pb12-docker-smoke.yml");
        String runner = read("tools/run-pb12-docker-smoke.sh");
        String manifestBuilder = read("tools/build-pb12-artifact-manifest.py");
        String manifestVerifier = read("tools/verify-pb12-artifact-manifest.py");
        String manifestTests = read("tools/test-pb12-artifact-manifest.py");

        assertThat(dockerfile).contains("@sha256:");
        assertThat(workflow).contains("actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683");
        assertThat(workflow).contains("anchore/sbom-action@d94f46e13c6c62f59525ac9a1e147a99dc0b9bf5");
        assertThat(workflow).contains("aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25");
        assertThat(workflow).contains("actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02");
        assertThat(workflow).doesNotContain("uses: actions/checkout@v");
        assertThat(workflow).doesNotContain("docker system prune");
        assertThat(workflow).contains("pb12-artifact-manifest.json");
        assertThat(workflow).contains("tools/build-pb12-artifact-manifest.py");
        assertThat(workflow).contains("tools/verify-pb12-artifact-manifest.py");
        assertThat(workflow).contains("artifactInventoryExact");
        assertThat(workflow).contains("manifestMetadataVerified");
        assertThat(workflow).contains("path: ${{ runner.temp }}/pb12-docker-smoke-${{ github.run_id }}/artifact/");
        assertThat(workflow).doesNotContain("'containsSecrets': False");
        assertThat(workflow).doesNotContain("containsSecrets: False");
        assertThat(workflow).contains("finalArtifactSecretScanPassed");
        assertThat(runner).contains("authTempCleanupVerified");
        assertThat(runner).contains("finalArtifactSecretScanPassed");
        assertThat(runner).contains("PB12_FORCE_AUTH_TMP_DELETE_FAILURE");
        assertThat(manifestBuilder).contains("containsSecrets");
        assertThat(manifestBuilder).contains("matches != 0 or not passed");
        assertThat(manifestBuilder).contains("metadataFiles");
        assertThat(manifestVerifier).contains("artifact inventory is not exact");
        assertThat(manifestVerifier).contains("manifest canonical hash mismatch");
        assertThat(manifestVerifier).contains("containsSecrets mismatch");
        assertThat(manifestTests).contains("JWT");
        assertThat(manifestTests).contains("SHA altered");
        assertThat(manifestTests).contains("file added after manifest");
    }

    @Test
    void dockerContextExcludesRuntimeEvidenceAndFrontendSources() throws IOException {
        String dockerignore = read(".dockerignore");

        assertThat(dockerignore).contains("docs/");
        assertThat(dockerignore).contains("front-ciber/");
        assertThat(dockerignore).contains("logs/");
        assertThat(dockerignore).contains("backups/");
        assertThat(dockerignore).contains("target/surefire-reports/");
        assertThat(dockerignore).contains("*.dump");
        assertThat(dockerignore).contains(".env");
    }

    @Test
    void productionJarSmokeRequiresGracefulShutdownAndMandatoryCareer() throws IOException {
        String script = read("tools/run-production-jar-smoke.ps1");
        String helper = read("tools/GracefulProcessGroupRunner.cs");

        assertThat(helper).contains("CREATE_NEW_PROCESS_GROUP");
        assertThat(helper).contains("CREATE_NEW_CONSOLE");
        assertThat(helper).contains("AttachConsole(processInfo.dwProcessId)");
        assertThat(helper).contains("GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0)");
        assertThat(helper).contains("forceKillUsed");
        assertThat(script).contains("if (-not $careerResponse)");
        assertThat(script).contains("Minimal career creation returned an empty response.");
        assertThat(script).contains("Expected exactly 1 successful Flyway migration after run 2");
        assertThat(script).contains("Stop-AppRunGracefully $run1");
        assertThat(script).contains("Stop-AppRunGracefully $run2");
        assertThat(script).contains("forceKillUsed");
        assertThat(script).contains("residualProcesses");
        assertThat(script).contains("residualPorts");
        assertThat(script).contains("Remove-SmokeWorkspace $work");
        assertThat(script).contains("[System.IO.Directory]::Delete($full, $true)");
        assertThat(script).doesNotContain("Stop-Process -Id $appProcess.Id");
        assertThat(script).doesNotContain("careerCreated = $false");
    }

    @Test
    void productionJarSmokeCoversPortAndServerPortModes() throws IOException {
        String script = read("tools/run-production-jar-smoke.ps1");
        String application = read("src/main/resources/application.yaml");

        assertThat(application).contains("port: ${PORT:${SERVER_PORT:8080}}");
        assertThat(script).contains("Start-AppRun 1 'PORT'");
        assertThat(script).contains("Start-AppRun 2 'SERVER_PORT'");
        assertThat(script).contains("$env:SERVER_ADDRESS = '0.0.0.0'");
        assertThat(script).contains("Remove-Item Env:SERVER_PORT");
        assertThat(script).contains("Remove-Item Env:PORT");
    }

    @Test
    void productionJarSmokeLifecycleTestModesFailClosedThroughExecutableRunner() throws Exception {
        String[] modes = {
            "postgres-stop-fails",
            "redis-stop-fails",
            "helper-fails-after-java",
            "helper-dead-recovery-signal-fails",
            "safe-summary-delete-fails",
            "workspace-delete-fails",
            "marker-order-invalid"
        };

        for (String mode : modes) {
            Process process = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                ROOT.resolve("tools/run-production-jar-smoke.ps1").toString(),
                "-SkipBuild",
                "-LifecycleTestMode",
                mode)
                .directory(ROOT.toFile())
                .redirectErrorStream(true)
                .start();

            boolean finished = process.waitFor(Duration.ofMinutes(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertThat(finished).as(mode).isTrue();
            assertThat(process.exitValue()).as(mode).isNotZero();
            assertThat(output).as(mode).contains("\"status\":\"FAIL\"");
            assertThat(output).as(mode).contains("\"lifecycleTestMode\":\"" + mode + "\"");
            assertThat(output).as(mode).contains("\"residualProcesses\":0");
            assertThat(output).as(mode).contains("\"residualPorts\":0");
            assertThat(output).as(mode).contains("\"historicalSafeSummaryCountAfter\":0");
            if (mode.equals("helper-fails-after-java")) {
                assertThat(output).contains("\"originalHelperExited\":true");
                assertThat(output).contains("\"javaWasAliveAfterHelperExit\":true");
                assertThat(output).contains("\"javaPidRecovered\":true");
                assertThat(output).contains("\"recoverySignalAttempted\":true");
                assertThat(output).contains("\"recoverySignalAttemptedMode\":\"NONE\"");
                assertThat(output).contains("\"recoveryError\":\"AttachConsole to existing Java process failed\"");
                assertThat(output).contains("\"javaGracefulRecoverySucceeded\":false");
                assertThat(output).contains("\"javaForceKillUsedRun1\":");
                assertThat(output).contains("\"forceKillUsed\":");
            }
            if (mode.equals("helper-dead-recovery-signal-fails")) {
                assertThat(output).contains("\"originalHelperExited\":true");
                assertThat(output).contains("\"javaWasAliveAfterHelperExit\":true");
                assertThat(output).contains("\"javaPidRecovered\":true");
                assertThat(output).contains("\"recoverySignalAttempted\":true");
                assertThat(output).contains("\"javaGracefulRecoverySucceeded\":false");
                assertThat(output).contains("\"javaForceKillUsedRun1\":true");
                assertThat(output).contains("\"forceKillUsed\":true");
            }
            if (mode.equals("safe-summary-delete-fails")) {
                assertThat(output).contains("\"currentRunSafeSummaryExists\":true");
                assertThat(output).contains("\"safeSummaryCleanupVerified\":false");
            }
        }
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
