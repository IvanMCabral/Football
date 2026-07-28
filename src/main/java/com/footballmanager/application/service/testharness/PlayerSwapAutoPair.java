package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.entity.SessionPlayer;

record PlayerSwapAutoPair(
    SessionPlayer starter,
    SessionPlayer bench
) {
}
