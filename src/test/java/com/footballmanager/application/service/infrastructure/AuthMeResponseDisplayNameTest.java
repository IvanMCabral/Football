package com.footballmanager.application.service.infrastructure;

import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.domain.model.valueobject.UserId;
import com.footballmanager.domain.port.in.auth.AuthUserInfo;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import com.footballmanager.domain.ports.out.user.UserRepository;
import com.footballmanager.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthMeResponseDisplayNameTest {

    private static final UUID USER_ID_RAW = UUID.fromString("00000000-0000-0000-0000-00005577c711");
    private static final String USER_ID_STRING = USER_ID_RAW.toString();
    private static final String EMAIL = "smoke.c55.7.7.1.20260701@test.local";
    private static final String USERNAME = "smoke-c55.7.7.1";
    private static final String PASSWORD_HASH = "$2a$10$dummy.hash.for.test.only";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthUseCaseImpl useCase;

    private AuthUseCaseImpl build() {
        return new AuthUseCaseImpl(userRepository, teamRepository, passwordEncoder, jwtTokenProvider);
    }

    private User buildUser() {
        return User.create(UserId.of(USER_ID_RAW), EMAIL, USERNAME, PASSWORD_HASH);
    }

    @Test
    @DisplayName("(a) displayName == username when user has no team")
    void displayName_aliasesUsername_noTeam() {
        User user = buildUser();
        when(userRepository.findById(any(UUID.class))).thenReturn(Mono.just(user));

        useCase = build();
        AuthUserInfo info = useCase.getUserInfo(USER_ID_STRING).block();

        assertNotNull(info, "AuthUserInfo must not be null");
        assertEquals(USERNAME, info.username(), "username must round-trip");
        assertEquals(USERNAME, info.displayName(),
            "displayName must be populated as a 1:1 alias of username");
        assertEquals(EMAIL, info.email(), "email must round-trip");
        assertEquals(USER_ID_STRING, info.id(), "id must match");
        assertEquals(null, info.teamId(), "teamId must stay null for user without team");
        assertEquals(null, info.teamName(), "teamName must stay null when teamId is null");

        verify(teamRepository, never()).findById(any(), any());
    }

    @Test
    @DisplayName("(b) displayName == username when user has a team and team lookup empty")
    void displayName_aliasesUsername_teamBranchEmpty() {
        UUID teamId = UUID.fromString("00000000-0000-0000-0000-00005577c722");
        User user = buildUser();
        user.setTeamId(teamId);

        when(userRepository.findById(any(UUID.class))).thenReturn(Mono.just(user));
        when(teamRepository.findById(any(UUID.class), any(UUID.class))).thenReturn(Mono.empty());

        useCase = build();
        AuthUserInfo info = useCase.getUserInfo(USER_ID_STRING).block();

        assertNotNull(info, "AuthUserInfo must not be null");
        assertEquals(USERNAME, info.username());
        assertEquals(USERNAME, info.displayName(),
            "displayName must be populated before optional team lookup resolution");
        assertEquals(teamId.toString(), info.teamId());
        assertEquals(null, info.teamName(), "teamName must stay null when TeamRepository returns empty");
    }

    @Test
    @DisplayName("(c) displayName is part of the public auth port result")
    void displayName_portResult_isObservable() {
        AuthUserInfo info = new AuthUserInfo(USER_ID_STRING, EMAIL, USERNAME, USERNAME, null, null);

        assertEquals(USERNAME, info.displayName(),
            "displayName must be visible before the web adapter maps it to JSON");
    }

    @Test
    @DisplayName("(d) getUserInfo returns Mono with displayName")
    void getUserInfo_returnsMonoWithDisplayName() {
        User user = buildUser();
        when(userRepository.findById(any(UUID.class))).thenReturn(Mono.just(user));

        useCase = build();
        StepVerifier.create(useCase.getUserInfo(USER_ID_STRING))
            .assertNext(info -> {
                assertNotNull(info);
                assertEquals(USERNAME, info.displayName());
                assertEquals(USERNAME, info.username());
            })
            .verifyComplete();
    }
}
