package com.footballmanager.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("team_squad")
public class TeamSquadEntity {
    @Id
    private Long id;
    private UUID teamId;
    private UUID playerId;
}
