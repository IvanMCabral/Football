package com.footballmanager.domain.model.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * Owner-scoped mutable portion of a version-2 world representation.
 *
 * <p>The canonical catalog (real leagues, teams and players) is shared and
 * rebuildable. This overlay keeps only identity compatibility and mutable
 * owner state. It is deliberately a plain domain object so codecs and tests
 * can use it without Spring or Redis dependencies.</p>
 */
public class WorldSnapshotOverlay {

    public static final int STORAGE_VERSION = 2;

    /** Presence is separate from value so an explicit null is not confused with no delta. */
    public enum SnapshotField { CREATED_AT, LAST_UPDATED }

    private int storageVersion = STORAGE_VERSION;
    private UUID ownerId;
    private Set<SnapshotField> changedSnapshotFields = java.util.EnumSet.noneOf(SnapshotField.class);
    private Instant createdAt;
    private Instant lastUpdated;
    private List<WorldLeague> leagues = new ArrayList<>();
    private Map<String, WorldTeam> customTeams = new LinkedHashMap<>();
    private Map<String, WorldPlayer> customPlayers = new LinkedHashMap<>();
    private Map<UUID, String> canonicalPlayerIds = new LinkedHashMap<>();
    /** Legacy/random real-player ID -> deterministic canonical player ID. */
    private Map<String, String> legacyPlayerAliases = new LinkedHashMap<>();
    private Map<UUID, String> canonicalPlayerTeamIds = new LinkedHashMap<>();
    private Map<UUID, UUID> teamLeagueAssignments = new LinkedHashMap<>();
    private Map<UUID, WorldLeagueDelta> realLeagueDeltas = new LinkedHashMap<>();
    private List<WorldLeague> additionalLeagues = new ArrayList<>();
    private Set<UUID> removedCanonicalLeagueIds = new LinkedHashSet<>();
    private Map<UUID, WorldTeamDelta> realTeamDeltas = new LinkedHashMap<>();
    private Set<UUID> removedCanonicalTeamIds = new LinkedHashSet<>();
    private Map<UUID, WorldPlayerDelta> realPlayerDeltas = new LinkedHashMap<>();
    private Set<UUID> removedCanonicalPlayerIds = new LinkedHashSet<>();

    public static WorldSnapshotOverlay fromSnapshot(WorldSnapshot snapshot) {
        return fromSnapshot(snapshot, null);
    }

    /** Extracts only owner differences when the canonical rebuild is known. */
    public static WorldSnapshotOverlay fromSnapshot(WorldSnapshot snapshot, WorldSnapshot canonical) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        WorldSnapshotOverlay overlay = new WorldSnapshotOverlay();
        overlay.ownerId = snapshot.getUserId();
        overlay.captureSnapshotField(SnapshotField.CREATED_AT, snapshot.getCreatedAt(),
                canonical == null ? null : canonical.getCreatedAt());
        overlay.captureSnapshotField(SnapshotField.LAST_UPDATED, snapshot.getLastUpdated(),
                canonical == null ? null : canonical.getLastUpdated());
        extractLeagues(snapshot, canonical, overlay);
        if (snapshot.getWorldTeams() != null) {
            Set<UUID> retainedRealTeamIds = new LinkedHashSet<>();
            snapshot.getWorldTeams().forEach((id, team) -> {
                if (team == null) {
                    throw new IllegalStateException("World overlay contains a missing team");
                }
                if (team.getOrigin() == WorldTeam.WorldTeamOrigin.CUSTOM
                        || team.getRealTeamId() == null) {
                    overlay.customTeams.put(id, team);
                    return;
                }
                retainedRealTeamIds.add(team.getRealTeamId());
                WorldTeam canonicalTeam = findCanonicalTeam(canonical, team.getRealTeamId());
                if (canonicalTeam == null) {
                    // Lossless compatibility when no canonical rebuild was supplied, or
                    // when a real entity is not part of the current catalog.
                    overlay.customTeams.put(id, team);
                    return;
                }
                WorldTeamDelta delta = WorldTeamDelta.between(team, canonicalTeam);
                if (delta != null) {
                    overlay.realTeamDeltas.put(team.getRealTeamId(), delta);
                }
                if (!Objects.equals(canonicalTeam.getRealLeagueId(), team.getRealLeagueId())) {
                    overlay.teamLeagueAssignments.put(team.getRealTeamId(), team.getRealLeagueId());
                }
            });
            if (canonical != null && canonical.getWorldTeams() != null) {
                canonical.getWorldTeams().values().stream()
                        .filter(Objects::nonNull)
                        .map(WorldTeam::getRealTeamId)
                        .filter(Objects::nonNull)
                        .filter(id -> !retainedRealTeamIds.contains(id))
                        .forEach(overlay.removedCanonicalTeamIds::add);
            }
        }
        if (snapshot.getWorldPlayers() != null) {
            Set<UUID> retainedRealPlayerIds = new LinkedHashSet<>();
            snapshot.getWorldPlayers().forEach((id, player) -> {
                if (player == null) {
                    throw new IllegalStateException("World overlay contains a missing player");
                }
                if (player.getOrigin() != WorldPlayer.WorldPlayerOrigin.REAL
                        || player.getRealPlayerId() == null) {
                    overlay.customPlayers.put(id, player);
                } else {
                    retainedRealPlayerIds.add(player.getRealPlayerId());
                    String canonicalId = stableCanonicalId(snapshot.getUserId(), player.getRealPlayerId());
                    WorldPlayer canonicalPlayer = findCanonicalPlayer(canonical, player.getRealPlayerId());
                    if (canonicalPlayer == null) {
                        overlay.customPlayers.put(id, player);
                        return;
                    }
                    if (!Objects.equals(canonicalId, id)) {
                        overlay.putLegacyAlias(id, canonicalId);
                    }
                    WorldPlayerDelta delta = WorldPlayerDelta.between(player, canonicalPlayer);
                    if (delta != null) {
                        overlay.realPlayerDeltas.put(player.getRealPlayerId(), delta);
                    }
                    if (!Objects.equals(canonicalPlayer.getWorldTeamId(), player.getWorldTeamId())) {
                        overlay.canonicalPlayerTeamIds.put(player.getRealPlayerId(), player.getWorldTeamId());
                    }
                }
            });
            if (canonical != null && canonical.getWorldPlayers() != null) {
                canonical.getWorldPlayers().values().stream()
                        .filter(Objects::nonNull)
                        .map(WorldPlayer::getRealPlayerId)
                        .filter(Objects::nonNull)
                        .filter(id -> !retainedRealPlayerIds.contains(id))
                        .forEach(overlay.removedCanonicalPlayerIds::add);
            }
        }
        if (snapshot.getWorldPlayerAliases() != null) {
            snapshot.getWorldPlayerAliases().forEach(overlay::putLegacyAlias);
        }
        return overlay;
    }

    private static void extractLeagues(WorldSnapshot snapshot, WorldSnapshot canonical,
                                       WorldSnapshotOverlay overlay) {
        if (snapshot.getLeagues() == null) return;
        if (canonical == null || canonical.getLeagues() == null) {
            overlay.leagues = new ArrayList<>(snapshot.getLeagues());
            return;
        }
        Map<UUID, WorldLeague> canonicalById = new LinkedHashMap<>();
        canonical.getLeagues().stream().filter(Objects::nonNull)
                .filter(league -> league.getRealLeagueId() != null)
                .forEach(league -> canonicalById.put(league.getRealLeagueId(), league));
        Set<UUID> retained = new LinkedHashSet<>();
        for (WorldLeague league : snapshot.getLeagues()) {
            if (league == null) throw new IllegalStateException("World overlay contains a missing league");
            WorldLeague base = league.getRealLeagueId() == null ? null : canonicalById.get(league.getRealLeagueId());
            if (base == null) {
                overlay.additionalLeagues.add(league);
            } else {
                retained.add(league.getRealLeagueId());
                WorldLeagueDelta delta = WorldLeagueDelta.between(league, base);
                if (delta != null) overlay.realLeagueDeltas.put(league.getRealLeagueId(), delta);
            }
        }
        canonicalById.keySet().stream().filter(id -> !retained.contains(id))
                .forEach(overlay.removedCanonicalLeagueIds::add);
    }

    private static WorldTeam findCanonicalTeam(WorldSnapshot canonical, UUID realTeamId) {
        if (canonical == null || canonical.getWorldTeams() == null) return null;
        return canonical.getWorldTeams().values().stream().filter(Objects::nonNull)
                .filter(team -> Objects.equals(realTeamId, team.getRealTeamId())).findFirst().orElse(null);
    }

    private static WorldPlayer findCanonicalPlayer(WorldSnapshot canonical, UUID realPlayerId) {
        if (canonical == null || canonical.getWorldPlayers() == null) return null;
        return canonical.getWorldPlayers().values().stream().filter(Objects::nonNull)
                .filter(player -> Objects.equals(realPlayerId, player.getRealPlayerId())).findFirst().orElse(null);
    }

    private static String stableCanonicalId(UUID ownerId, UUID realPlayerId) {
        return WorldPlayer.stableCanonicalWorldPlayerId(ownerId, realPlayerId);
    }

    /** Applies the owner overlay to a canonical snapshot in memory. */
    public WorldSnapshot applyTo(WorldSnapshot canonical) {
        Objects.requireNonNull(canonical, "canonical snapshot cannot be null");
        if (storageVersion != STORAGE_VERSION) {
            throw new IllegalStateException("Unsupported world overlay storage version");
        }
        if (ownerId == null) {
            throw new IllegalStateException("World overlay owner is required");
        }
        canonical.setUserId(ownerId);
        if (snapshotFieldChanged(SnapshotField.CREATED_AT)) {
            canonical.setCreatedAt(createdAt);
        } else if (createdAt != null) {
            // Backward compatibility for envelopes written before presence metadata existed.
            canonical.setCreatedAt(createdAt);
        }
        if (snapshotFieldChanged(SnapshotField.LAST_UPDATED)) {
            canonical.setLastUpdated(lastUpdated);
        } else if (lastUpdated != null) {
            canonical.setLastUpdated(lastUpdated);
        }
        if (leagues != null && !leagues.isEmpty()) {
            canonical.setLeagues(new ArrayList<>(leagues));
        } else {
            List<WorldLeague> reconstructedLeagues = new ArrayList<>();
            if (canonical.getLeagues() != null) {
                for (WorldLeague league : canonical.getLeagues()) {
                    if (league == null || removedCanonicalLeagueIds.contains(league.getRealLeagueId())) continue;
                    WorldLeagueDelta delta = realLeagueDeltas.get(league.getRealLeagueId());
                    if (delta != null) delta.applyTo(league);
                    reconstructedLeagues.add(league);
                }
            }
            reconstructedLeagues.addAll(additionalLeagues);
            canonical.setLeagues(reconstructedLeagues);
        }

        Map<String, WorldTeam> teams = new LinkedHashMap<>();
        if (canonical.getWorldTeams() != null) {
            canonical.getWorldTeams().forEach((id, team) -> {
                if (team == null || removedCanonicalTeamIds.contains(team.getRealTeamId())) return;
                WorldTeamDelta delta = realTeamDeltas.get(team.getRealTeamId());
                if (delta != null) delta.applyTo(team);
                teams.put(id, team);
            });
        }
        customTeams.forEach((id, team) -> {
            WorldTeam existing = teams.putIfAbsent(id, team);
            if (existing != null && existing != team) {
                throw new IllegalStateException("World overlay team identity collision");
            }
        });
        if (!teams.isEmpty()) {
            teams.values().forEach(team -> {
                if (team == null || team.getRealTeamId() == null) return;
                if (teamLeagueAssignments.containsKey(team.getRealTeamId())) {
                    team.setRealLeagueId(teamLeagueAssignments.get(team.getRealTeamId()));
                }
            });
        }
        canonical.setWorldTeams(teams);

        Map<String, WorldPlayer> players = new LinkedHashMap<>();
        if (canonical.getWorldPlayers() != null) {
            canonical.getWorldPlayers().values().forEach(player -> {
                if (player == null || player.getRealPlayerId() == null
                        || removedCanonicalPlayerIds.contains(player.getRealPlayerId())) return;
                WorldPlayerDelta delta = realPlayerDeltas.get(player.getRealPlayerId());
                if (delta != null) delta.applyTo(player);
                if (canonicalPlayerTeamIds.containsKey(player.getRealPlayerId())) {
                    player.setWorldTeamId(canonicalPlayerTeamIds.get(player.getRealPlayerId()));
                }
            });
            canonical.getWorldPlayers().values().forEach(player -> {
                if (player != null && !removedCanonicalPlayerIds.contains(player.getRealPlayerId())) {
                    WorldPlayer existing = players.putIfAbsent(player.getWorldPlayerId(), player);
                    if (existing != null && existing != player) {
                        throw new IllegalStateException("World overlay player identity collision");
                    }
                }
            });
        }
        customPlayers.forEach((id, player) -> {
            WorldPlayer existing = players.putIfAbsent(id, player);
            if (existing != null && existing != player) {
                throw new IllegalStateException("World overlay player identity collision");
            }
        });
        canonical.setWorldPlayers(players);
        Map<String, String> aliases = new LinkedHashMap<>();
        canonicalPlayerIds.forEach((realId, legacyId) -> {
            String canonicalId = stableCanonicalId(ownerId, realId);
            if (!Objects.equals(legacyId, canonicalId)) putValidatedAlias(aliases, players, legacyId, canonicalId);
        });
        legacyPlayerAliases.forEach((legacyId, canonicalId) ->
                putValidatedAlias(aliases, players, legacyId,
                        resolveLegacyTarget(canonicalId, legacyPlayerAliases)));
        canonical.setWorldPlayerAliases(aliases);
        return canonical;
    }

    private void putLegacyAlias(String legacyId, String canonicalId) {
        if (legacyId == null || canonicalId == null || legacyId.equals(canonicalId)) {
            throw new IllegalStateException("World overlay legacy self-alias is invalid");
        }
        String previous = legacyPlayerAliases.putIfAbsent(legacyId, canonicalId);
        if (previous != null && !previous.equals(canonicalId)) {
            throw new IllegalStateException("World overlay legacy alias collision");
        }
    }

    private static void putValidatedAlias(Map<String, String> aliases,
                                          Map<String, WorldPlayer> players,
                                          String legacyId,
                                          String canonicalId) {
        if (legacyId == null || canonicalId == null || !players.containsKey(canonicalId)) {
            throw new IllegalStateException("World overlay legacy alias target is invalid");
        }
        if (players.containsKey(legacyId) && !legacyId.equals(canonicalId)) {
            throw new IllegalStateException("World overlay legacy alias collides with a player");
        }
        String previous = aliases.putIfAbsent(legacyId, canonicalId);
        if (previous != null && !previous.equals(canonicalId)) {
            throw new IllegalStateException("World overlay legacy alias collision");
        }
    }

    private static String resolveLegacyTarget(String target, Map<String, String> aliases) {
        String current = target;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && aliases.containsKey(current) && visited.add(current)) {
            current = aliases.get(current);
        }
        if (current == null || !visited.add(current)) {
            throw new IllegalStateException("World overlay legacy alias cycle is invalid");
        }
        return current;
    }

    private void captureSnapshotField(SnapshotField field, Instant current, Instant canonical) {
        if (canonical == null || !Objects.equals(current, canonical)) {
            changedSnapshotFields.add(field);
            if (field == SnapshotField.CREATED_AT) createdAt = current;
            if (field == SnapshotField.LAST_UPDATED) lastUpdated = current;
        }
    }

    private boolean snapshotFieldChanged(SnapshotField field) {
        return changedSnapshotFields != null && changedSnapshotFields.contains(field);
    }

    public int getStorageVersion() { return storageVersion; }
    public void setStorageVersion(int storageVersion) { this.storageVersion = storageVersion; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public Set<SnapshotField> getChangedSnapshotFields() { return changedSnapshotFields; }
    public void setChangedSnapshotFields(Set<SnapshotField> changedSnapshotFields) {
        this.changedSnapshotFields = changedSnapshotFields == null || changedSnapshotFields.isEmpty()
                ? java.util.EnumSet.noneOf(SnapshotField.class)
                : java.util.EnumSet.copyOf(changedSnapshotFields);
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    public List<WorldLeague> getLeagues() { return leagues; }
    public void setLeagues(List<WorldLeague> leagues) { this.leagues = leagues == null ? new ArrayList<>() : leagues; }
    public Map<String, WorldTeam> getCustomTeams() { return customTeams; }
    public void setCustomTeams(Map<String, WorldTeam> customTeams) { this.customTeams = customTeams == null ? new LinkedHashMap<>() : customTeams; }
    public Map<String, WorldPlayer> getCustomPlayers() { return customPlayers; }
    public void setCustomPlayers(Map<String, WorldPlayer> customPlayers) { this.customPlayers = customPlayers == null ? new LinkedHashMap<>() : customPlayers; }
    public Map<UUID, String> getCanonicalPlayerIds() { return canonicalPlayerIds; }
    public void setCanonicalPlayerIds(Map<UUID, String> canonicalPlayerIds) { this.canonicalPlayerIds = canonicalPlayerIds == null ? new LinkedHashMap<>() : canonicalPlayerIds; }
    public Map<String, String> getLegacyPlayerAliases() { return legacyPlayerAliases; }
    public void setLegacyPlayerAliases(Map<String, String> legacyPlayerAliases) { this.legacyPlayerAliases = legacyPlayerAliases == null ? new LinkedHashMap<>() : legacyPlayerAliases; }
    public Map<UUID, String> getCanonicalPlayerTeamIds() { return canonicalPlayerTeamIds; }
    public void setCanonicalPlayerTeamIds(Map<UUID, String> canonicalPlayerTeamIds) { this.canonicalPlayerTeamIds = canonicalPlayerTeamIds == null ? new LinkedHashMap<>() : canonicalPlayerTeamIds; }
    public Map<UUID, UUID> getTeamLeagueAssignments() { return teamLeagueAssignments; }
    public void setTeamLeagueAssignments(Map<UUID, UUID> teamLeagueAssignments) { this.teamLeagueAssignments = teamLeagueAssignments == null ? new LinkedHashMap<>() : teamLeagueAssignments; }
    public Map<UUID, WorldLeagueDelta> getRealLeagueDeltas() { return realLeagueDeltas; }
    public void setRealLeagueDeltas(Map<UUID, WorldLeagueDelta> realLeagueDeltas) { this.realLeagueDeltas = realLeagueDeltas == null ? new LinkedHashMap<>() : realLeagueDeltas; }
    public List<WorldLeague> getAdditionalLeagues() { return additionalLeagues; }
    public void setAdditionalLeagues(List<WorldLeague> additionalLeagues) { this.additionalLeagues = additionalLeagues == null ? new ArrayList<>() : additionalLeagues; }
    public Set<UUID> getRemovedCanonicalLeagueIds() { return removedCanonicalLeagueIds; }
    public void setRemovedCanonicalLeagueIds(Set<UUID> removedCanonicalLeagueIds) { this.removedCanonicalLeagueIds = removedCanonicalLeagueIds == null ? new LinkedHashSet<>() : removedCanonicalLeagueIds; }
    public Map<UUID, WorldTeamDelta> getRealTeamDeltas() { return realTeamDeltas; }
    public void setRealTeamDeltas(Map<UUID, WorldTeamDelta> realTeamDeltas) { this.realTeamDeltas = realTeamDeltas == null ? new LinkedHashMap<>() : realTeamDeltas; }
    public Set<UUID> getRemovedCanonicalTeamIds() { return removedCanonicalTeamIds; }
    public void setRemovedCanonicalTeamIds(Set<UUID> removedCanonicalTeamIds) { this.removedCanonicalTeamIds = removedCanonicalTeamIds == null ? new LinkedHashSet<>() : removedCanonicalTeamIds; }
    public Map<UUID, WorldPlayerDelta> getRealPlayerDeltas() { return realPlayerDeltas; }
    public void setRealPlayerDeltas(Map<UUID, WorldPlayerDelta> realPlayerDeltas) { this.realPlayerDeltas = realPlayerDeltas == null ? new LinkedHashMap<>() : realPlayerDeltas; }
    public Set<UUID> getRemovedCanonicalPlayerIds() { return removedCanonicalPlayerIds; }
    public void setRemovedCanonicalPlayerIds(Set<UUID> removedCanonicalPlayerIds) { this.removedCanonicalPlayerIds = removedCanonicalPlayerIds == null ? new LinkedHashSet<>() : removedCanonicalPlayerIds; }
}
