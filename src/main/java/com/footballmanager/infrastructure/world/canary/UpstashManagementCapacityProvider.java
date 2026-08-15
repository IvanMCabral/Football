package com.footballmanager.infrastructure.world.canary;

import com.fasterxml.jackson.databind.JsonNode;
import com.footballmanager.application.service.world.canary.WorldV2CanaryCapacityProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Read-only Upstash Management API adapter; credentials are runtime-only. */
@Component
@Profile("world-v2-canary")
@ConditionalOnProperty(name = "world.v2.canary.enabled", havingValue = "true")
public final class UpstashManagementCapacityProvider implements WorldV2CanaryCapacityProvider {

    private final WebClient client;
    private final WorldV2CanaryProperties properties;
    private final String email;
    private final String apiKey;

    public UpstashManagementCapacityProvider(WebClient.Builder builder,
                                             WorldV2CanaryProperties properties,
                                             @org.springframework.beans.factory.annotation.Value("${UPSTASH_EMAIL:}") String email,
                                             @org.springframework.beans.factory.annotation.Value("${UPSTASH_API_KEY:}") String apiKey) {
        this.client = builder.baseUrl("https://api.upstash.com").build();
        this.properties = properties;
        this.email = email;
        this.apiKey = apiKey;
    }

    @Override
    public Mono<CapacitySample> sample() {
        if (blank(email) || blank(apiKey) || blank(properties.getDatabaseId())) {
            return Mono.error(new ProviderCapacityException(FailureKind.AUTHENTICATION,
                    "provider credentials or database identity are unavailable", null));
        }
        return get("/v2/redis/databases")
                .then(get("/v2/redis/stats/" + properties.getDatabaseId()))
                .map(this::toSample)
                .onErrorMap(WebClientResponseException.Unauthorized.class, error ->
                        new ProviderCapacityException(FailureKind.AUTHENTICATION,
                                "provider authentication failed", error))
                .onErrorMap(error -> error instanceof ProviderCapacityException
                        ? error : new ProviderCapacityException(FailureKind.UNAVAILABLE,
                        "provider accounting unavailable", error));
    }

    private Mono<JsonNode> get(String path) {
        return client.get().uri(path)
                .headers(headers -> headers.setBasicAuth(email, apiKey))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.createException().flatMap(Mono::error))
                .bodyToMono(JsonNode.class)
                .onErrorMap(DecodingException.class, error -> new ProviderCapacityException(
                        FailureKind.MALFORMED, "provider accounting response is malformed", error))
                .switchIfEmpty(Mono.just(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()));
    }

    private CapacitySample toSample(JsonNode json) {
        return parseCurrentStorage(json);
    }

    static CapacitySample parseCurrentStorage(JsonNode json) {
        JsonNode value = json.path("current_storage");
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new ProviderCapacityException(FailureKind.MALFORMED,
                    "provider accounting response has invalid current_storage", null);
        }
        return new CapacitySample(value.longValue());
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
