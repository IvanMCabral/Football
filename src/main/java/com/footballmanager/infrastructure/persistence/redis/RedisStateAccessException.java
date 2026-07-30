package com.footballmanager.infrastructure.persistence.redis;

/**
 * Infrastructure exception used when Redis state access fails.
 *
 * <p>Callers can still represent a real cache miss as an empty result. This
 * exception is reserved for infrastructure failures, timeouts and malformed
 * payloads that must not masquerade as missing data.
 */
public class RedisStateAccessException extends RuntimeException {

    public RedisStateAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
