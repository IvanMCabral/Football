package com.footballmanager.adapters.in.web.common;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Componente reutilizable con helpers para controllers.
 * Centraliza lógica común de extracción de datos de Authentication.
 */
@Component
public class ControllerHelper {

    /**
     * Extrae el userId de Authentication.
     *
     * @param authentication Spring Security authentication
     * @return UUID del usuario autenticado
     * @throws IllegalArgumentException si no hay usuario autenticado
     */
    public UUID getUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Unauthorized: no user id in authentication");
        }
        return UUID.fromString(authentication.getName());
    }

    /**
     * Verifica si hay un usuario autenticado.
     */
    public boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.getName() != null;
    }

    /**
     * Obtiene el nombre del usuario autenticado (o null).
     */
    public String getUsername(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
