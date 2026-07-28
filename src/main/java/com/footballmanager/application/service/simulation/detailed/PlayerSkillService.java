package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.List;

final class PlayerSkillService {

    int maxSkill(List<PlayerMatchState> players, PlayerSkill skill) {
        return players.stream()
            .filter(PlayerMatchState::onPitch)
            .filter(p -> !p.injured())
            .filter(p -> !p.redCard())
            .mapToInt(p -> p.getSkillLevel(skill))
            .max()
            .orElse(0);
    }
}
