package com.footballmanager.application.exception;

/**
 * LIVE-MATCH-F2-LIVE F2.5: protocol-level failure thrown when a manager
 * tries to schedule a manual substitution for a minute that is already in
 * the past.
 *
 * <p>This is distinct from a business validation failure (e.g. "player
 * not in starting XI", which is translated by
 * {@code SubstitutionCommandUseCaseImpl} into a 200 OK +
 * {@code success=false} response per FLAG 1 UX). A request for a
 * past minute is a protocol failure: the engine would never apply
 * the swap, so the manager should get a hard error rather than a
 * silent no-op.
 *
 * <p>Extends {@link IllegalArgumentException} so callers that catch
 * IAE generically (e.g. legacy exception filters) still see a sensible
 * exception, but the dedicated
 * {@code @ExceptionHandler(MinuteInPastException.class)} in
 * {@code GlobalExceptionHandler} returns HTTP 400 with the message.
 *
 * <p>Status code mapping:
 * <ul>
 *   <li>{@code 400 BAD_REQUEST} via dedicated handler in
 *       {@code GlobalExceptionHandler} (this exception is a subclass of
 *       IAE but is handled first by Spring's exception handler
 *       resolution — most specific match wins).</li>
 * </ul>
 */
public class MinuteInPastException extends IllegalArgumentException {
    public MinuteInPastException(String message) {
        super(message);
    }
}
