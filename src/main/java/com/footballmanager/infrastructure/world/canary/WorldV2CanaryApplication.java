package com.footballmanager.infrastructure.world.canary;

import com.footballmanager.FootballManagerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Explicit process boundary for the one-shot runner. It is never the normal
 * application entry point and is only used when the dedicated canary profile
 * and enable property are supplied by the operator.
 */
public final class WorldV2CanaryApplication {

    private WorldV2CanaryApplication() { }

    public static void main(String[] args) {
        if (!activationPresent(args)) {
            System.exit(2);
            return;
        }
        ConfigurableApplicationContext context = null;
        int exitCode = 1;
        try {
            context = SpringApplication.run(FootballManagerApplication.class, args);
            exitCode = SpringApplication.exit(context);
        } catch (WorldV2CanaryRunner.WorldV2CanaryExecutionException ignored) {
            // The runner has already emitted a sanitized structured result.
            exitCode = 1;
        } finally {
            if (context != null && context.isActive()) context.close();
        }
        System.exit(exitCode);
    }

    private static boolean activationPresent(String[] args) {
        boolean profile = value(args, "spring.profiles.active")
                .map(value -> java.util.Arrays.stream(value.split(","))
                .anyMatch("world-v2-canary"::equals)).orElseGet(() ->
                        "world-v2-canary".equals(System.getenv("SPRING_PROFILES_ACTIVE")));
        String enabled = value(args, "world.v2.canary.enabled")
                .orElseGet(() -> System.getenv("WORLD_V2_CANARY_ENABLED"));
        return profile && "true".equalsIgnoreCase(enabled);
    }

    private static java.util.Optional<String> value(String[] args, String key) {
        String prefix = "--" + key + "=";
        return java.util.Arrays.stream(args)
                .filter(arg -> arg.startsWith(prefix))
                .map(arg -> arg.substring(prefix.length()))
                .findFirst();
    }
}
