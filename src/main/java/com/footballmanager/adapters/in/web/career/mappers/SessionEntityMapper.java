package com.footballmanager.adapters.in.web.career.mappers;

import com.footballmanager.adapters.in.web.career.dto.response.SessionPlayerDTO;
import com.footballmanager.adapters.in.web.career.dto.response.SessionTeamDTO;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;

/**
 * Mapper for converting Session entities to DTOs.
 */
public final class SessionEntityMapper {

    private SessionEntityMapper() {}

    public static SessionTeamDTO toDTO(SessionTeam team) {
        return new SessionTeamDTO(
            team.getSessionTeamId(),
            team.getBaseTeamId(),
            team.getName(),
            team.getCountry(),
            team.getBudget(),
            team.getFormation(),
            team.getMorale(),
            team.getReputation(),
            team.getOrigin().name()
        );
    }

    public static SessionPlayerDTO toDTO(SessionPlayer player) {
        return new SessionPlayerDTO(
            player.getSessionPlayerId(),
            player.getBasePlayerId(),
            player.getName(),
            player.getAge(),
            player.getPosition(),
            player.getAttack(),
            player.getDefense(),
            player.getTechnique(),
            player.getSpeed(),
            player.getStamina(),
            player.getMentality(),
            player.getMarketValue(),
            player.getEnergy(),
            player.getForm(),
            player.getInjured(),
            player.getInjuryType(),
            player.getInjuryRemainingMatches(),
            player.getOrigin().name(),
            player.calculateOverall()
        );
    }
}
