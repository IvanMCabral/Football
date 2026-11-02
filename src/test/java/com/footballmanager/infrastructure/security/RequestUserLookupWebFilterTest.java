package com.footballmanager.infrastructure.security;

import com.footballmanager.adapters.out.sql.UserRepositoryAdapter;
import com.footballmanager.infrastructure.persistence.entity.UserEntity;
import com.footballmanager.infrastructure.persistence.repository.UserR2dbcRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestUserLookupWebFilterTest {
    private final UserR2dbcRepository sql = mock(UserR2dbcRepository.class);
    private final UserRepositoryAdapter repository = new UserRepositoryAdapter(sql);
    private final CanonicalUserAuthenticationManager authentication = new CanonicalUserAuthenticationManager(repository);
    private final RequestUserLookupWebFilter filter = new RequestUserLookupWebFilter();
    private final UUID owner = UUID.randomUUID();

    private UserEntity user(UUID id, String role) {
        return new UserEntity(id, "qa@example.invalid", "webqa", "hash", role, Instant.now(), Instant.now(), null);
    }

    private Mono<Void> request(HttpMethod method, String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
        return filter.filter(exchange, ignored -> authentication.authenticate(
                new UsernamePasswordAuthenticationToken(owner.toString(), "verified-jwt"))
                .flatMap(auth -> Mono.zip(repository.findById(owner), repository.findById(owner)))
                .then());
    }

    @Test void securityAndConcurrentHandlerReadsShareOnePhysicalQueryOnBothReadRoutes() {
        when(sql.findById(owner)).thenReturn(Mono.just(user(owner, "USER")));
        request(HttpMethod.GET, "/api/v1/auth/me").block();
        verify(sql).findById(owner);
        request(HttpMethod.GET, "/api/v1/dashboard/user-stats").block();
        verify(sql, times(2)).findById(owner);
    }

    @Test void deletionIsRejectedOnTheVeryNextRequest() {
        when(sql.findById(owner)).thenReturn(Mono.just(user(owner, "USER")), Mono.empty());
        request(HttpMethod.GET, "/api/v1/auth/me").block();
        assertThrows(BadCredentialsException.class, () -> request(HttpMethod.GET, "/api/v1/auth/me").block());
        verify(sql, times(2)).findById(owner);
    }

    @Test void everyRequestReadsTheCurrentCanonicalRole() {
        when(sql.findById(owner)).thenReturn(Mono.just(user(owner, "ADMIN")), Mono.just(user(owner, "USER")));
        for (String role : new String[]{"ROLE_ADMIN", "ROLE_USER"}) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/me"));
            filter.filter(exchange, ignored -> authentication.authenticate(
                    new UsernamePasswordAuthenticationToken(owner.toString(), "verified-jwt"))
                    .doOnNext(auth -> assertEquals(role, auth.getAuthorities().iterator().next().getAuthority())).then()).block();
        }
        verify(sql, times(2)).findById(owner);
    }

    @Test void differentIdsNeverShareAUser() {
        UUID other = UUID.randomUUID();
        when(sql.findById(owner)).thenReturn(Mono.just(user(owner, "USER")));
        when(sql.findById(other)).thenReturn(Mono.just(user(other, "ADMIN")));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/me"));
        filter.filter(exchange, ignored -> Mono.zip(repository.findById(owner), repository.findById(other))
                .doOnNext(users -> assertNotEquals(users.getT1().getId(), users.getT2().getId())).then()).block();
        verify(sql).findById(owner);
        verify(sql).findById(other);
    }

    @Test void mutationAndUnrelatedRoutesDoNotReuseReads() {
        when(sql.findById(owner)).thenReturn(Mono.just(user(owner, "USER")));
        request(HttpMethod.POST, "/api/v1/auth/me").block();
        request(HttpMethod.GET, "/api/v1/something-else").block();
        verify(sql, times(6)).findById(owner);
    }

    @Test void resubscribingCreatesANewRequestScope() {
        when(sql.findById(owner)).thenReturn(Mono.just(user(owner, "USER")));
        Mono<Void> request = request(HttpMethod.GET, "/api/v1/auth/me");
        request.block();
        request.block();
        verify(sql, times(2)).findById(owner);
    }
}
