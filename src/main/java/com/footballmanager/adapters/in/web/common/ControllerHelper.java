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
     * @throws UnauthorizedException si no hay usuario autenticado
     *         (mapeada a HTTP 401 por GlobalExceptionHandler)
     */
    public UUID getUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Unauthorized: no user id in authentication");
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

    /**
     * V25D78-C50: Impersonation sweep — valida que el {@code userId} del JWT
     * coincide con el {@code userId} recibido en el query param o body.
     *
     * <p>Si el JWT userId NO coincide con {@code paramUserId}, lanza
     * {@link ImpersonationForbiddenException} (mapeada a HTTP 403 con código
     * {@code IMPERSONATION_FORBIDDEN} por
     * {@link GlobalExceptionHandler#handleImpersonationForbidden}).
     *
     * <p>Si coinciden (o si la request es anónima sin JWT), retorna
     * {@code paramUserId} sin error y el caller continúa con el flujo normal.
     *
     * <p><b>Por qué excepción y no ResponseEntity inline:</b> el patrón
     * C47/C48 inline retornaba un {@code ResponseEntity.status(FORBIDDEN)}
     * directamente desde el controller. Eso requiere repetir el wrapping en
     * cada endpoint y obliga a casts feos cuando el tipo genérico del
     * Mono&lt;ResponseEntity&lt;X&gt;&gt; cambia (X = List, Map, entity, etc).
     * Una excepción dedicada + @ExceptionHandler mantiene los controllers
     * limpios (1 línea por endpoint) y centraliza el wire format del 403.
     *
     * <p><b>Nota sobre requests anónimas:</b> SecurityConfig.java (post-C48)
     * rechaza con 401 cualquier request a /api/v1/world/** que no tenga JWT.
     * Por lo tanto, en la práctica este helper SIEMPRE recibe un
     * Authentication poblado cuando se ejecuta. Sin embargo, mantenemos el
     * check defensivo {@code authentication != null && getName() != null}
     * para no acoplar el helper al SecurityConfig — si en el futuro se
     * decide volver a permitAll para algún path, el helper sigue siendo
     * seguro (no bloquea requests anónimas, sigue el contrato C47).
     *
     * @param authentication Spring Security authentication (puede ser null en request anónima)
     * @param paramUserId    userId recibido en query/body
     * @return {@code paramUserId} si OK o si anónimo
     * @throws ImpersonationForbiddenException si el JWT userId no coincide con {@code paramUserId}
     */
    public UUID requireSelfUserId(Authentication authentication, UUID paramUserId) {
        // Si la request es anónima, no validamos impersonation. SecurityConfig
        // ya la rechaza con 401 antes de llegar acá; si llegara, la dejamos pasar
        // para no introducir regresiones (contrato C47: anónimos permitidos).
        if (authentication == null || authentication.getName() == null
                || authentication.getName().isBlank()) {
            return paramUserId;
        }
        UUID jwtUserId;
        try {
            jwtUserId = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            // JWT con name no-UUID (defensivo, no debería pasar dado el converter
            // del SecurityConfig que setea name=userId). Devolvemos 403 igual.
            throw new ImpersonationForbiddenException("JWT principal is not a valid userId");
        }
        if (!jwtUserId.equals(paramUserId)) {
            throw new ImpersonationForbiddenException(
                "Authenticated user is not allowed to act on another user's data");
        }
        return paramUserId;
    }
}
