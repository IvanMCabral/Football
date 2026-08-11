package com.footballmanager.infrastructure.world.legacyseed;

import com.footballmanager.application.service.world.WorldSeedTeamWriter;
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
public class WorldTeamPostgresWriter implements WorldSeedTeamWriter {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(120);

    private final DatabaseClient databaseClient;
    private final LegacySeedPrincipalDatabaseGuard principalDatabaseGuard;

    public void upsertTeams(List<WorldTeam> teams, UUID leagueId, String logPrefix) {
        principalDatabaseGuard.assertLegacySeedCanWrite("world seed team upsert");
        int upserted = 0;
        int skipped = 0;
        int errors = 0;
        Instant now = Instant.now();
        ensureLeague(leagueId, teams, now, logPrefix);
        UUID syntheticManagerId = ensureSyntheticManager(leagueId, logPrefix);

        for (WorldTeam team : teams) {
            UUID teamId = team.getRealTeamId();
            if (teamId == null) {
                skipped++;
                continue;
            }

            try {
                databaseClient.sql("""
                    INSERT INTO teams (id, manager_id, name, country, formation, league_id, budget, division, created_at, updated_at)
                    VALUES (:id, :managerId, :name, :country, :formation, :leagueId, :budget, :division, :createdAt, :updatedAt)
                    ON CONFLICT (id) DO UPDATE SET
                        league_id = EXCLUDED.league_id,
                        division = EXCLUDED.division,
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
                    .bind("division", team.getDivision().name())
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

    private void ensureLeague(UUID leagueId, List<WorldTeam> teams, Instant now, String logPrefix) {
        if (leagueId == null) {
            return;
        }
        String country = teams.stream()
            .map(WorldTeam::getCountry)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("");
        String code = leagueCode(leagueId, logPrefix);
        String name = leagueName(code);
        int teamCount = Math.max(teams.size(), 1);
        try {
            databaseClient.sql("""
                INSERT INTO leagues (id, code, name, country, tier, team_count, status, created_at, updated_at)
                VALUES (:id, :code, :name, :country, 1, :teamCount, 'CREATED', :createdAt, :updatedAt)
                ON CONFLICT (id) DO UPDATE SET
                    code = EXCLUDED.code,
                    name = EXCLUDED.name,
                    country = EXCLUDED.country,
                    team_count = GREATEST(leagues.team_count, EXCLUDED.team_count),
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at
                """)
                .bind("id", leagueId)
                .bind("code", code)
                .bind("name", name)
                .bind("country", country)
                .bind("teamCount", teamCount)
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .fetch()
                .rowsUpdated()
                .block(BLOCK_TIMEOUT);
        } catch (Exception e) {
            log.error("{} league upsert failed for {}: {}", logPrefix, leagueId, e.getMessage(), e);
            throw e;
        }
    }

    private String leagueCode(UUID leagueId, String logPrefix) {
        if (logPrefix != null && logPrefix.contains("LA-LIGA")) {
            return "laliga";
        }
        return "seed-" + leagueId.toString().substring(0, 8);
    }

    private String leagueName(String code) {
        if ("laliga".equals(code)) {
            return "La Liga";
        }
        return "Seed League " + code.substring("seed-".length());
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
