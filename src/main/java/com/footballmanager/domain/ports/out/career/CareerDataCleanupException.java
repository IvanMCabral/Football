package com.footballmanager.domain.ports.out.career;

/** Propagates cleanup failures without allowing a career root delete to succeed. */
public final class CareerDataCleanupException extends RuntimeException {

    private final CareerDataCleanupResult result;

    public CareerDataCleanupException(CareerDataCleanupResult result, Throwable cause) {
        super("Career data cleanup failed for owner " + result.ownerHash(), cause);
        this.result = result;
    }

    public CareerDataCleanupResult result() {
        return result;
    }
}
