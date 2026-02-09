package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.*;
import com.footballmanager.domain.model.aggregate.*;

/**
 * Enum representing player's injury state for match eligibility and recovery logic.
 */
public enum InjuryState {
    HEALTHY,
    INJURED_LIGHT,
    INJURED_SERIOUS
}
