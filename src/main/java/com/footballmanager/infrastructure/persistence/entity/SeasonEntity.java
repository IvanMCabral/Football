package com.footballmanager.infrastructure.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Table("seasons")
@Data
public class SeasonEntity {
    @Id
    private int id;
    private UUID externalId;
    @Column("season_year")
    private int seasonYear;
    private UUID leagueId;
    private String status;
    private LocalDate startsAt;
    private LocalDate endsAt;
    private Instant createdAt;
    private Instant updatedAt;
}
