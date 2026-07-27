package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldTeam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorldTeamPostgresWriter {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(120);

    private final DatabaseClient databaseClient;

    public void upsertTeams(List<WorldTeam> teams, UUID leagueId, String logPrefix) {
        int upserted = 0;
        int skipped = 0;
        int errors = 0;
        Instant now = Instant.now();
        UUID syntheticManagerId = ensureSyntheticManager(leagueId, logPrefix);

        for (WorldTeam team : teams) {
            UUID teamId = team.getRealTeamId();
            if (teamId == null) {
                skipped++;
                continue;
            }

            try {
                databaseClient.sql("""
                    INSERT INTO teams (id, manager_id, name, country, formation, league_id, budget, created_at, updated_at)
                    VALUES (:id, :managerId, :name, :country, :formation, :leagueId, :budget, :createdAt, :updatedAt)
                    ON CONFLICT (id) DO UPDATE SET
                        league_id = EXCLUDED.league_id,
                        updated_at = EXCLUDED.updated_at
                    """)
                    .bind("id", teamId)
                    .bind("managerId", syntheticManagerId)
                    .bind("name", valueOrDefault(team.getName(), "Unknown"))
                    .bind("country", valueOrDefault(team.getCountry(), ""))
                    .bind("formation", valueOrDefault(team.getBaseFormation(), "4-3-3"))
                    .bind("leagueId", leagueId)
                    .bind("budget", team.getBaseBudget() == null
                            ? java.math.BigDecimal.valueOf(10_000_000L)
                            : team.getBaseBudget())
                    .bind("createdAt", now)
                    .bind("updatedAt", now)
                    .fetch()
                    .rowsUpdated()
                    .block(BLOCK_TIMEOUT);
                upserted++;
            } catch (Exception e) {
                log.error("{} team upsert failed for {} (id={}): {}",
                        logPrefix, team.getName(), teamId, e.getMessage(), e);
                errors++;
            }
        }

        log.info("{} postgres teams upsert: total={}, upserted={}, skipped={}, errors={}",
                logPrefix, teams.size(), upserted, skipped, errors);
    }

    private UUID ensureSyntheticManager(UUID leagueId, String logPrefix) {
        UUID managerId = UUID.nameUUIDFromBytes(("synthetic-manager|" + leagueId).getBytes());
        Instant now = Instant.now();

        try {
            databaseClient.sql("""
                INSERT INTO users (id, username, email, password_hash, created_at, updated_at)
                VALUES (:id, :username, :email, :password, :createdAt, :updatedAt)
                ON CONFLICT (id) DO NOTHING
                """)
                .bind("id", managerId)
                .bind("username", "synthetic_" + leagueId.toString().substring(0, 8))
                .bind("email", "synthetic_" + leagueId.toString().substring(0, 8) + "@synthetic.local")
                .bind("password", "synthetic_no_login")
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .fetch()
                .rowsUpdated()
                .block(BLOCK_TIMEOUT);
        } catch (Exception e) {
            log.warn("{} synthetic manager insert failed: {}", logPrefix, e.getMessage());
        }

        return managerId;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
