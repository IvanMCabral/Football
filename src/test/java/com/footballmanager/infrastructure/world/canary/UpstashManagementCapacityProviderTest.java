package com.footballmanager.infrastructure.world.canary;

import com.footballmanager.application.service.world.canary.WorldV2CanaryCapacityProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpstashManagementCapacityProviderTest {

    @Test
    void validCurrentStorageIsAcceptedAndMonthlyFieldHasNoAuthority() {
        assertThat(sample("{\"current_storage\":265736968,\"total_monthly_storage\":1}")
                .currentStorageBytes()).isEqualTo(265_736_968L);
        assertThat(sample("{\"current_storage\":265736969,\"total_monthly_storage\":1}")
                .currentStorageBytes()).isEqualTo(265_736_969L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"total_monthly_storage\":1}",
            "{\"current_storage\":null,\"total_monthly_storage\":1}",
            "{\"current_storage\":\"265736968\",\"total_monthly_storage\":1}",
            "{\"current_storage\":1.5,\"total_monthly_storage\":1}",
            "{\"current_storage\":NaN,\"total_monthly_storage\":1}",
            "{\"current_storage\":-1}",
            "{\"current_storage\":9223372036854775808}"
    })
    void invalidCurrentStorageFailsClosedEvenWhenMonthlyFieldLooksValid(String response) {
        assertThatThrownBy(() -> sample(response))
                .isInstanceOf(WorldV2CanaryCapacityProvider.ProviderCapacityException.class)
                .satisfies(error -> assertThat(((WorldV2CanaryCapacityProvider.ProviderCapacityException) error)
                        .kind()).isEqualTo(WorldV2CanaryCapacityProvider.FailureKind.MALFORMED));
    }

    @Test
    void malformedJsonFailsClosedAsMalformedAccounting() {
        assertThatThrownBy(() -> sample("{not-json"))
                .isInstanceOf(WorldV2CanaryCapacityProvider.ProviderCapacityException.class)
                .satisfies(error -> assertThat(((WorldV2CanaryCapacityProvider.ProviderCapacityException) error)
                        .kind()).isEqualTo(WorldV2CanaryCapacityProvider.FailureKind.MALFORMED));
    }

    private static WorldV2CanaryCapacityProvider.CapacitySample sample(String statsBody) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            String body = request.url().getPath().contains("/stats/") ? statsBody : "[]";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(body)
                    .build());
        });
        WorldV2CanaryProperties properties = new WorldV2CanaryProperties();
        properties.setDatabaseId("synthetic-database-id");
        return new UpstashManagementCapacityProvider(builder, properties,
                "synthetic@example.invalid", "synthetic-test-key").sample().block();
    }
}
