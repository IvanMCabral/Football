package com.footballmanager.infrastructure.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.util.UUID;

@Table("seasons")
@Data
public class SeasonEntity {
    @Id
    private int id;
    private int year;
    private UUID leagueId;
    private String status;
    private Instant createdAt;
}
