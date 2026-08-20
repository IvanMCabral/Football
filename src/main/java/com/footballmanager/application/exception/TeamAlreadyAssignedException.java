package com.footballmanager.application.exception;

/** Raised when a requested team already has a different authenticated owner. */
public class TeamAlreadyAssignedException extends RuntimeException {
    public TeamAlreadyAssignedException() {
        super("TEAM_ALREADY_ASSIGNED");
    }
}
