package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.testharness.LabMutationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
@Slf4j
class TestHarnessWideDefenderLabService {

    private final CareerRepository careerRepository;
    private final CareerSessionService careerSessionService;
    private final ConcurrentMap<String, LabSnapshot> labSnapshots = new ConcurrentHashMap<>();

    private Mono<CareerSave> loadCareer(UUID userId) {
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> optionalCareer
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new IllegalStateException(
                    "Career not found for userId=" + userId))));
    }

    private int safeInt(Integer value) { return value != null ? value : 0; }

    private int substitutionScore(SessionPlayer player) {
        if (player == null) return 0;
        return safeInt(player.getAttack()) + safeInt(player.getDefense()) + safeInt(player.getTechnique())
            + safeInt(player.getSpeed()) + safeInt(player.getStamina()) + safeInt(player.getMentality());
    }

    public Mono<LabMutationResult> prepareWeakWideDefendersLab(UUID userId) {
        return mutateWeakWideDefendersLab(
            userId,
            "prepare-weak-wide-defenders-lab",
            45, 25, 45, 45, 55, 25,
            "Prepared weak wide defenders lab: current wide DEF starters made vulnerable");
    }

    public Mono<LabMutationResult> restoreWeakWideDefendersLab(UUID userId) {
        return mutateWeakWideDefendersLab(
            userId,
            "restore-weak-wide-defenders-lab",
            76, 76, 76, 76, 76, 76,
            "Restored weak wide defenders lab players to smoke defaults");
    }

    public Mono<LabMutationResult> prepareOpponentWeakWideDefendersLab(UUID userId, String matchId) {
        return mutateOpponentWeakWideDefendersLab(
            userId,
            matchId,
            "prepare-opponent-weak-wide-defenders-lab",
            45, 25, 45, 45, 55, 25,
            "Prepared opponent weak wide defenders lab: selected rival wide DEF starters made vulnerable");
    }

    public Mono<LabMutationResult> restoreOpponentWeakWideDefendersLab(UUID userId, String matchId) {
        return mutateOpponentWeakWideDefendersLab(
            userId,
            matchId,
            "restore-opponent-weak-wide-defenders-lab",
            76, 76, 76, 76, 76, 76,
            "Restored opponent weak wide defenders lab players");
    }


    private Mono<LabMutationResult> mutateWeakWideDefendersLab(
            UUID userId,
            String labKey,
            int attack,
            int defense,
            int technique,
            int speed,
            int stamina,
            int mentality,
            String message) {

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                String userTeamId = career.getUserSessionTeamId();
                List<SessionPlayer> userSquad = career.getTeamSquad(userTeamId);
                Map<String, LineupSlot> slots = career.getTeamStarting11SubdivisionSlots()
                    .getOrDefault(userTeamId, Map.of());

                List<SessionPlayer> wideDefenders = findWideStartingDefenders(userSquad, slots);
                if (wideDefenders.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Weak wide defenders lab requires at least one starting DEF in a wide slot"));
                }

                if (labKey.startsWith("prepare-")) {
                    rememberLabSnapshot(userId, "weak-wide-defenders", career, userTeamId, wideDefenders);
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, "weak-wide-defenders", career, userTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }

                wideDefenders.forEach(player ->
                    applyLabStats(player, attack, defense, technique, speed, stamina, mentality));
                if (labKey.startsWith("prepare-")) {
                    Map<String, LineupSlot> labSlots = new LinkedHashMap<>(slots);
                    for (int i = 0; i < wideDefenders.size(); i++) {
                        SessionPlayer player = wideDefenders.get(i);
                        double x = i % 2 == 0 ? 18.0 : 82.0;
                        String subdivision = i % 2 == 0 ? "S22-1" : "S24-3";
                        labSlots.put(player.getSessionPlayerId(), new LineupSlot(
                            player.getSessionPlayerId(),
                            subdivision,
                            x,
                            78.0));
                    }
                    career.replaceTeamStarting11SubdivisionRaw(userTeamId, labSlots);
                } else {
                    Map<String, LineupSlot> restoredSlots = new LinkedHashMap<>(slots);
                    wideDefenders.forEach(player ->
                        restoredSlots.remove(player.getSessionPlayerId()));
                    career.replaceTeamStarting11SubdivisionRaw(userTeamId, restoredSlots);
                }

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("userTeamId", userTeamId);
                details.put("affectedPlayers", wideDefenders.stream()
                    .map(this::labPlayerDetails)
                    .toList());
                details.put("slotMutation", labKey.startsWith("prepare-")
                    ? "affected DEF forced to x18/x82 y78 for channel exposure"
                    : "affected DEF lab slots removed; other slots preserved");
                details.put("expectedScenarios", List.of("m45-opponent-wide", "m45-opponent-central"));
                details.put("expectedSignal", "m45-opponent-wide should increase opponent wide shots/xG against vulnerable fullbacks");

                log.info("{} userId={} team={} affected={}",
                    labKey, userId, userTeamId,
                    wideDefenders.stream().map(SessionPlayer::getSessionPlayerId).toList());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }

    private Mono<LabMutationResult> mutateOpponentWeakWideDefendersLab(
            UUID userId,
            String matchId,
            String labKey,
            int attack,
            int defense,
            int technique,
            int speed,
            int stamina,
            int mentality,
            String message) {

        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                MatchFixture fixture = career.getTournamentState().getFixtures().stream()
                    .filter(f -> f.getMatchId().equals(matchId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Match not found in current tournament: " + matchId));
                String userTeamId = career.getUserSessionTeamId();
                boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
                boolean userIsAway = fixture.getAwayTeamId().equals(userTeamId);
                if (!userIsHome && !userIsAway) {
                    return Mono.error(new IllegalArgumentException(
                        "Opponent weak wide defenders lab requires a match involving the user team: " + userTeamId));
                }
                String opponentTeamId = userIsHome ? fixture.getAwayTeamId() : fixture.getHomeTeamId();
                List<SessionPlayer> opponentSquad = career.getTeamSquad(opponentTeamId);
                Map<String, LineupSlot> slots = career.getTeamStarting11SubdivisionSlots()
                    .getOrDefault(opponentTeamId, Map.of());

                List<SessionPlayer> wideDefenders = findWideStartingDefenders(opponentSquad, slots);
                if (wideDefenders.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Opponent weak wide defenders lab requires at least one opponent starting DEF in a wide slot"));
                }

                String snapshotKey = "opponent-weak-wide-defenders:" + matchId;
                if (labKey.startsWith("prepare-")) {
                    rememberLabSnapshot(userId, snapshotKey, career, opponentTeamId, wideDefenders);
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, snapshotKey, career, opponentTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }

                wideDefenders.forEach(player ->
                    applyLabStats(player, attack, defense, technique, speed, stamina, mentality));
                if (labKey.startsWith("prepare-")) {
                    Map<String, LineupSlot> labSlots = new LinkedHashMap<>(slots);
                    for (int i = 0; i < wideDefenders.size(); i++) {
                        SessionPlayer player = wideDefenders.get(i);
                        double x = i % 2 == 0 ? 18.0 : 82.0;
                        String subdivision = i % 2 == 0 ? "S22-1" : "S24-3";
                        labSlots.put(player.getSessionPlayerId(), new LineupSlot(
                            player.getSessionPlayerId(),
                            subdivision,
                            x,
                            78.0));
                    }
                    career.replaceTeamStarting11SubdivisionRaw(opponentTeamId, labSlots);
                } else {
                    Map<String, LineupSlot> restoredSlots = new LinkedHashMap<>(slots);
                    wideDefenders.forEach(player ->
                        restoredSlots.remove(player.getSessionPlayerId()));
                    career.replaceTeamStarting11SubdivisionRaw(opponentTeamId, restoredSlots);
                }

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("matchId", matchId);
                details.put("userTeamId", userTeamId);
                details.put("opponentTeamId", opponentTeamId);
                details.put("affectedPlayers", wideDefenders.stream()
                    .map(this::labPlayerDetails)
                    .toList());
                details.put("slotMutation", labKey.startsWith("prepare-")
                    ? "opponent wide DEF forced to x18/x82 y78 for offensive wide-exploitation smoke"
                    : "opponent wide DEF lab slots removed; other slots preserved");
                details.put("expectedFocus", List.of("WIDE_PLAY", "CENTRAL_PLAY"));
                details.put("expectedSignal", "WIDE_PLAY should gain relative xG/xG-diff versus CENTRAL_PLAY when opponent wide defenders are vulnerable");

                log.info("{} userId={} match={} opponentTeam={} affected={}",
                    labKey, userId, matchId, opponentTeamId,
                    wideDefenders.stream().map(SessionPlayer::getSessionPlayerId).toList());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }


    private List<SessionPlayer> findWideStartingDefenders(
            List<SessionPlayer> squad,
            Map<String, LineupSlot> slots) {
        if (squad == null || squad.isEmpty() || slots == null || slots.isEmpty()) {
            return firstSquadDefenders(squad, 2);
        }
        Map<String, SessionPlayer> byId = squad.stream()
            .filter(p -> p != null && p.getSessionPlayerId() != null)
            .collect(Collectors.toMap(SessionPlayer::getSessionPlayerId, p -> p, (a, b) -> a));

        List<SessionPlayer> wide = slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .filter(slot -> {
                Double x = slotXPercent(slot);
                return x != null && (x < 35.0 || x > 65.0);
            })
            .map(slot -> byId.get(slot.playerId()))
            .filter(p -> p != null && "DEF".equals(p.getPosition()))
            .distinct()
            .toList();
        if (!wide.isEmpty()) {
            return wide;
        }
        return slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .map(slot -> byId.get(slot.playerId()))
            .filter(p -> p != null && "DEF".equals(p.getPosition()))
            .distinct()
            .limit(2)
            .toList();
    }


    private List<SessionPlayer> firstSquadDefenders(List<SessionPlayer> squad, int limit) {
        if (squad == null || squad.isEmpty()) {
            return List.of();
        }
        return squad.stream()
            .filter(p -> p != null
                && p.getSessionPlayerId() != null
                && "DEF".equals(p.getPosition()))
            .limit(limit)
            .toList();
    }

    private Double slotXPercent(LineupSlot slot) {
        if (slot == null) return null;
        if (slot.customXPercent() != null && Double.isFinite(slot.customXPercent())) {
            return slot.customXPercent();
        }
        int[] parsed = parseSlotSubdivision(slot);
        if (parsed == null) return null;
        int sector = parsed[0];
        int subIndex = parsed[1];
        int sectorCol = (sector - 1) % 3;
        double left = (sectorCol * 3 + (subIndex - 1)) * 11.11;
        return Math.max(0.0, Math.min(100.0, left + 11.11 / 2.0));
    }

    private int[] parseSlotSubdivision(LineupSlot slot) {
        if (slot == null || slot.subdivisionId() == null || !slot.subdivisionId().startsWith("S")) {
            return null;
        }
        String id = slot.subdivisionId();
        int dash = id.indexOf('-');
        if (dash < 0 || dash >= id.length() - 1) return null;
        try {
            int sector = Integer.parseInt(id.substring(1, dash));
            int subIndex = Integer.parseInt(id.substring(dash + 1));
            if (sector < 1 || sector > 27 || subIndex < 1 || subIndex > 3) return null;
            return new int[] { sector, subIndex };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private SessionPlayer findSquadPlayerByName(List<SessionPlayer> squad, String name) {
        if (squad == null || name == null) {
            return null;
        }
        return squad.stream()
            .filter(p -> p != null && name.equalsIgnoreCase(p.getName()))
            .findFirst()
            .orElse(null);
    }

    private void applyLabStats(SessionPlayer player,
                               int attack,
                               int defense,
                               int technique,
                               int speed,
                               int stamina,
                               int mentality) {
        player.setAttack(attack);
        player.setDefense(defense);
        player.setTechnique(technique);
        player.setSpeed(speed);
        player.setStamina(stamina);
        player.setMentality(mentality);
    }

    private void rememberLabSnapshot(UUID userId,
                                     String labName,
                                     CareerSave career,
                                     String teamId,
                                     List<SessionPlayer> affectedPlayers) {
        String key = labSnapshotKey(userId, labName);
        labSnapshots.computeIfAbsent(key, ignored -> {
            Map<String, PlayerStatSnapshot> playerStats = new LinkedHashMap<>();
            if (affectedPlayers != null) {
                for (SessionPlayer player : affectedPlayers) {
                    if (player != null && player.getSessionPlayerId() != null) {
                        playerStats.put(player.getSessionPlayerId(), PlayerStatSnapshot.from(player));
                    }
                }
            }
            Map<String, LineupSlot> slots = new LinkedHashMap<>(
                career.getTeamStarting11SubdivisionSlots().getOrDefault(teamId, Map.of()));
            return new LabSnapshot(teamId, playerStats, slots);
        });
    }

    private Optional<LabMutationResult> restoreLabSnapshot(UUID userId,
                                                           String labName,
                                                           CareerSave career,
                                                           String fallbackTeamId,
                                                           String message) {
        LabSnapshot snapshot = labSnapshots.remove(labSnapshotKey(userId, labName));
        if (snapshot == null) {
            return Optional.empty();
        }

        String teamId = snapshot.teamId() != null ? snapshot.teamId() : fallbackTeamId;
        List<SessionPlayer> squad = career.getTeamSquad(teamId);
        Map<String, SessionPlayer> byId = squad.stream()
            .filter(p -> p != null && p.getSessionPlayerId() != null)
            .collect(Collectors.toMap(SessionPlayer::getSessionPlayerId, p -> p, (a, b) -> a));

        List<Map<String, Object>> restoredPlayers = new ArrayList<>();
        for (Map.Entry<String, PlayerStatSnapshot> entry : snapshot.playerStats().entrySet()) {
            SessionPlayer player = byId.get(entry.getKey());
            if (player == null) {
                continue;
            }
            entry.getValue().applyTo(player);
            restoredPlayers.add(labPlayerDetails(player));
        }
        career.replaceTeamStarting11SubdivisionRaw(teamId, snapshot.slots());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("userTeamId", teamId);
        details.put("restoredFromSnapshot", true);
        details.put("restoredPlayers", restoredPlayers);
        details.put("restoredSlotCount", snapshot.slots().size());
        details.put("message", "Exact in-memory lab snapshot restored");

        return Optional.of(new LabMutationResult(
            "restore-" + labName + "-lab",
            message + " (exact snapshot)",
            details));
    }

    private Mono<LabMutationResult> persistLabMutation(CareerSave career, LabMutationResult result) {
        return careerSessionService.saveCareer(career).then()
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())))
            .thenReturn(result);
    }

    private String labSnapshotKey(UUID userId, String labName) {
        return userId + ":" + labName;
    }

    private Map<String, Object> labPlayerDetails(SessionPlayer player) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("playerId", player.getSessionPlayerId());
        details.put("name", player.getName());
        details.put("position", player.getPosition());
        details.put("attack", player.getAttack());
        details.put("defense", player.getDefense());
        details.put("technique", player.getTechnique());
        details.put("speed", player.getSpeed());
        details.put("stamina", player.getStamina());
        details.put("mentality", player.getMentality());
        details.put("score", substitutionScore(player));
        return details;
    }

    private record LabSnapshot(
        String teamId,
        Map<String, PlayerStatSnapshot> playerStats,
        Map<String, LineupSlot> slots
    ) {}

    private record PlayerStatSnapshot(
        Integer attack,
        Integer defense,
        Integer technique,
        Integer speed,
        Integer stamina,
        Integer mentality
    ) {
        static PlayerStatSnapshot from(SessionPlayer player) {
            return new PlayerStatSnapshot(
                player.getAttack(),
                player.getDefense(),
                player.getTechnique(),
                player.getSpeed(),
                player.getStamina(),
                player.getMentality());
        }

        void applyTo(SessionPlayer player) {
            player.setAttack(attack);
            player.setDefense(defense);
            player.setTechnique(technique);
            player.setSpeed(speed);
            player.setStamina(stamina);
            player.setMentality(mentality);
        }
    }
}
