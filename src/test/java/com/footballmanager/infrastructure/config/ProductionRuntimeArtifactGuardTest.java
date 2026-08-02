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
    void dockerfileUsesExplicitDebianRuntimeNonRootAndCurlHealthcheck() throws IOException {
        String dockerfile = read("Dockerfile");

        assertThat(dockerfile).contains("FROM maven:3.9.11-eclipse-temurin-21 AS build");
        assertThat(dockerfile).contains("FROM eclipse-temurin:21.0.8_9-jre-jammy");
        assertThat(dockerfile).contains("apt-get install -y --no-install-recommends curl ca-certificates tzdata");
        assertThat(dockerfile).contains("USER manager");
        assertThat(dockerfile).contains("curl --fail --silent --show-error --max-time 3");
        assertThat(dockerfile).contains("http://127.0.0.1:${PORT}/api/v1/health/liveness");
        assertThat(dockerfile).doesNotContain("latest");
        assertThat(dockerfile).doesNotContain("wget");
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
    void productionJarSmokeLifecycleSelfTestExercisesNegativeCases() throws Exception {
        Process process = new ProcessBuilder(
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            ROOT.resolve("tools/run-production-jar-smoke.ps1").toString(),
            "-LifecycleSelfTest")
            .directory(ROOT.toFile())
            .redirectErrorStream(true)
            .start();

        boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(finished).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output).contains("\"status\":\"PASS\"");
        assertThat(output).contains("\"negativeCases\":5");
        assertThat(output).contains("\"postgresStopFailure\":\"FAIL\"");
        assertThat(output).contains("\"redisStopFailure\":\"FAIL\"");
        assertThat(output).contains("\"helperFailureAfterJava\":\"FAIL\"");
        assertThat(output).contains("\"workspaceDeleteFailure\":\"FAIL\"");
        assertThat(output).contains("\"markerOrderInvalid\":\"FAIL\"");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
