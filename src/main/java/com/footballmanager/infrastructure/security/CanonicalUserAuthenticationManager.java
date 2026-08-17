package com.footballmanager.infrastructure.security;

import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.domain.ports.out.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Resolves a cryptographically authenticated subject against the canonical
 * PostgreSQL user authority before Spring Security accepts the request.
 *
 * <p>The JWT is proof of token integrity only.  Current-user existence and
 * the current role are deliberately read from the user port here so deleted
 * users and stale role claims cannot reach owner-scoped application code.</p>
 */
@RequiredArgsConstructor
public final class CanonicalUserAuthenticationManager implements ReactiveAuthenticationManager {

    private final UserRepository userRepository;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return Mono.defer(() -> parseSubject(authentication.getName())
                .map(userRepository::findById)
                .orElseGet(() -> Mono.error(invalidSubject())))
            .flatMap(user -> authenticationFor(authentication, user))
            .switchIfEmpty(Mono.error(invalidSubject()))
            .onErrorMap(error -> error instanceof BadCredentialsException
                ? error
                : invalidSubject());
    }

    private static BadCredentialsException invalidSubject() {
        return new BadCredentialsException("Canonical user authentication failed");
    }

    private static java.util.Optional<UUID> parseSubject(String subject) {
        try {
            return java.util.Optional.of(UUID.fromString(subject));
        } catch (RuntimeException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static Mono<Authentication> authenticationFor(Authentication original, User user) {
        String role = user.getRole().name();
        return Mono.just(new UsernamePasswordAuthenticationToken(
            original.getName(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
