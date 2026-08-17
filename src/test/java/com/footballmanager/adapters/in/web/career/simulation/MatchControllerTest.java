package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.service.match.MatchManagementService;
import com.footballmanager.application.service.match.MatchSessionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void missingSessionIsControlledAsNotFound() {
        when(matchManagementService.pauseMatch(userId, matchId))
            .thenReturn(Mono.error(new MatchSessionNotFoundException(matchId)));

        var response = controller.pauseMatch(matchId.toString(), authentication).block();

        org.junit.jupiter.api.Assertions.assertEquals(
            org.springframework.http.HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void missingResumeAndStopSessionsAreControlledAsNotFound() {
        when(matchManagementService.resumeMatch(userId, matchId))
            .thenReturn(Mono.error(new MatchSessionNotFoundException(matchId)));
        when(matchManagementService.stopMatch(userId, matchId))
            .thenReturn(Mono.error(new MatchSessionNotFoundException(matchId)));

        var resume = controller.resumeMatch(matchId.toString(), authentication).block();
        var stop = controller.stopMatch(matchId.toString(), authentication).block();

        org.junit.jupiter.api.Assertions.assertEquals(
            org.springframework.http.HttpStatus.NOT_FOUND, resume.getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
            org.springframework.http.HttpStatus.NOT_FOUND, stop.getStatusCode());
    }

    @Test
    void unexpectedFailureIsNotHiddenAsNotFound() {
        when(matchManagementService.pauseMatch(userId, matchId))
            .thenReturn(Mono.error(new IllegalStateException("driver unavailable")));

        assertThrows(IllegalStateException.class,
            () -> controller.pauseMatch(matchId.toString(), authentication).block());
    }
}
