package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.valueobject.Division;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * distribution to {@link WorldTeam} instances in a {@link WorldSnapshot},
 *
 * {@link WorldTeam} so the frontend could surface division tiers (PRIMERA /
 * SEGUNDA / TERCERA). The seed services hardcoded
 * {@link Division#defaultDivision()} (=PRIMERA) for every team at create
 * downstream. That assumption was fragile because:
 *
 * <ol>
 *   <li>Seed paths can update Redis and Postgres in different moments.
 *   <li>Even for leagues whose teams DO reach Postgres, the
 *       {@code BuildWorldViewUseCase} read path returns the Redis
 *       {@code WorldSnapshot} verbatim — never re-queries Postgres for
 *       division. So Postgres may have 20/20/20 while the snapshot still
 *       shows 60/0/0 (PRIMERA uniform).</li>
 * </ol>
 *
 * in Java so the snapshot stores the correct per-league division BEFORE
 * {@code saveSnapshot} persists it to Redis. For a 60-team league:
 * top-20 (alphabetical by name) = PRIMERA, mid-20 = SEGUNDA, last-20 =
 * TERCERA. Idempotent (re-running produces the same distribution).
 *
 * remainders land in TERCERA. E.g. 58 teams → 19 PRIMERA + 19 SEGUNDA +
 * 20 TERCERA.
 *
 * <p>Implementation note: kept as a static utility (no Spring bean)
 * because the seed services have direct-constructor tests (e.g.
 * {@code LaLigaSeedServiceTest}) that mock the other dependencies; adding
 * a new Spring-managed field would force updates to ~3 test setUp
 * methods for a stateless function. Static keeps the surface minimal.
 */
public final class DivisionRankDistributor {

    private DivisionRankDistributor() {
        // Static utility — not instantiable.
    }

    /**
     * Apply the canonical per-league rank distribution to all REAL teams
     * in the snapshot. CUSTOM teams (no {@code realLeagueId}) are skipped
     * — they have no league to rank within. Mutates the snapshot in place.
     *
     * @param snapshot the WorldSnapshot to redistribute (may be null/empty,
     *                 in which case the call is a no-op)
     */
    public static void applyPerLeagueRankDivision(WorldSnapshot snapshot) {
        if (snapshot == null || snapshot.getWorldTeams() == null
                || snapshot.getWorldTeams().isEmpty()) {
            return;
        }

        // Group REAL teams by league. CUSTOM teams (realLeagueId=null) are
        // skipped — they have no canonical rank within a league.
        Map<UUID, List<WorldTeam>> byLeague = new HashMap<>();
        for (WorldTeam team : snapshot.getWorldTeams().values()) {
            if (team.getRealLeagueId() == null) {
                continue;
            }
            byLeague.computeIfAbsent(team.getRealLeagueId(), k -> new ArrayList<>())
                    .add(team);
        }

        for (List<WorldTeam> leagueTeams : byLeague.values()) {
            applyRankDistribution(leagueTeams);
        }
    }

    /**
     * Sort teams alphabetically by name (case-insensitive, null-safe), then
     * assign division by 1-indexed rank: rank <= total/3 → PRIMERA,
     * {@code ROW_NUMBER() OVER (PARTITION BY league_id ORDER BY name)}.
     *
     * <p>Mutates the input list in place and the contained
     * {@link WorldTeam#setDivision(Division)} values.
     *
     * @param leagueTeams teams in a single league; not null, may be empty
     */
    private static void applyRankDistribution(List<WorldTeam> leagueTeams) {
        if (leagueTeams.isEmpty()) {
            return;
        }
        leagueTeams.sort(Comparator.comparing(
                t -> t.getName() == null ? "" : t.getName().toLowerCase()));
        int total = leagueTeams.size();
        int oneThird = total / 3;
        int twoThirds = (total * 2) / 3;
        for (int rank = 0; rank < total; rank++) {
            int rn = rank + 1;
            Division tier;
            if (rn <= oneThird) {
                tier = Division.PRIMERA;
            } else if (rn <= twoThirds) {
                tier = Division.SEGUNDA;
            } else {
                tier = Division.TERCERA;
            }
            leagueTeams.get(rank).setDivision(tier);
        }
    }
}
