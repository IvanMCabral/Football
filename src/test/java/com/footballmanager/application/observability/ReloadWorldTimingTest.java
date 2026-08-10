package com.footballmanager.application.observability;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReloadWorldTimingTest {

    @Test
    void writesOnlySanitizedTimingAndStructuralHeaders() {
        ReloadWorldTiming timing = new ReloadWorldTiming();
        timing.countWorld(3, 70, 770);
        timing.serializedBytes(123_456);
        timing.measure("statusQueryMs", Mono.just("ok")).block();

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        timing.writeHeaders(response);

        assertEquals("3", headers.getFirst("X-Reload-League-Count"));
        assertEquals("70", headers.getFirst("X-Reload-Team-Count"));
        assertEquals("770", headers.getFirst("X-Reload-Player-Count"));
        assertEquals("123456", headers.getFirst("X-Reload-Serialized-Bytes"));
        assertTrue(Long.parseLong(headers.getFirst("X-Reload-Status-Ms")) >= 0);
        assertNull(headers.getFirst("X-Reload-User-Id"));
        assertNull(headers.getFirst("X-Reload-Redis-Key"));
    }

    @Test
    void recordsStageOnErrorAndKeepsPublisherFailure() {
        ReloadWorldTiming timing = new ReloadWorldTiming();

        RuntimeException failure = new RuntimeException("expected");
        RuntimeException observed = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> timing.measure("redisSaveMs", Mono.<String>error(failure)).block());

        assertEquals(failure, observed);
        assertEquals(0L, timing.snapshot().get("redisSaveMs"));
    }
}
