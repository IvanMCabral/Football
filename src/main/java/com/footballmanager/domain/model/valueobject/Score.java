package com.footballmanager.domain.model.valueobject;

import java.io.Serializable;

/**
 * Score value object - INMUTABLE.
 * Usar withHomeIncrement() y withAwayIncrement() para crear nuevos valores.
 */
public record Score(int home, int away) implements Serializable {

    public Score() {
        this(0, 0);
    }

    public Score withHomeIncrement() {
        return new Score(home + 1, away);
    }

    public Score withAwayIncrement() {
        return new Score(home, away + 1);
    }
}
