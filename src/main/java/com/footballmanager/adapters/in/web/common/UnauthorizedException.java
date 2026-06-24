package com.footballmanager.adapters.in.web.common;

/**
 * Excepcion para requests sin autenticacion valida o con userId nulo.
 * Mapeada a HTTP 401 con body {code: UNAUTHORIZED, message, status: 401}
 * por GlobalExceptionHandler.
 *
 * <p>V24D12-3: Introduced to differentiate auth failures (401) from
 * validation failures (422 LINEUP_VALIDATION_ERROR) and state errors
 * (422 LINEUP_STATE_ERROR). Previously ControllerHelper.getUserId()
 * threw IllegalArgumentException which was caught by the IAE handler
 * and mapped to 422, leaking the auth flow into the validation flow
 * and causing the front to render a misleading 'validation error'
 * banner on missing tokens.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
