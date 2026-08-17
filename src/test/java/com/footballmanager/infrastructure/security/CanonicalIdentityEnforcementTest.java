package com.footballmanager.infrastructure.security;

import com.footballmanager.application.service.infrastructure.AuthUseCaseImpl;
import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.domain.model.valueobject.UserId;
import com.footballmanager.domain.port.in.auth.AuthRefreshCommand;
import com.footballmanager.domain.ports.out.auth.AuthTokenService;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import com.footballmanager.domain.ports.out.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalIdentityEnforcementTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthTokenService tokenService;

    @Test
    void staleAccessSubjectIsRejectedBeforeOwnerScopedCodeCanRun() {
        CanonicalUserAuthenticationManager manager = new CanonicalUserAuthenticationManager(userRepository);
        when(userRepository.findById(USER_ID)).thenReturn(Mono.empty());

        Authentication token = token(USER_ID.toString(), "ADMIN");

        StepVerifier.create(manager.authenticate(token))
            .expectError(BadCredentialsException.class)
            .verify();

        verify(userRepository).findById(USER_ID);
    }

    @Test
    void malformedAccessSubjectIsRejectedWithoutRepositoryLookup() {
        CanonicalUserAuthenticationManager manager = new CanonicalUserAuthenticationManager(userRepository);

        StepVerifier.create(manager.authenticate(token("not-a-uuid", "USER")))
            .expectError(BadCredentialsException.class)
            .verify();

        verify(userRepository, never()).findById(any(UUID.class));
    }

    @Test
    void canonicalAuthorityFailureFailsClosed() {
        CanonicalUserAuthenticationManager manager = new CanonicalUserAuthenticationManager(userRepository);
        when(userRepository.findById(USER_ID)).thenReturn(Mono.error(new IllegalStateException("database unavailable")));

        StepVerifier.create(manager.authenticate(token(USER_ID.toString(), "USER")))
            .expectError(BadCredentialsException.class)
            .verify();
    }

    @Test
    void currentUserIsAcceptedWithCanonicalRoleInsteadOfStaleJwtRole() {
        CanonicalUserAuthenticationManager manager = new CanonicalUserAuthenticationManager(userRepository);
        User user = User.create(UserId.of(USER_ID), "current@example.test", "current-user", "hash");
        when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user));

        Authentication result = manager.authenticate(token(USER_ID.toString(), "ADMIN")).block();

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(USER_ID.toString());
        assertThat(result.getAuthorities())
            .extracting(Object::toString)
            .containsExactly("ROLE_USER");
    }

    @Test
    void staleRefreshSubjectIsRejectedWithoutMintingTokens() {
        AuthUseCaseImpl useCase = new AuthUseCaseImpl(userRepository, teamRepository, passwordEncoder, tokenService);
        String refresh = "signed-refresh-with-stale-subject";
        when(tokenService.validateToken(refresh)).thenReturn(true);
        when(tokenService.getUserIdFromToken(refresh)).thenReturn(USER_ID.toString());
        when(userRepository.findById(USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.refreshToken(new AuthRefreshCommand(refresh)))
            .expectErrorMatches(error -> error.getClass().getSimpleName().equals("AuthCredentialsException"))
            .verify();

        verify(tokenService, never()).generateToken(any(), any());
        verify(tokenService, never()).generateRefreshToken(any());
    }

    @Test
    void refreshWithMalformedSubjectIsRejectedBeforeLookupOrMinting() {
        AuthUseCaseImpl useCase = new AuthUseCaseImpl(userRepository, teamRepository, passwordEncoder, tokenService);
        String refresh = "signed-refresh-with-malformed-subject";
        when(tokenService.validateToken(refresh)).thenReturn(true);
        when(tokenService.getUserIdFromToken(refresh)).thenReturn("not-a-uuid");

        StepVerifier.create(useCase.refreshToken(new AuthRefreshCommand(refresh)))
            .expectErrorMatches(error -> error.getClass().getSimpleName().equals("AuthCredentialsException"))
            .verify();

        verify(userRepository, never()).findById(any(UUID.class));
        verify(tokenService, never()).generateToken(any(), any());
    }

    @Test
    void refreshUsesCurrentCanonicalRoleAndIdentity() {
        AuthUseCaseImpl useCase = new AuthUseCaseImpl(userRepository, teamRepository, passwordEncoder, tokenService);
        String refresh = "signed-refresh";
        User user = User.create(UserId.of(USER_ID), "current@example.test", "current-user", "hash");
        when(tokenService.validateToken(refresh)).thenReturn(true);
        when(tokenService.getUserIdFromToken(refresh)).thenReturn(USER_ID.toString());
        when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user));
        when(tokenService.generateToken(USER_ID.toString(), "USER")).thenReturn("new-access");
        when(tokenService.generateRefreshToken(USER_ID.toString())).thenReturn("new-refresh");
        when(tokenService.getExpirationTime()).thenReturn(900L);

        StepVerifier.create(useCase.refreshToken(new AuthRefreshCommand(refresh)))
            .assertNext(result -> {
                assertThat(result.accessToken()).isEqualTo("new-access");
                assertThat(result.refreshToken()).isEqualTo("new-refresh");
            })
            .verifyComplete();
    }

    private static Authentication token(String subject, String role) {
        return new UsernamePasswordAuthenticationToken(
            subject, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
