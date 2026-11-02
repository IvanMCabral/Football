package com.footballmanager.infrastructure.security;

import com.footballmanager.domain.ports.out.auth.AuthTokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider implements AuthTokenService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    private volatile JwtParser parser;

    private JwtParser parser() {
        JwtParser current = parser;
        if (current == null) {
            synchronized (this) {
                current = parser;
                if (current == null) {
                    current = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes())).build();
                    parser = current;
                }
            }
        }
        return current;
    }

    @Override
    public String generateToken(String userId, String role) {
        return createToken(userId, role, jwtExpirationMs);
    }

    @Override
    public String generateRefreshToken(String userId) {
        return createToken(userId, "ROLE_USER", refreshExpirationMs);
    }

    private String createToken(String userId, String role, long expirationMs) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Instant now = Instant.now();
        Instant expiryDate = now.plusMillis(expirationMs);

        return Jwts.builder()
                .setSubject(userId)
                .claim("role", role)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiryDate))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    @Override
    public String getUserIdFromToken(String token) {
        return parser()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    @Override
    public String getRoleFromToken(String token) {
        return (String) parser()
                .parseClaimsJws(token)
                .getBody()
                .get("role");
    }

    @Override
    public boolean validateToken(String token) {
        try {
            parser().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long getExpirationTime() {
        return jwtExpirationMs / 1000;
    }
}
