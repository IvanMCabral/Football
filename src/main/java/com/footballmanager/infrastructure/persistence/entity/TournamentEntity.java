package com.footballmanager.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table("tournament")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TournamentEntity {
    @Id
    private UUID id;
    private String name;
    private String status;
    private UUID winnerTeamId;
}
