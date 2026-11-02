package com.footballmanager.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SinglePassJwtConverterTest {
    private static final String SECRET = "a".repeat(80);
    private final JwtTokenProvider provider = spy(new JwtTokenProvider());

    SinglePassJwtConverterTest() {
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 3_600_000L);
    }

    private org.springframework.security.web.server.authentication.ServerAuthenticationConverter converter() {
        return new SecurityConfig(provider, new ObjectMapper(), null, null, null).serverAuthenticationConverter();
    }

    private MockServerWebExchange request(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/").header("Authorization", "Bearer " + token));
    }

    @Test void signedSubjectIsVerifiedOnceBeforeCanonicalAuthentication() {
        String owner = UUID.randomUUID().toString();
        String token = provider.generateToken(owner, "ADMIN");
        var authentication = converter().convert(request(token)).block();
        assertEquals(owner, authentication.getName());
        assertFalse(authentication.isAuthenticated());
        assertTrue(authentication.getAuthorities().isEmpty());
        verify(provider).getUserIdFromToken(token);
        verify(provider, never()).validateToken(anyString());
        verify(provider, never()).getRoleFromToken(anyString());
    }

    @Test void invalidExpiredUnsignedAndFutureTokensAreRejected() {
        String owner = UUID.randomUUID().toString();
        var key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String expired = Jwts.builder().setSubject(owner).setExpiration(new Date(1)).signWith(key, SignatureAlgorithm.HS512).compact();
        String future = Jwts.builder().setSubject(owner).setNotBefore(new Date(System.currentTimeMillis() + 60_000)).signWith(key, SignatureAlgorithm.HS512).compact();
        String wrongKey = Jwts.builder().setSubject(owner).signWith(Keys.hmacShaKeyFor("b".repeat(80).getBytes()), SignatureAlgorithm.HS512).compact();
        String unsigned = Jwts.builder().setSubject(owner).compact();
        for (String token : java.util.List.of("garbage", expired, future, wrongKey, unsigned)) {
            assertNull(converter().convert(request(token)).block());
        }
    }

    @Test void parsingDoesNotBlockTheCallingEventLoop() {
        AtomicReference<String> thread = new AtomicReference<>();
        doAnswer(invocation -> { thread.set(Thread.currentThread().getName()); return invocation.callRealMethod(); })
                .when(provider).getUserIdFromToken(anyString());
        String token = provider.generateToken(UUID.randomUUID().toString(), "USER");
        assertNotNull(converter().convert(request(token)).block());
        assertTrue(thread.get().startsWith("boundedElastic"), thread.get());
    }

    @Test void sharedParserDoesNotReuseSubjectsOrExpiryResults() {
        var owners = java.util.List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        var tokens = owners.stream().map(owner -> provider.generateToken(owner, "USER")).toList();
        var actual = reactor.core.publisher.Flux.range(0, 100)
                .flatMap(i -> converter().convert(request(tokens.get(i % 2))).map(auth -> auth.getName()), 8)
                .collect(java.util.stream.Collectors.toSet()).block();
        assertEquals(new java.util.HashSet<>(owners), actual);
        String expired = Jwts.builder().setSubject(owners.get(0)).setExpiration(new Date(1))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS512).compact();
        assertNull(converter().convert(request(expired)).block());
    }
}
