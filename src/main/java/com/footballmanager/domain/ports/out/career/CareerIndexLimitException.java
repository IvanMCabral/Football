package com.footballmanager.domain.ports.out.career;

/** Signals that the bounded owner career index cannot accept another entry. */
public final class CareerIndexLimitException extends RuntimeException {
    public CareerIndexLimitException() {
        super("career index limit reached");
    }
}
