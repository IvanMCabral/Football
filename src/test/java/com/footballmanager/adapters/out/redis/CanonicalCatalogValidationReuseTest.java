package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.adapters.out.redis.RedisWorldRepository.WorldStorageFormatException;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CanonicalCatalogValidationReuseTest {
    private final ObjectMapper mapper = spy(new ObjectMapper().findAndRegisterModules());
    @SuppressWarnings("unchecked")
    private final RedisWorldRepository repository = new RedisWorldRepository(mock(ReactiveRedisTemplate.class), mapper);

    private WorldSnapshot catalog() {
        WorldSnapshot catalog = new WorldSnapshot();
        catalog.setLeagues(List.of(WorldLeague.fromRealLeague(UUID.randomUUID(), "Original", "ARG", 1)));
        return catalog;
    }

    private void validate(String key, String fingerprint, String json) {
        Mono<Void> validation = ReflectionTestUtils.invokeMethod(repository, "validateCatalog", key, fingerprint, json);
        validation.block();
    }

    @Test
    void identicalBytesReuseOnlyTheExpensiveSemanticValidation() throws Exception {
        var catalog = catalog();
        String fingerprint = new CanonicalWorldCatalogFingerprint(mapper).fingerprint(catalog);
        String json = mapper.writeValueAsString(catalog);
        String key = "world-catalog:v2:" + fingerprint;
        validate(key, fingerprint, json);
        validate(key, fingerprint, json);
        verify(mapper, times(1)).readValue(json, WorldSnapshot.class);
    }

    @Test
    void changedBytesCannotReusePreviouslyValidatedIdentity() throws Exception {
        var catalog = catalog();
        String fingerprint = new CanonicalWorldCatalogFingerprint(mapper).fingerprint(catalog);
        String key = "world-catalog:v2:" + fingerprint;
        validate(key, fingerprint, mapper.writeValueAsString(catalog));
        catalog.getLeagues().getFirst().setName("Changed");
        String changed = mapper.writeValueAsString(catalog);
        assertThatThrownBy(() -> validate(key, fingerprint, changed)).isInstanceOf(WorldStorageFormatException.class);
    }

    @Test
    void changedExpectedFingerprintCannotReuseTheSameBytes() throws Exception {
        var catalog = catalog();
        String fingerprint = new CanonicalWorldCatalogFingerprint(mapper).fingerprint(catalog);
        String key = "world-catalog:v2:" + fingerprint;
        String json = mapper.writeValueAsString(catalog);
        validate(key, fingerprint, json);
        assertThatThrownBy(() -> validate(key, "wrong", json)).isInstanceOf(WorldStorageFormatException.class);
    }
}
