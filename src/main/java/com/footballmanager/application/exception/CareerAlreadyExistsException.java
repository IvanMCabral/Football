package com.footballmanager.application.exception;

/** Raised when a new career is requested while the owner already has one. */
public final class CareerAlreadyExistsException extends RuntimeException {
    public CareerAlreadyExistsException() {
        super("career already exists");
    }
}
