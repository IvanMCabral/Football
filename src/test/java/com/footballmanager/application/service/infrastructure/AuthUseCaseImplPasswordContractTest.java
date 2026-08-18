package com.footballmanager.application.service.infrastructure;

import com.footballmanager.application.exception.AuthCredentialsException;
import com.footballmanager.application.exception.AuthValidationException;
import com.footballmanager.domain.port.in.auth.AuthLoginCommand;
import com.footballmanager.domain.port.in.auth.AuthRegisterCommand;
import com.footballmanager.domain.ports.out.auth.AuthTokenService;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import com.footballmanager.domain.ports.out.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthUseCaseImplPasswordContractTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthTokenService tokenService;

    @Test
    void registrationRejectsTheHistorical95BytePasswordBeforeRepositoryOrEncoder() {
        AuthUseCaseImpl useCase = useCase();

        assertThatThrownBy(() -> useCase.register(new AuthRegisterCommand(
            "user@example.test", "manager", "a".repeat(95))))
            .isInstanceOf(AuthValidationException.class);

        verify(userRepository, never()).findByEmail("user@example.test");
        verify(passwordEncoder, never()).encode("a".repeat(95));
    }

    @Test
    void encoderBoundaryFailureIsConvertedToAnAuthValidationFailure() {
        AuthUseCaseImpl useCase = useCase();
        when(userRepository.findByEmail("user@example.test")).thenReturn(Mono.empty());
        when(userRepository.existsByUsername("manager")).thenReturn(Mono.just(false));
        when(passwordEncoder.encode("abcdefgh"))
            .thenThrow(new IllegalArgumentException("BCrypt password exceeds maximum length"));

        StepVerifier.create(useCase.register(new AuthRegisterCommand(
                "user@example.test", "manager", "abcdefgh")))
            .expectError(AuthValidationException.class)
            .verify();
    }

    @Test
    void loginRejectsAnUnhashablePasswordAsCredentialsWithoutLeakingEncoderError() {
        AuthUseCaseImpl useCase = useCase();

        assertThatThrownBy(() -> useCase.login(new AuthLoginCommand(
            "user@example.test", "a".repeat(95))))
            .isInstanceOf(AuthCredentialsException.class);

        verify(userRepository, never()).findByEmail("user@example.test");
        verify(passwordEncoder, never()).matches("a".repeat(95), "unused");
    }

    private AuthUseCaseImpl useCase() {
        return new AuthUseCaseImpl(userRepository, teamRepository, passwordEncoder, tokenService);
    }
}
