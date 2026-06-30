package com.footballmanager.adapters.in.web.common;

/**
 * V25D78-C50: Excepción dedicada para impersonation / JWT-userId mismatch.
 *
 * <p>Lanzada por {@link ControllerHelper#requireSelfUserId} cuando el
 * {@code userId} del JWT NO coincide con el {@code userId} recibido en el
 * query param o body de la request. Mapeada a HTTP 403 Forbidden con código
 * {@code IMPERSONATION_FORBIDDEN} por
 * {@link GlobalExceptionHandler#handleImpersonationForbidden}.
 *
 * <p><b>Por qué una excepción dedicada y no un {@code ResponseEntity}
 * inline:</b> el patrón C47/C48 inline retornaba el
 * {@code ResponseEntity.status(FORBIDDEN)} directamente desde el controller,
 * lo que obligaba a repetir el wire format en cada endpoint y producía casts
 * feos cuando el tipo genérico del Mono&lt;ResponseEntity&lt;X&gt;&gt; variaba
 * entre endpoints. Esta excepción + @ExceptionHandler centraliza el contrato.
 */
public class ImpersonationForbiddenException extends RuntimeException {
    public ImpersonationForbiddenException(String message) {
        super(message);
    }
}