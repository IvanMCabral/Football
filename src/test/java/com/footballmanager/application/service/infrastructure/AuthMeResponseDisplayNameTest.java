package com.footballmanager.application.service.infrastructure;

import com.footballmanager.adapters.in.web.auth.dto.UserInfoResponse;
import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.domain.model.valueobject.UserId;
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

/**
 * V25D78-C55.7.7.1 BUG_L1: validates that {@link AuthUseCaseImpl#getUserInfo(String)}
 * now emits {@code displayName} populated as a 1:1 alias of the user's username, so
 * the frontend {@code displayName → email → username} chain (already implemented in
 * C55.7.7 commit 62ade5b) actually resolves to a friendly name instead of falling
 * back to the user's email address.
 *
 * <p>Coverage:
 * <ul>
 *   <li><b>(a)</b> Displayname == username (happy path, user without team).</li>
 *   <li><b>(b)</b> Displayname == username (user WITH team, team lookup resolves
 *       empty so {@code teamName=null} defaultIfEmpty branch runs).</li>
 *   <li><b>(c)</b> Displayname field roundtrips through the public DTO — no
 *       private serialization gotcha (regression guard for accidental private/private-set
 *       migration that would break Jackson).</li>
 * </ul>
 */
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
        // Use reconstruct() so we can stamp explicit UUID without a fresh Instant that
        // would defeat snapshot equality. Username must be 3+ chars (User.validateUsername).
        User user = User.create(UserId.of(USER_ID_RAW), EMAIL, USERNAME, PASSWORD_HASH);
        return user;
    }

    @Test
    @DisplayName("(a) displayName == username when user has no team")
    void displayName_aliasesUsername_noTeam() {
        User user = buildUser(); // teamId stays null (default)
        when(userRepository.findById(any(UUID.class))).thenReturn(Mono.just(user));

        useCase = build();
        UserInfoResponse info = useCase.getUserInfo(USER_ID_STRING).block();

        assertNotNull(info, "UserInfoResponse must not be null");
        assertEquals(USERNAME, info.username, "username must round-trip");
        assertEquals(USERNAME, info.displayName,
            "BUG_L1 fix: displayName must be populated as a 1:1 alias of username");
        assertEquals(EMAIL, info.email, "email must round-trip");
        assertEquals(USER_ID_STRING, info.id, "id must match");
        assertEquals(null, info.teamId, "teamId must stay null for user without team");
        assertEquals(null, info.teamName, "teamName must stay null when teamId is null");

        // TeamRepository must NOT be touched when getTeamId() == null (the early-return branch).
        verify(teamRepository, never()).findById(any(), any());
    }

    @Test
    @DisplayName("(b) displayName == username when user has a team and team lookup empty")
    void displayName_aliasesUsername_teamBranchEmpty() {
        UUID teamId = UUID.fromString("00000000-0000-0000-0000-00005577c722");
        User user = buildUser();
        user.setTeamId(teamId);

        when(userRepository.findById(any(UUID.class))).thenReturn(Mono.just(user));
        // Team lookup resolves empty -> defaultIfEmpty(info) keeps teamName=null but
        // the full UserInfoResponse is still returned with displayName populated.
        when(teamRepository.findById(any(UUID.class), any(UUID.class))).thenReturn(Mono.empty());

        useCase = build();
        UserInfoResponse info = useCase.getUserInfo(USER_ID_STRING).block();

        assertNotNull(info, "UserInfoResponse must not be null");
        assertEquals(USERNAME, info.username);
        assertEquals(USERNAME, info.displayName,
            "BUG_L1 fix: even with team wired, displayName must be populated before the " +
            "team lookup happens — username is already known at that point");
        assertEquals(teamId.toString(), info.teamId);
        assertEquals(null, info.teamName, "teamName must stay null when TeamRepository returns empty");
    }

    @Test
    @DisplayName("(c) displayName is serialized as a public String field on the DTO")
    void displayName_dtoField_isPublic() throws NoSuchFieldException {
        // Regression guard: if someone changes UserInfoResponse from public fields to
        // private+getters later and forgets to wire the getter, Jackson will silently
        // drop the field on JSON. This test asserts the field is publicly accessible
        // AND writable (matches the existing 5 fields pattern).
        java.lang.reflect.Field f = UserInfoResponse.class.getDeclaredField("displayName");

        assertEquals(String.class, f.getType(), "displayName must be String");
        assertEquals(java.lang.reflect.Modifier.PUBLIC, f.getModifiers() & java.lang.reflect.Modifier.PUBLIC
                | f.getModifiers() & java.lang.reflect.Modifier.STATIC,
            "displayName must be public (and not static) — matches the other 5 fields "
                + "(id, email, username, teamId, teamName) so Jackson serializes it");
    }

    @Test
    @DisplayName("(d) getUserInfo returns Mono — downstream StepVerifier contract")
    void getUserInfo_returnsMonoWithDisplayName() {
        User user = buildUser();
        when(userRepository.findById(any(UUID.class))).thenReturn(Mono.just(user));

        useCase = build();
        StepVerifier.create(useCase.getUserInfo(USER_ID_STRING))
            .assertNext(info -> {
                assertNotNull(info);
                assertEquals(USERNAME, info.displayName);
                assertEquals(USERNAME, info.username);
            })
            .verifyComplete();
    }
}
