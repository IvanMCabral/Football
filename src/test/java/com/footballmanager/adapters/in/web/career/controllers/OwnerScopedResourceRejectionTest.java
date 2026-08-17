package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.service.security.CareerOwnershipAuthority;
import com.footballmanager.application.service.security.CareerOwnershipDeniedException;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchQueryService;
import com.footballmanager.application.service.simulation.detailed.MatchComparisonService;
import com.footballmanager.application.service.simulation.detailed.stats.PlayerSeasonStatsQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnerScopedResourceRejectionTest {

    private static final UUID OWNER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String CAREER_B = "career-b";
    private final Authentication ownerA = new UsernamePasswordAuthenticationToken(
            OWNER_A.toString(), null, List.of());

    @Mock DetailedMatchQueryService detailQuery;
    @Mock MatchComparisonService comparisonService;
    @Mock PlayerSeasonStatsQueryService statsQuery;
    @Mock CareerOwnershipAuthority ownershipAuthority;

    @Test
    void foreignDetailedReadIsRejectedBeforeDetailOrComparisonServices() {
        when(ownershipAuthority.requireOwned(OWNER_A, CAREER_B))
                .thenReturn(Mono.error(new CareerOwnershipDeniedException()));
        DetailedMatchController controller = new DetailedMatchController(
                detailQuery, comparisonService, ownershipAuthority, new ControllerHelper());

        ResponseEntity<Object> detail = controller.getDetail(CAREER_B, "match-b", ownerA).block();
        ResponseEntity<Object> compare = controller.getCompare(CAREER_B, "match-b", ownerA).block();

        assertThat(detail.getStatusCode().value()).isEqualTo(404);
        assertThat(compare.getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(detailQuery, comparisonService);
    }

    @Test
    void foreignStatsReadIsRejectedBeforeStatsQuery() {
        when(ownershipAuthority.requireOwned(OWNER_A, CAREER_B))
                .thenReturn(Mono.error(new CareerOwnershipDeniedException()));
        PlayerSeasonStatsController controller = new PlayerSeasonStatsController(
                statsQuery, ownershipAuthority, new ControllerHelper());

        ResponseEntity<Object> response = controller.getPlayerSeasonStats(
                CAREER_B, 1, null, null, null, null, ownerA).block();

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(statsQuery);
    }
}
