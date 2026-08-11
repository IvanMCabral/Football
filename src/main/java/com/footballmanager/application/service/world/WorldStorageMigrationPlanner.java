package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only, fail-closed planner for legacy world migration. */
public final class WorldStorageMigrationPlanner {

    private final WorldStoragePhysicalCapacityModel physicalCapacityModel =
            new WorldStoragePhysicalCapacityModel();

    public MigrationPlan plan(WorldSnapshot legacy, WorldSnapshot proposedCanonical) {
        return plan(legacy, proposedCanonical, WorldReferenceGraph.empty());
    }

    public MigrationPlan plan(WorldSnapshot legacy, WorldSnapshot proposedCanonical,
                              WorldReferenceGraph references) {
        Objects.requireNonNull(legacy, "legacy snapshot is required");
        Objects.requireNonNull(proposedCanonical, "proposed canonical snapshot is required");
        Objects.requireNonNull(references, "reference graph is required");
        if (!Objects.equals(legacy.getUserId(), proposedCanonical.getUserId())) {
            return MigrationPlan.blocked("owner mismatch");
        }

        Resolution resolution = buildResolution(legacy, proposedCanonical);
        if (!resolution.collisions().isEmpty()) {
            return new MigrationPlan(Status.BLOCKED_REFERENCE_INCOMPATIBILITY, Set.of(), Set.of(),
                    Map.copyOf(resolution.aliases()), Map.of("collisions", resolution.collisions()),
                    "legacy identity collision; retain legacy blob");
        }

        Set<String> missingTeams = missing(legacy.getWorldTeams() == null ? Set.of()
                : legacy.getWorldTeams().keySet(), resolution.teamIds());
        Set<String> missingPlayers = missing(legacy.getWorldPlayers() == null ? Set.of()
                : legacy.getWorldPlayers().keySet(), resolution.playerIds());
        Map<String, Set<String>> referenceFailures = new LinkedHashMap<>();
        references.teamReferences().forEach((surface, ids) -> {
            Set<String> unresolved = missing(ids, resolution.teamIds());
            if (!unresolved.isEmpty()) referenceFailures.put(surface, unresolved);
        });
        references.playerReferences().forEach((surface, ids) -> {
            Set<String> unresolved = missing(ids, resolution.playerIds());
            if (!unresolved.isEmpty()) referenceFailures.put(surface, unresolved);
        });
        if (!missingTeams.isEmpty() || !missingPlayers.isEmpty() || !referenceFailures.isEmpty()) {
            return new MigrationPlan(Status.BLOCKED_REFERENCE_INCOMPATIBILITY,
                    missingTeams, missingPlayers, Map.copyOf(resolution.aliases()),
                    Map.copyOf(referenceFailures),
                    "legacy or active-career references are not resolvable; retain legacy blob");
        }
        return new MigrationPlan(Status.READY, Set.of(), Set.of(), Map.copyOf(resolution.aliases()),
                Map.of(), "all world and active-career references resolve; migration remains non-destructive");
    }

    public DryRunPlan dryRun(DryRunInput input, MigrationPlan referencePlan) {
        Objects.requireNonNull(input, "dry-run input is required");
        Objects.requireNonNull(referencePlan, "reference plan is required");
        WorldStoragePhysicalCapacityModel.Estimate estimate = physicalCapacityModel.estimate(
                input.currentDatasetBytes(), input.legacyBytes(), input.preparedBytes(),
                input.projectedCommittedBytes(), input.catalogBytes(), input.catalogAlreadyExists());
        long requiredAdditional = Math.max(0, estimate.physicalPeakBytes() - input.currentDatasetBytes());
        long guardedLimit = input.quotaBytes() - input.safetyMarginBytes()
                - input.localAccountingUncertaintyMarginBytes();
        long projectedPeak = estimate.physicalPeakBytes();
        boolean referencesValidated = referencePlan.ready();
        boolean feasible = referencesValidated && projectedPeak <= guardedLimit;
        String reason = !referencesValidated ? "reference graph is not safe"
                : feasible ? "quota-bound prepared migration fits"
                : "required peak exceeds guarded provider quota";
        return new DryRunPlan(input.owner(), input.sourceStorageVersion(), input.catalogHash(),
                input.legacyBytes(), input.projectedOverlayBytes(), input.projectedCommittedBytes(),
                input.catalogAlreadyExists(),
                referencesValidated, requiredAdditional,
                Math.max(0, input.quotaBytes() - input.currentDatasetBytes()),
                projectedPeak, feasible, reason);
    }

    private Resolution buildResolution(WorldSnapshot legacy, WorldSnapshot canonical) {
        Set<String> teams = new LinkedHashSet<>();
        if (canonical.getWorldTeams() != null) teams.addAll(canonical.getWorldTeams().keySet());
        Set<String> players = new LinkedHashSet<>();
        if (canonical.getWorldPlayers() != null) players.addAll(canonical.getWorldPlayers().keySet());
        Map<String, String> aliases = new LinkedHashMap<>();
        Set<String> collisions = new LinkedHashSet<>();

        if (legacy.getWorldTeams() != null) {
            legacy.getWorldTeams().forEach((legacyId, team) -> {
                if (team == null) {
                    collisions.add("missing-team:" + legacyId);
                } else if (team.getOrigin() == WorldTeam.WorldTeamOrigin.CUSTOM
                        || team.getRealTeamId() == null) {
                    if (teams.contains(legacyId) && !sameCustomTeam(team, canonical.getWorldTeam(legacyId))) {
                        collisions.add("team:" + legacyId);
                    }
                    else teams.add(legacyId);
                } else {
                    String canonicalId = team.getRealTeamId().toString();
                    if (teams.contains(canonicalId)) teams.add(legacyId);
                }
            });
        }
        if (legacy.getWorldPlayers() != null) {
            legacy.getWorldPlayers().forEach((legacyId, player) -> {
                if (player == null) {
                    collisions.add("missing-player:" + legacyId);
                    return;
                }
                if (player.getOrigin() != WorldPlayer.WorldPlayerOrigin.REAL
                        || player.getRealPlayerId() == null) {
                    if (players.contains(legacyId) && !sameCustomPlayer(player, canonical.getWorldPlayer(legacyId))) {
                        collisions.add("player:" + legacyId);
                    }
                    else players.add(legacyId);
                    return;
                }
                String canonicalId = WorldPlayer.stableCanonicalWorldPlayerId(legacy.getUserId(),
                        player.getRealPlayerId());
                if (!players.contains(canonicalId)) return;
                if (legacyId.equals(canonicalId)) {
                    players.add(legacyId);
                    return;
                }
                if (players.contains(legacyId) && !legacyId.equals(canonicalId)) {
                    collisions.add("alias:" + legacyId);
                    return;
                }
                String previous = aliases.putIfAbsent(legacyId, canonicalId);
                if (previous != null && !previous.equals(canonicalId)) collisions.add("alias:" + legacyId);
                players.add(legacyId);
            });
        }
        if (legacy.getWorldPlayerAliases() != null) {
            legacy.getWorldPlayerAliases().forEach((legacyId, targetId) -> {
                String canonicalId = resolveAlias(targetId, aliases);
                if (legacyId == null || canonicalId == null || !players.contains(canonicalId)
                        || (players.contains(legacyId) && !legacyId.equals(canonicalId))) {
                    collisions.add("alias:" + legacyId);
                } else {
                    String previous = aliases.putIfAbsent(legacyId, canonicalId);
                    if (previous != null && !previous.equals(canonicalId)) collisions.add("alias:" + legacyId);
                    players.add(legacyId);
                }
            });
        }
        return new Resolution(teams, players, aliases, collisions);
    }

    private static String resolveAlias(String target, Map<String, String> aliases) {
        if (target == null) return null;
        String current = target;
        Set<String> visited = new LinkedHashSet<>();
        while (aliases.containsKey(current) && !Objects.equals(aliases.get(current), current)
                && visited.add(current)) current = aliases.get(current);
        return visited.contains(current) ? null : current;
    }

    private static boolean sameCustomTeam(WorldTeam left, WorldTeam right) {
        if (left == right) return true;
        return left != null && right != null
                && Objects.equals(left.getWorldTeamId(), right.getWorldTeamId())
                && Objects.equals(left.getName(), right.getName())
                && Objects.equals(left.getCountry(), right.getCountry())
                && Objects.equals(left.getBaseBudget(), right.getBaseBudget())
                && Objects.equals(left.getBaseFormation(), right.getBaseFormation());
    }

    private static boolean sameCustomPlayer(WorldPlayer left, WorldPlayer right) {
        if (left == right) return true;
        return left != null && right != null
                && Objects.equals(left.getWorldPlayerId(), right.getWorldPlayerId())
                && Objects.equals(left.getName(), right.getName())
                && Objects.equals(left.getPosition(), right.getPosition())
                && Objects.equals(left.getAge(), right.getAge())
                && Objects.equals(left.getBaseAttack(), right.getBaseAttack())
                && Objects.equals(left.getBaseDefense(), right.getBaseDefense())
                && Objects.equals(left.getBaseTechnique(), right.getBaseTechnique());
    }

    private static Set<String> missing(Set<String> expected, Set<String> available) {
        Set<String> missing = new LinkedHashSet<>();
        expected.stream().filter(Objects::nonNull).filter(id -> !available.contains(id)).forEach(missing::add);
        return missing;
    }

    public record MigrationPlan(Status status, Set<String> missingTeamIds,
                                Set<String> missingPlayerIds, Map<String, String> legacyPlayerAliases,
                                Map<String, Set<String>> referenceFailures, String reason) {
        public boolean ready() { return status == Status.READY; }
        public static MigrationPlan blocked(String reason) {
            return new MigrationPlan(Status.BLOCKED_REFERENCE_INCOMPATIBILITY, Set.of(), Set.of(),
                    Map.of(), Map.of(), reason);
        }
    }

    public record DryRunInput(String owner, int sourceStorageVersion, String catalogHash,
                              long legacyBytes, long projectedOverlayBytes, long projectedCommittedBytes,
                              long catalogBytes,
                              long preparedBytes, boolean catalogAlreadyExists,
                              long currentDatasetBytes,
                              long quotaBytes, long safetyMarginBytes,
                              long localAccountingUncertaintyMarginBytes) {
        public DryRunInput(String owner, int sourceStorageVersion, String catalogHash,
                           long legacyBytes, long projectedOverlayBytes, long projectedCommittedBytes,
                           long catalogBytes, long preparedBytes, boolean catalogAlreadyExists,
                           long currentDatasetBytes, long quotaBytes, long safetyMarginBytes) {
            this(owner, sourceStorageVersion, catalogHash, legacyBytes, projectedOverlayBytes,
                    projectedCommittedBytes, catalogBytes, preparedBytes, catalogAlreadyExists,
                    currentDatasetBytes, quotaBytes, safetyMarginBytes,
                    WorldStoragePhysicalCapacityModel.DEFAULT_LOCAL_ACCOUNTING_UNCERTAINTY_MARGIN_BYTES);
        }
    }

    public record DryRunPlan(String owner, int sourceStorageVersion, String catalogHash,
                             long legacyBytes, long projectedOverlayBytes, long projectedCommittedBytes,
                             boolean catalogAlreadyExists, boolean referencesValidated,
                             long requiredPeakBytes, long availableHeadroom,
                             long projectedPeakBytes, boolean migrationFeasible,
                             String blockingReason) {}

    private record Resolution(Set<String> teamIds, Set<String> playerIds,
                              Map<String, String> aliases, Set<String> collisions) {}

    public enum Status { READY, BLOCKED_REFERENCE_INCOMPATIBILITY }
}
