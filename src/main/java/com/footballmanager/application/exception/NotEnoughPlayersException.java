package com.footballmanager.application.exception;

public class NotEnoughPlayersException extends RuntimeException {
    public NotEnoughPlayersException(String message) {
        super(message);
    }
}
