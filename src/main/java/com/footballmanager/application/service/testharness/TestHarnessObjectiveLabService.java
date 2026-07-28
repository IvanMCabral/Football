package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
@Slf4j
class TestHarnessObjectiveLabService {

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

    private boolean isOutfieldPlayer(SessionPlayer player) {
        return player != null
            && player.getSessionPlayerId() != null
            && !player.getSessionPlayerId().isBlank()
            && player.getPosition() != null
            && !"GK".equals(player.getPosition());
    }

    private static int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private int substitutionScore(SessionPlayer player) {
        if (player == null) {
            return 0;
        }
        String normalizedLine = positionPixelAutoLine(player);
        if ("WINGER".equalsIgnoreCase(player.getPosition())
            || "LW".equalsIgnoreCase(player.getPosition())
            || "RW".equalsIgnoreCase(player.getPosition())) {
            return safeInt(player.getAttack()) * 2
                + safeInt(player.getSpeed()) * 2
                + safeInt(player.getTechnique())
                + safeInt(player.getMentality());
        }
        return switch (normalizedLine) {
            case "DEF" -> safeInt(player.getDefense()) * 3
                + safeInt(player.getMentality()) * 2
                + safeInt(player.getSpeed())
                + safeInt(player.getStamina());
            case "MID" -> safeInt(player.getTechnique()) * 2
                + safeInt(player.getMentality()) * 2
                + safeInt(player.getAttack())
                + safeInt(player.getDefense())
                + safeInt(player.getStamina());
            case "WINGER" -> safeInt(player.getAttack()) * 2
                + safeInt(player.getSpeed()) * 2
                + safeInt(player.getTechnique())
                + safeInt(player.getMentality());
            case "ATT" -> safeInt(player.getAttack()) * 3
                + safeInt(player.getTechnique()) * 2
                + safeInt(player.getSpeed())
                + safeInt(player.getMentality());
            default -> safeInt(player.getAttack())
                + safeInt(player.getDefense())
                + safeInt(player.getTechnique())
                + safeInt(player.getSpeed())
                + safeInt(player.getStamina())
                + safeInt(player.getMentality());
        };
    }

    private String positionPixelAutoLine(SessionPlayer player) {
        if (player == null || player.getPosition() == null) {
            return "MID";
        }
        return positionPixelAutoLine(player.getPosition());
    }

    private String positionPixelAutoLine(String rawPosition) {
        if (rawPosition == null || rawPosition.isBlank()) {
            return "MID";
        }
        String position = rawPosition.toUpperCase(Locale.ROOT);
        return switch (position) {
            case "GK" -> "GK";
            case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> "DEF";
            case "ATT", "ST", "CF", "LW", "RW", "WINGER" -> "ATT";
            default -> "MID";
        };
    }

    private String safeName(SessionPlayer player) {
        return player != null && player.getName() != null ? player.getName() : "unknown";
    }

    public Mono<LabMutationResult> prepareObjectiveContrastLab(UUID userId) {
        return mutateObjectiveContrastLab(
            userId,
            "prepare-objective-contrast-lab",
            "Prepared objective contrast lab: one attacking upside swap and one protective swap shaped for DT objective comparison",
            true);
    }

    public Mono<LabMutationResult> restoreObjectiveContrastLab(UUID userId) {
        return mutateObjectiveContrastLab(
            userId,
            "restore-objective-contrast-lab",
            "Restored objective contrast lab players to exact snapshot",
            false);
    }

    private Mono<LabMutationResult> mutateObjectiveContrastLab(
            UUID userId,
            String labKey,
            String message,
            boolean prepare) {

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
                ObjectiveContrastLabPairs pairs = chooseObjectiveContrastLabPairs(career, userTeamId, userSquad)
                    .orElse(null);
                if (pairs == null) {
                    return Mono.error(new IllegalStateException(
                        "Objective contrast lab requires offensive and defensive starter/bench pairs in the user squad"));
                }

                List<SessionPlayer> affected = List.of(
                    pairs.offensiveStarter(),
                    pairs.offensiveBench(),
                    pairs.defensiveStarter(),
                    pairs.defensiveBench());

                if (prepare) {
                    rememberLabSnapshot(userId, "objective-contrast", career, userTeamId, affected);
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, "objective-contrast", career, userTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }
                applyLabStats(pairs.offensiveStarter(), 62, 70, 66, 68, 82, 78);
                applyLabStats(pairs.offensiveBench(), 98, 42, 94, 96, 78, 64);
                applyLabStats(pairs.defensiveStarter(), 72, 58, 78, 78, 82, 68);
                applyLabStats(pairs.defensiveBench(), 38, 98, 58, 74, 88, 96);

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("userTeamId", userTeamId);
                details.put("offensiveStarter", labPlayerDetails(pairs.offensiveStarter()));
                details.put("offensiveBench", labPlayerDetails(pairs.offensiveBench()));
                details.put("defensiveStarter", labPlayerDetails(pairs.defensiveStarter()));
                details.put("defensiveBench", labPlayerDetails(pairs.defensiveBench()));
                details.put("expectedAttackChange", safeName(pairs.offensiveStarter()) + " -> " + safeName(pairs.offensiveBench()));
                details.put("expectedProtectChange", safeName(pairs.defensiveStarter()) + " -> " + safeName(pairs.defensiveBench()));
                details.put("expectedHarnessRead", "Necesito gol should prefer attacking upside; Cuidar resultado should surface protective option if engine signal supports it.");

                log.info("{} userId={} team={} attackPair={}->{} protectPair={}->{}",
                    labKey,
                    userId,
                    userTeamId,
                    pairs.offensiveStarter().getSessionPlayerId(),
                    pairs.offensiveBench().getSessionPlayerId(),
                    pairs.defensiveStarter().getSessionPlayerId(),
                    pairs.defensiveBench().getSessionPlayerId());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }

    private Optional<ObjectiveContrastLabPairs> chooseObjectiveContrastLabPairs(
            CareerSave career,
            String userTeamId,
            List<SessionPlayer> userSquad) {
        if (career == null || userTeamId == null || userSquad == null || userSquad.isEmpty()) {
            return Optional.empty();
        }
        Set<String> startingIds = Set.copyOf(
            career.getTeamStarting11().getOrDefault(userTeamId, List.of()));
        if (startingIds.isEmpty()) {
            return Optional.empty();
        }
        List<SessionPlayer> starters = userSquad.stream()
            .filter(this::isOutfieldPlayer)
            .filter(p -> startingIds.contains(p.getSessionPlayerId()))
            .toList();
        List<SessionPlayer> bench = userSquad.stream()
            .filter(this::isOutfieldPlayer)
            .filter(p -> !startingIds.contains(p.getSessionPlayerId()))
            .filter(p -> !Boolean.TRUE.equals(p.getInjured()) && !Boolean.TRUE.equals(p.getSuspended()))
            .toList();

        SessionPlayer offensiveStarter = starters.stream()
            .filter(p -> "ATT".equals(p.getPosition()) || "WINGER".equals(p.getPosition()))
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> "ATT".equals(p.getPosition()) ? 0 : 1)
                .thenComparingInt((SessionPlayer p) -> -substitutionScore(p))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .findFirst()
            .orElse(null);
        SessionPlayer offensiveBench = bench.stream()
            .filter(p -> offensiveStarter != null && offensiveStarter.getPosition() != null
                && offensiveStarter.getPosition().equals(p.getPosition()))
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> substitutionScore(p))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .findFirst()
            .orElseGet(() -> bench.stream()
                .filter(p -> "ATT".equals(p.getPosition()) || "WINGER".equals(p.getPosition()))
                .sorted(Comparator
                    .comparingInt((SessionPlayer p) -> substitutionScore(p))
                    .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
                .findFirst()
                .orElse(null));

        SessionPlayer defensiveStarter = starters.stream()
            .filter(p -> "DEF".equals(p.getPosition()))
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> p.getDefense())
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .findFirst()
            .orElse(null);
        SessionPlayer defensiveBench = bench.stream()
            .filter(p -> "DEF".equals(p.getPosition()))
            .filter(p -> defensiveStarter == null || !p.getSessionPlayerId().equals(defensiveStarter.getSessionPlayerId()))
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> substitutionScore(p))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .findFirst()
            .orElse(null);

        if (offensiveStarter == null || offensiveBench == null || defensiveStarter == null || defensiveBench == null) {
            return Optional.empty();
        }
        if (Set.of(
            offensiveStarter.getSessionPlayerId(),
            offensiveBench.getSessionPlayerId(),
            defensiveStarter.getSessionPlayerId(),
            defensiveBench.getSessionPlayerId()).size() < 4) {
            return Optional.empty();
        }
        return Optional.of(new ObjectiveContrastLabPairs(
            offensiveStarter,
            offensiveBench,
            defensiveStarter,
            defensiveBench));
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
            return new LabSnapshot(teamId, playerStats);
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
        for (Map.Entry<String, PlayerStatSnapshot> entry : snapshot.players().entrySet()) {
            SessionPlayer player = byId.get(entry.getKey());
            if (player == null) {
                continue;
            }
            entry.getValue().applyTo(player);
            restoredPlayers.add(labPlayerDetails(player));
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("userTeamId", teamId);
        details.put("restoredPlayers", restoredPlayers);
        details.put("restoredSnapshot", labName);
        return Optional.of(new LabMutationResult("restore-" + labName, message, details));
    }

    private Mono<LabMutationResult> persistLabMutation(CareerSave career, LabMutationResult result) {
        return careerRepository.save(career)
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


    private record ObjectiveContrastLabPairs(
        SessionPlayer offensiveStarter,
        SessionPlayer offensiveBench,
        SessionPlayer defensiveStarter,
        SessionPlayer defensiveBench
    ) {}

    private record LabSnapshot(
        String teamId,
        Map<String, PlayerStatSnapshot> players
    ) {}

    private record PlayerStatSnapshot(
        int attack,
        int defense,
        int technique,
        int speed,
        int stamina,
        int mentality
    ) {
        private static PlayerStatSnapshot from(SessionPlayer player) {
            return new PlayerStatSnapshot(
                safeInt(player.getAttack()),
                safeInt(player.getDefense()),
                safeInt(player.getTechnique()),
                safeInt(player.getSpeed()),
                safeInt(player.getStamina()),
                safeInt(player.getMentality())
            );
        }

        private void applyTo(SessionPlayer player) {
            player.setAttack(attack);
            player.setDefense(defense);
            player.setTechnique(technique);
            player.setSpeed(speed);
            player.setStamina(stamina);
            player.setMentality(mentality);
        }
    }
}
