package com.footballmanager.domain.ports.out.auth;

/**
 * Outbound authentication token boundary.
 *
 * Domain/application code asks for token capabilities through this port; the
 * concrete token technology stays in infrastructure.
 */
public interface AuthTokenService {

    String generateToken(String userId, String role);

    String generateRefreshToken(String userId);

    boolean validateToken(String token);

    String getUserIdFromToken(String token);

    String getRoleFromToken(String token);

    long getExpirationTime();
}
