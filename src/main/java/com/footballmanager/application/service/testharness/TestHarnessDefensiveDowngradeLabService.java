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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
@Slf4j
class TestHarnessDefensiveDowngradeLabService {

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

    public Mono<LabMutationResult> prepareDefensiveDowngradeLab(UUID userId) {
        return mutateDefensiveDowngradeLab(
            userId,
            "prepare-defensive-downgrade-lab",
            88, 99, 88, 88, 99, 99,
            45, 35, 45, 45, 55, 35,
            "Prepared defensive downgrade lab: Carvajal strong, Fran Garcia weak");
    }

    public Mono<LabMutationResult> restoreDefensiveDowngradeLab(UUID userId) {
        return mutateDefensiveDowngradeLab(
            userId,
            "restore-defensive-downgrade-lab",
            85, 85, 85, 85, 85, 85,
            78, 78, 78, 78, 78, 78,
            "Restored defensive downgrade lab players to smoke defaults");
    }

    private Mono<LabMutationResult> mutateDefensiveDowngradeLab(
            UUID userId,
            String labKey,
            int carvajalAttack,
            int carvajalDefense,
            int carvajalTechnique,
            int carvajalSpeed,
            int carvajalStamina,
            int carvajalMentality,
            int franAttack,
            int franDefense,
            int franTechnique,
            int franSpeed,
            int franStamina,
            int franMentality,
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
                SessionPlayer carvajal = findSquadPlayerByName(userSquad, "Dani Carvajal");
                SessionPlayer fran = findSquadPlayerByName(userSquad, "Fran Garcia");
                if (carvajal == null || fran == null) {
                    return Mono.error(new IllegalStateException(
                        "Defensive downgrade lab requires Dani Carvajal and Fran Garcia in the user squad"));
                }

                if (labKey.startsWith("prepare-")) {
                    rememberLabSnapshot(userId, "defensive-downgrade", career, userTeamId,
                        List.of(carvajal, fran));
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, "defensive-downgrade", career, userTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }

                applyLabStats(carvajal, carvajalAttack, carvajalDefense, carvajalTechnique,
                    carvajalSpeed, carvajalStamina, carvajalMentality);
                applyLabStats(fran, franAttack, franDefense, franTechnique,
                    franSpeed, franStamina, franMentality);

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("userTeamId", userTeamId);
                details.put("starterCandidate", labPlayerDetails(carvajal));
                details.put("benchCandidate", labPlayerDetails(fran));
                details.put("expectedScenario", "m60-defensive-downgrade-sub");
                details.put("expectedChange", "Dani Carvajal (DEF) -> Fran Garcia (DEF)");

                log.info("{} userId={} team={} carvajal={} fran={}",
                    labKey, userId, userTeamId, carvajal.getSessionPlayerId(), fran.getSessionPlayerId());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
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
