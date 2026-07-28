package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.entity.career.*;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerSeasonManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.service.SessionTeamRankingPolicy;

import java.time.Instant;
import java.util.*;

/**
 * CareerSave - Save game completo de una carrera.
 * Se persiste en Redis con clave: career:{userId}
 *
 * Delegated responsibilities:
 * - CareerData: metadata y configuración
 * - CareerTeamManager: equipos y squads
 * - CareerPlayerManager: jugadores
 * - CareerSeasonManager: temporadas y divisiones
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CareerSave {

    private CareerData data = new CareerData();
    private CareerTeamManager teamManager = new CareerTeamManager();
    private CareerPlayerManager playerManager = new CareerPlayerManager();
    private CareerSeasonManager seasonManager = new CareerSeasonManager();
    private Map<String, List<String>> teamStarting11 = new HashMap<>();
    /**
     * MVP1-lineup-cancha-1: subdivisionId por jugador (mapa interno:
     * teamId → { subdivisionId → LineupSlot }).
     * Paralelo a {@link #teamStarting11} — no lo reemplaza. Si está vacío
     * o ausente para un team, se infiere on-the-fly del role del jugador
     * (backward compat con lineups viejos).
     *
     * {@code Map<String, Map<String, String>>} (subdivisionId → playerId,
     * which lost the front's free-positioning customX/customY) to a
     * raw-typed Object map that holds {@link LineupSlot} values.
     * {@code new LineupSlot(playerId, null, null, null)} on read by
     * the typed getter {@link #getTeamStarting11SubdivisionSlots()}. The
     * legacy {@code Map<String, String>} setter
     * {@link #setTeamStarting11Subdivision(Map)} is preserved for backward
     *
     * <p>Field declared as {@code Map<String, Map<String, Object>>} so
     * are plain strings) and post-fix saves (inner values are
     * LineupSlot records) into the same field. Conversion to
     * LineupSlot happens lazily in the typed getter / on write.
     */
    private Map<String, Map<String, Object>> teamStarting11Subdivision = new HashMap<>();
    /**
     * MVP1-lineup-cancha-1.6: formation code persistido por team
     * (teamId → formation code, ej. "4-3-3"). El front puede cambiar la
     * formación sin reasignar jugadores — sin este campo, {@code getCurrentLineup}
     * recomputaba la formación contando DEF/MID/ATT de la lineup persistida
     * (que seguía siendo la original) y devolvía el código viejo. Si está
     * vacío o ausente para un team (saves de sprint 1.5 o anteriores), se
     * hace fallback a {@code lineupHelper.inferFormation(lineup)} en read path.
     */
    private Map<String, String> teamStarting11Formation = new HashMap<>();
    private TournamentState tournamentState = new TournamentState();

    // Setters requeridos para deserialización JSON
    public void setData(CareerData data) { this.data = data; }
    public void setTeamManager(CareerTeamManager teamManager) { this.teamManager = teamManager; }
    public void setPlayerManager(CareerPlayerManager playerManager) { this.playerManager = playerManager; }
    public void setSeasonManager(CareerSeasonManager seasonManager) {
        this.seasonManager = seasonManager;
    }
    public void setTeamStarting11(Map<String, List<String>> starting11) {
        this.teamStarting11.clear();
        this.teamStarting11.putAll(starting11);
    }
    public void setTeamStarting11Subdivision(Map<String, Map<String, String>> slots) {
        // (subdivisionId -> playerId) String shape and stores each String
        // value as a raw Object in the new typed-raw field. The typed
        // getter getTeamStarting11SubdivisionSlots() converts these on
        // demand (or callers can use the new slot-based setter below for
        // customX/Y persistence).
        Map<String, Map<String, Object>> raw = new HashMap<>();
        if (slots != null) {
            for (Map.Entry<String, Map<String, String>> e : slots.entrySet()) {
                Map<String, Object> inner = new HashMap<>();
                if (e.getValue() != null) {
                    for (Map.Entry<String, String> ie : e.getValue().entrySet()) {
                        inner.put(ie.getKey(), ie.getValue());
                    }
                }
                raw.put(e.getKey(), inner);
            }
        }
        this.teamStarting11Subdivision = raw;
    }

    /**
     * {@code LineupCommandUseCaseImpl} write paths so the front's free-
     * positioning {@code customXPercent} / {@code customYPercent} values
     * are persisted (pre-fix, the String-only setter dropped them).
     *
     * <p>Backed by the same raw-typed field as the legacy setter — both
     * shapes are normalized to {@code Map<String, Map<String, Object>>}
     * internally.
     */
    public void setTeamStarting11SubdivisionSlots(Map<String, Map<String, LineupSlot>> slots) {
        Map<String, Map<String, Object>> raw = new HashMap<>();
        if (slots != null) {
            for (Map.Entry<String, Map<String, LineupSlot>> e : slots.entrySet()) {
                Map<String, Object> inner = new HashMap<>();
                if (e.getValue() != null) {
                    for (Map.Entry<String, LineupSlot> ie : e.getValue().entrySet()) {
                        inner.put(ie.getKey(), ie.getValue());
                    }
                }
                raw.put(e.getKey(), inner);
            }
        }
        this.teamStarting11Subdivision = raw;
    }

    /**
     * directly for a single teamId. This bypasses the
     * typed-raw-conversion round-trip (which the unit test mocks but
     * the runtime JSON serialization+deserialization does not).
     *
     * <p>Use this in {@code autoSelectLineup} so the persisted
     * (serialized) shape exactly matches the new slotMap, with no
     * risk of stale keys surviving a clear-and-rebuild on a
     * separately-allocated typed map.
     *
     * @param teamId the user/team key (e.g. session team id).
     * @param slots the new slot map. If null/empty, the entry is removed
     *              entirely.
     */
    public void replaceTeamStarting11SubdivisionRaw(String teamId,
                                                     Map<String, LineupSlot> slots) {
        if (teamStarting11Subdivision == null) {
            teamStarting11Subdivision = new HashMap<>();
        }
        if (slots == null || slots.isEmpty()) {
            teamStarting11Subdivision.remove(teamId);
            return;
        }
        Map<String, Object> inner = teamStarting11Subdivision.computeIfAbsent(
            teamId, k -> new HashMap<>());
        inner.clear();
        for (Map.Entry<String, LineupSlot> e : slots.entrySet()) {
            inner.put(e.getKey(), e.getValue());
        }
    }
    public void setTeamStarting11Formation(Map<String, String> formation) {
        this.teamStarting11Formation = (formation == null) ? new HashMap<>() : new HashMap<>(formation);
    }
    public void setTournamentState(TournamentState state) { this.tournamentState = state; }

    // ========== Core accessors (for services) ==========

    public CareerData getData() { return data; }
    public CareerTeamManager getTeamManager() { return teamManager; }
    public CareerPlayerManager getPlayerManager() { return playerManager; }
    public CareerSeasonManager getSeasonManager() { return seasonManager; }
    public Map<String, List<String>> getTeamStarting11() { return teamStarting11; }

    /**
     * shape (subdivisionId → playerId). Wraps any LineupSlot values back to
     * their {@code playerId} for callers that haven't migrated. Persists raw
     * String values as-is.
     *
     * <p><b>Prefer {@link #getTeamStarting11SubdivisionSlots()}</b> for the
     * new slot-aware shape (it preserves {@code customX/Y} for the engine's
     * distance-from-ideal penalty).
     */
    public Map<String, Map<String, String>> getTeamStarting11Subdivision() {
        if (teamStarting11Subdivision == null) {
            teamStarting11Subdivision = new HashMap<>();
        }
        Map<String, Map<String, String>> legacy = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : teamStarting11Subdivision.entrySet()) {
            if (e.getValue() == null) {
                legacy.put(e.getKey(), new HashMap<>());
                continue;
            }
            Map<String, String> inner = new HashMap<>(e.getValue().size());
            for (Map.Entry<String, Object> ie : e.getValue().entrySet()) {
                Object v = ie.getValue();
                if (v instanceof LineupSlot slot) {
                    inner.put(ie.getKey(), slot.playerId());
                } else if (v instanceof String s) {
                    inner.put(ie.getKey(), s);
                }
                // Unknown shape: skip (defensive).
            }
            legacy.put(e.getKey(), inner);
        }
        return legacy;
    }

    /**
     * {@code subdivisionId → LineupSlot} with the front's
     * {@code customXPercent / customYPercent} preserved.
     *
     * {@code subdivisionId → playerId}) are wrapped to
     * {@code new LineupSlot(playerId, null, null, null)} so downstream
     * consumers can rely on the LineupSlot shape uniformly.
     */
    public Map<String, Map<String, LineupSlot>> getTeamStarting11SubdivisionSlots() {
        if (teamStarting11Subdivision == null) {
            teamStarting11Subdivision = new HashMap<>();
        }
        Map<String, Map<String, LineupSlot>> typed = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : teamStarting11Subdivision.entrySet()) {
            if (e.getValue() == null) {
                typed.put(e.getKey(), new HashMap<>());
                continue;
            }
            Map<String, LineupSlot> inner = new HashMap<>(e.getValue().size());
            for (Map.Entry<String, Object> ie : e.getValue().entrySet()) {
                Object v = ie.getValue();
                if (v instanceof LineupSlot slot) {
                    inner.put(ie.getKey(), slot);
                } else if (v instanceof String playerId) {
                    // OUTER key, so the wrapped LineupSlot has
                    // subdivisionId=null (the consumer can recover it
                    // from the outer key or the LineupDTO's slots list).
                    inner.put(ie.getKey(), new LineupSlot(playerId, null, null, null));
                }
                // Unknown shape: skip (defensive).
            }
            typed.put(e.getKey(), inner);
        }
        return typed;
    }
    /**
     * MVP1-lineup-cancha-1.6: formación persistida por team. Devuelve
     * un mapa vacío si el save es viejo (no contiene el campo) —
     * el read path hace fallback a {@code lineupHelper.inferFormation}.
     */
    public Map<String, String> getTeamStarting11Formation() {
        if (teamStarting11Formation == null) {
            teamStarting11Formation = new HashMap<>();
        }
        return teamStarting11Formation;
    }
    public TournamentState getTournamentState() { return tournamentState; }

    // ========== Metadata convenience ==========

    public String getCareerId() { return data.getCareerId(); }
    public UUID getUserId() { return data.getUserId(); }
    public void setUserId(UUID userId) { data.setUserId(userId); }
    public UUID getUserTeamId() { return data.getUserTeamId(); }
    public void setUserTeamId(UUID id) { data.setUserTeamId(id); }
    public String getUserSessionTeamId() { return data.getUserSessionTeamId(); }
    public void setUserSessionTeamId(String id) { data.setUserSessionTeamId(id); }
    public Instant getLastUpdated() { return data.getLastUpdated(); }
    public String getDifficulty() { return data.getDifficulty(); }
    public void setDifficulty(String d) { data.setDifficulty(d); }
    public String getGameSpeed() { return data.getGameSpeed(); }
    public void setGameSpeed(String s) { data.setGameSpeed(s); }

    // ========== Convenience delegation (for backward compatibility) ==========

    public void addSessionTeam(SessionTeam team) { teamManager.addSessionTeam(team); data.touch(); }
    public SessionTeam getSessionTeam(String id) { return teamManager.getSessionTeam(id); }
    public List<SessionTeam> getAllSessionTeams() { return teamManager.getAllSessionTeams(); }

    public void addSessionPlayer(SessionPlayer player) { playerManager.addSessionPlayer(player); data.touch(); }
    public SessionPlayer getSessionPlayer(String id) { return playerManager.getSessionPlayer(id); }
    public List<SessionPlayer> getTeamSquad(String sessionTeamId) {
        return playerManager.getTeamSquad(teamManager.getSquadPlayerIds(sessionTeamId));
    }

    public void startNewSeason() { seasonManager.startNewSeason(); data.touch(); }
    public int getCurrentSeason() { return seasonManager.getCurrentSeason(); }
    public void setCurrentSeason(int season) { seasonManager.setCurrentSeason(season); }

    public Division getUserDivision() {
        String userSessionTeamId = data.getUserSessionTeamId();
        return seasonManager.findDivisionByTeamId(userSessionTeamId);
    }
    public int getTotalDivisions() { return seasonManager.getTotalDivisions(); }

    public void setPalmares(java.util.List<TournamentResult> palmares) { seasonManager.setPalmares(palmares); }
    public void addTournamentResult(TournamentResult result) { seasonManager.addTournamentResult(result); data.touch(); }

    public java.util.List<Promotion> getPromotions() { return seasonManager.getPromotions(); }
    public void updateTopTeams(TournamentResult result) { seasonManager.updateTopTeams(result); data.touch(); }

    public void executePromotionsAndRelegations() { seasonManager.executePromotionsAndRelegations(); data.touch(); }
    public List<Promotion> calculatePromotionsAndRelegations(TournamentState state) { return seasonManager.calculatePromotionsAndRelegations(state); }

    // ========== Team/Squad delegation ==========

    public List<String> getSquadPlayerIds(String sessionTeamId) { return teamManager.getSquadPlayerIds(sessionTeamId); }
    public String findSessionTeamIdByWorldTeamId(String worldTeamId) { return teamManager.findSessionTeamIdByWorldTeamId(worldTeamId); }
    public void removeSessionTeam(String id) { teamManager.removeSessionTeam(id); data.touch(); }
    public void assignPlayerToTeam(String sessionPlayerId, String sessionTeamId) {
        teamManager.assignPlayerToSquad(sessionPlayerId, sessionTeamId);
        playerManager.removeFromFreePlayers(sessionPlayerId);
        data.touch();
    }
    public void removePlayerFromTeam(String sessionPlayerId, String sessionTeamId) {
        teamManager.removePlayerFromSquad(sessionPlayerId, sessionTeamId);
        data.touch();
    }

    // ========== Player delegation ==========

    public Map<String, SessionPlayer> getSessionPlayers() { return playerManager.getSessionPlayers(); }
    public List<String> getFreePlayers() { return playerManager.getFreePlayerIds(); }
    public List<SessionPlayer> getFreePlayersObjects() { return playerManager.getFreePlayersObjects(); }
    public Map<String, Set<String>> getRemovedPlayers() { return playerManager.getRemovedPlayers(); }
    public Set<String> getRemovedPlayerIds(String worldTeamId) { return playerManager.getRemovedPlayerIds(worldTeamId); }
    public String findSessionPlayerIdByWorldPlayerId(String worldPlayerId) { return playerManager.findSessionPlayerIdByWorldPlayerId(worldPlayerId); }
    public void markPlayerAsRemoved(String worldTeamId, String worldPlayerId) { playerManager.markPlayerAsRemoved(worldTeamId, worldPlayerId); data.touch(); }
    public void removePlayer(String sessionPlayerId) {
        playerManager.removePlayer(sessionPlayerId);
        playerManager.removePlayerFromAllStarting11(teamStarting11, sessionPlayerId);
        // MVP1-lineup-cancha-1: también limpiar de subdivision map (si estaba).
        // String (legacy). Iterate the raw field and unwrap per value.
        for (Map<String, Object> slots : teamStarting11Subdivision.values()) {
            if (slots == null) continue;
            slots.entrySet().removeIf(e -> {
                Object v = e.getValue();
                if (v instanceof LineupSlot slot) {
                    return sessionPlayerId.equals(slot.playerId());
                }
                if (v instanceof String s) {
                    return sessionPlayerId.equals(s);
                }
                return false;
            });
        }
        teamManager.removePlayerFromAllSquads(sessionPlayerId);
        data.touch();
    }
    public void addToFreePlayers(String sessionPlayerId) { playerManager.addToFreePlayers(sessionPlayerId); data.touch(); }

    // ========== Season/Division delegation ==========

    public void assignTeamsToDivisions(int teamsPerDivision) {
        seasonManager.assignTeamsToDivisions(teamManager.getAllSessionTeams(),
                SessionTeamRankingPolicy.byStrengthBudgetAndName(this::calculateTeamOVR),
                teamsPerDivision);
        data.touch();
    }

    private int calculateTeamOVR(String teamId) {
        List<String> playerIds = teamManager.getSquadPlayerIds(teamId);
        if (playerIds == null || playerIds.isEmpty()) {
            return 0;
        }
        int totalOVR = 0;
        int count = 0;
        for (String playerId : playerIds) {
            SessionPlayer p = playerManager.getSessionPlayer(playerId);
            if (p != null) {
                totalOVR += p.calculateOverall();
                count++;
            }
        }
        return count > 0 ? totalOVR / count : 0;
    }
}
