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
class TestHarnessOffensiveLabService {

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

    public Mono<LabMutationResult> prepareOffensiveUpgradeLab(UUID userId) {
        return mutateOffensiveUpgradeLab(
            userId,
            "prepare-offensive-upgrade-lab",
            65, 65, 65, 65, 88, 65,
            99, 80, 99, 99, 99, 99,
            "Prepared offensive upgrade lab: current offensive starter down, matching bench attacker up");
    }

    public Mono<LabMutationResult> restoreOffensiveUpgradeLab(UUID userId) {
        return mutateOffensiveUpgradeLab(
            userId,
            "restore-offensive-upgrade-lab",
            88, 88, 88, 88, 88, 88,
            78, 78, 78, 78, 78, 78,
            "Restored offensive upgrade lab players");
    }

    private Mono<LabMutationResult> mutateOffensiveUpgradeLab(
            UUID userId,
            String labKey,
            int mbappeAttack,
            int mbappeDefense,
            int mbappeTechnique,
            int mbappeSpeed,
            int mbappeStamina,
            int mbappeMentality,
            int endrickAttack,
            int endrickDefense,
            int endrickTechnique,
            int endrickSpeed,
            int endrickStamina,
            int endrickMentality,
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
                LabPair offensivePair = chooseOffensiveLabPair(career, userTeamId, userSquad)
                    .orElse(null);
                if (offensivePair == null) {
                    return Mono.error(new IllegalStateException(
                        "Offensive upgrade lab requires one offensive starter and one offensive bench player in the user squad"));
                }
                SessionPlayer starter = offensivePair.starter();
                SessionPlayer bench = offensivePair.bench();

                if (labKey.startsWith("prepare-")) {
                    rememberLabSnapshot(userId, "offensive-upgrade", career, userTeamId,
                        List.of(starter, bench));
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, "offensive-upgrade", career, userTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }

                applyLabStats(starter, mbappeAttack, mbappeDefense, mbappeTechnique, mbappeSpeed, mbappeStamina, mbappeMentality);
                applyLabStats(bench, endrickAttack, endrickDefense, endrickTechnique, endrickSpeed, endrickStamina, endrickMentality);

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("userTeamId", userTeamId);
                details.put("starterCandidate", labPlayerDetails(starter));
                details.put("benchCandidate", labPlayerDetails(bench));
                details.put("expectedScenario", "m60-offensive-upgrade-sub");
                details.put("expectedChange", safeName(starter) + " (" + starter.getPosition()
                    + ") -> " + safeName(bench) + " (" + bench.getPosition() + ")");

                log.info("{} userId={} team={} starter={} bench={}",
                    labKey, userId, userTeamId, starter.getSessionPlayerId(), bench.getSessionPlayerId());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }

    private Optional<LabPair> chooseOffensiveLabPair(
            CareerSave career,
            String userTeamId,
            List<SessionPlayer> userSquad) {
        if (career == null || userTeamId == null || userSquad == null || userSquad.isEmpty()) {
            return Optional.empty();
        }

        SessionPlayer namedStarter = findSquadPlayerByName(userSquad, "Kylian Mbappe");
        SessionPlayer namedBench = findSquadPlayerByName(userSquad, "Endrick");
        if (namedStarter != null && namedBench != null) {
            return Optional.of(new LabPair(namedStarter, namedBench));
        }

        Set<String> startingIds = Set.copyOf(
            career.getTeamStarting11().getOrDefault(userTeamId, List.of()));
        if (startingIds.isEmpty()) {
            return Optional.empty();
        }

        List<SessionPlayer> offensiveStarters = userSquad.stream()
            .filter(this::isOutfieldPlayer)
            .filter(p -> startingIds.contains(p.getSessionPlayerId()))
            .filter(p -> "ATT".equals(p.getPosition()) || "WINGER".equals(p.getPosition()))
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> "ATT".equals(p.getPosition()) ? 0 : 1)
                .thenComparingInt((SessionPlayer p) -> -substitutionScore(p))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .toList();

        for (SessionPlayer starter : offensiveStarters) {
            Optional<SessionPlayer> bench = userSquad.stream()
                .filter(this::isOutfieldPlayer)
                .filter(p -> !startingIds.contains(p.getSessionPlayerId()))
                .filter(p -> starter.getPosition() != null && starter.getPosition().equals(p.getPosition()))
                .sorted(Comparator
                    .comparingInt((SessionPlayer p) -> substitutionScore(p))
                    .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
                .findFirst();
            if (bench.isPresent()) {
                return Optional.of(new LabPair(starter, bench.get()));
            }
        }

        return Optional.empty();
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


    private record LabPair(
        SessionPlayer starter,
        SessionPlayer bench
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
