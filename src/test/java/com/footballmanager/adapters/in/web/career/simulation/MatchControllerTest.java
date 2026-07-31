package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.service.match.MatchManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchControllerTest {

    private MatchManagementService matchManagementService;
    private ControllerHelper controllerHelper;
    private Authentication authentication;
    private MatchController controller;

    private UUID userId;
    private UUID matchId;

    @BeforeEach
    void setUp() {
        matchManagementService = mock(MatchManagementService.class);
        controllerHelper = mock(ControllerHelper.class);
        authentication = mock(Authentication.class);
        controller = new MatchController(matchManagementService, controllerHelper);

        userId = UUID.randomUUID();
        matchId = UUID.randomUUID();

        when(controllerHelper.getUserId(authentication)).thenReturn(userId);
        when(matchManagementService.pauseMatch(userId, matchId)).thenReturn(Mono.empty());
        when(matchManagementService.resumeMatch(userId, matchId)).thenReturn(Mono.empty());
        when(matchManagementService.stopMatch(userId, matchId)).thenReturn(Mono.empty());
    }

    @Test
    void pauseUsesAuthenticatedUser() {
        controller.pauseMatch(matchId.toString(), authentication).block();

        verify(matchManagementService).pauseMatch(userId, matchId);
    }

    @Test
    void resumeUsesAuthenticatedUser() {
        controller.resumeMatch(matchId.toString(), authentication).block();

        verify(matchManagementService).resumeMatch(userId, matchId);
    }

    @Test
    void stopUsesAuthenticatedUser() {
        controller.stopMatch(matchId.toString(), authentication).block();

        verify(matchManagementService).stopMatch(userId, matchId);
    }
}
