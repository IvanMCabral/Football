package com.footballmanager.application.service.security;

import com.footballmanager.application.engine.match.MatchEngine;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.port.out.CareerOwnershipPort;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ResourceOwnershipAuthorityTest {

    private static final UUID OWNER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OWNER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void careerAuthorityMapsMissingOrForeignMappingToOpaqueDenial() {
        CareerOwnershipPort port = mock(CareerOwnershipPort.class);
        when(port.capture(OWNER_A, "career-b"))
                .thenReturn(Mono.error(new IllegalStateException("mapping belongs to another owner")));

        StepVerifier.create(new CareerOwnershipAuthority(port).requireOwned(OWNER_A, "career-b"))
                .expectError(CareerOwnershipDeniedException.class)
                .verify();

        verify(port).capture(OWNER_A, "career-b");
    }

    @Test
    void roundAuthorityRequiresBothOwnerAndCareer() {
        RoundEngineRegistry registry = new RoundEngineRegistry();
        UUID roundId = UUID.randomUUID();
        RoundEngine engine = new RoundEngine(roundId);
        engine.setOwner(OWNER_A, "career-a");
        registry.register(roundId, engine);
        RoundOwnershipAuthority authority = new RoundOwnershipAuthority(registry);

        assertThat(authority.requireOwned(OWNER_A, "career-a", roundId).block()).isSameAs(engine);
        StepVerifier.create(authority.requireOwned(OWNER_A, "career-b", roundId))
                .expectError(RoundOwnershipDeniedException.class)
                .verify();
        StepVerifier.create(authority.requireOwned(OWNER_B, "career-a", roundId))
                .expectError(RoundOwnershipDeniedException.class)
                .verify();
    }

    @Test
    void matchToRoundLookupIsOwnerScoped() {
        RoundEngineRegistry registry = new RoundEngineRegistry();
        UUID roundId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        RoundEngine engine = new RoundEngine(roundId);
        engine.setOwner(OWNER_A, "career-a");
        engine.registerMatch(matchId, mock(MatchEngine.class));
        registry.register(roundId, engine);
        RoundOwnershipAuthority authority = new RoundOwnershipAuthority(registry);

        assertThat(authority.requireOwnedRoundIdForMatch(OWNER_A, matchId).block()).isEqualTo(roundId);
        StepVerifier.create(authority.requireOwnedRoundIdForMatch(OWNER_B, matchId))
                .expectError(RoundOwnershipDeniedException.class)
                .verify();
    }
}
