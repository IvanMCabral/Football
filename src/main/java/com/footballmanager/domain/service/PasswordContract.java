package com.footballmanager.domain.service;

import java.nio.charset.StandardCharsets;

/**
 * Canonical raw-password contract for the BCrypt-backed authentication flow.
 *
 * <p>BCrypt consumes UTF-8 bytes, not Java characters. Keeping the byte limit
 * here prevents a validator from accepting input that the productive encoder
 * cannot hash. Passwords are never truncated.</p>
 */
public final class PasswordContract {

    public static final int MIN_CHARACTERS = 8;
    public static final int MAX_UTF8_BYTES = 72;

    private PasswordContract() {
    }

    public static int utf8ByteLength(String password) {
        return password == null ? -1 : password.getBytes(StandardCharsets.UTF_8).length;
    }

    public static boolean isRegistrationPasswordValid(String password) {
        return password != null
            && !password.isBlank()
            && password.length() >= MIN_CHARACTERS
            && utf8ByteLength(password) <= MAX_UTF8_BYTES;
    }

    public static boolean isHashablePassword(String password) {
        return password != null
            && !password.isBlank()
            && utf8ByteLength(password) <= MAX_UTF8_BYTES;
    }
}
