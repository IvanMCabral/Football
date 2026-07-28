package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.List;

final class V24PlayerSkillService {

    int maxSkill(List<V24PlayerMatchState> players, PlayerSkill skill) {
        return players.stream()
            .filter(V24PlayerMatchState::onPitch)
            .filter(p -> !p.injured())
            .filter(p -> !p.redCard())
            .mapToInt(p -> p.getSkillLevel(skill))
            .max()
            .orElse(0);
    }
}
