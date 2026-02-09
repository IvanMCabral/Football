package com.footballmanager.infrastructure.persistence.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table("league_teams")
@Data
public class LeagueTeamEntity {
    private UUID leagueId;
    private UUID teamId;
}
