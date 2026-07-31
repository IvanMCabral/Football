package com.footballmanager.adapters.in.web.common;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class PublicErrorMessageResolver {

    private final Environment environment;

    public PublicErrorMessageResolver(Environment environment) {
        this.environment = environment;
    }

    public String clientMessage(Throwable error, String productionMessage) {
        if (isProduction()) {
            return productionMessage;
        }
        String message = error.getMessage();
        return message == null || message.isBlank() ? productionMessage : message;
    }

    public boolean isProduction() {
        return Arrays.stream(environment.getActiveProfiles())
            .anyMatch("prod"::equalsIgnoreCase);
    }
}
