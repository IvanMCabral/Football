package com.footballmanager.adapters.in.web.common;

/**
 * V24D14-JSON401: Centralized JSON error body shape.
 *
 * <p>Used by both SecurityConfig.authenticationEntryPoint (for security
 * filter rejections) and GlobalExceptionHandler.handleUnauthorized (for
 * controller-level rejections from ControllerHelper.getUserId()).
 *
 * <p>Contract: { "code": "...", "message": "...", "status": int }
 * <ul>
 *   <li>code: machine-readable string (e.g. "UNAUTHORIZED", "LINEUP_VALIDATION_ERROR")</li>
 *   <li>message: human-readable description (safe to display to end users)</li>
 *   <li>status: HTTP status code as int (e.g. 401, 422)</li>
 * </ul>
 *
 * <p>Do NOT change the field names or order without coordinating with
 * the frontend — clients parse this body for code/message.
 */
public record ErrorResponseBody(String code, String message, int status) {

    /**
     * Factory for HTTP 401 Unauthorized responses.
     * The code is always "UNAUTHORIZED" (per V24D12.1 contract).
     * The status is always 401.
     */
    public static ErrorResponseBody unauthorized(String message) {
        return new ErrorResponseBody("UNAUTHORIZED", message, 401);
    }
}
