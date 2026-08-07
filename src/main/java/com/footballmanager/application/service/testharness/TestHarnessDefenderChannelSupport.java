package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.testharness.LabMutationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
class TestHarnessDefenderChannelSupport {

    private final CareerRepository careerRepository;
    private final CareerSessionService careerSessionService;
    private final ConcurrentMap<String, LabSnapshot> labSnapshots = new ConcurrentHashMap<>();

    Mono<CareerSave> loadCareer(UUID userId) {
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> optionalCareer
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new IllegalStateException(
                    "Career not found for userId=" + userId))));
    }

    static int safeInt(Integer value) { return value != null ? value : 0; }

    int substitutionScore(SessionPlayer player) {
        if (player == null) return 0;
        return safeInt(player.getAttack()) + safeInt(player.getDefense()) + safeInt(player.getTechnique())
            + safeInt(player.getSpeed()) + safeInt(player.getStamina()) + safeInt(player.getMentality());
    }

    List<SessionPlayer> findStartingDefendersByChannel(
            List<SessionPlayer> squad,
            Map<String, LineupSlot> slots,
            DefenderChannel channel,
            int limit) {
        if (limit <= 0) {
            return List.of();
        }
        if (squad == null || squad.isEmpty()) {
            return List.of();
        }
        if (slots == null || slots.isEmpty()) {
            return squadDefendersByChannelOrder(squad, channel, limit);
        }

        Map<String, SessionPlayer> byId = squad.stream()
            .filter(p -> p != null && p.getSessionPlayerId() != null)
            .collect(Collectors.toMap(SessionPlayer::getSessionPlayerId, p -> p, (a, b) -> a));

        List<LineupSlot> allStartingSlots = slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .filter(slot -> byId.get(slot.playerId()) != null)
            .toList();
        if (allStartingSlots.size() < 2 && channel != DefenderChannel.CENTER) {
            return squadDefendersByChannelOrder(squad, channel, limit);
        }

        List<SessionPlayer> channelPlayers = slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .filter(slot -> {
                Double x = slotXPercent(slot);
                return x != null && channel.matches(x);
            })
            .map(slot -> byId.get(slot.playerId()))
            .filter(p -> p != null && "DEF".equals(p.getPosition()))
            .distinct()
            .limit(limit)
            .toList();
        if (!channelPlayers.isEmpty()) {
            return channelPlayers;
        }

        List<SessionPlayer> tacticalChannelPlayers = slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .filter(slot -> {
                Double x = slotXPercent(slot);
                return x != null && channel.matches(x);
            })
            .map(slot -> byId.get(slot.playerId()))
            .filter(p -> p != null)
            .distinct()
            .limit(limit)
            .toList();
        if (!tacticalChannelPlayers.isEmpty()) {
            return tacticalChannelPlayers;
        }

        List<LineupSlot> defenderSlots = slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .filter(slot -> {
                SessionPlayer player = byId.get(slot.playerId());
                return player != null && "DEF".equals(player.getPosition());
            })
            .toList();

        List<LineupSlot> nearestSlots = !defenderSlots.isEmpty()
            && (channel == DefenderChannel.CENTER || defenderSlots.size() >= 2)
            ? defenderSlots
            : allStartingSlots;
        if (nearestSlots.isEmpty()) {
            return squadDefendersByChannelOrder(squad, channel, limit);
        }

        return nearestSlots.stream()
            .sorted((a, b) -> {
                double ax = Optional.ofNullable(slotXPercent(a)).orElse(50.0);
                double bx = Optional.ofNullable(slotXPercent(b)).orElse(50.0);
                return switch (channel) {
                    case LEFT -> Double.compare(ax, bx);
                    case RIGHT -> Double.compare(bx, ax);
                    case CENTER -> Double.compare(Math.abs(ax - 50.0), Math.abs(bx - 50.0));
                };
            })
            .map(slot -> byId.get(slot.playerId()))
            .distinct()
            .limit(limit)
            .toList();
    }

    ChannelSlot slotForChannel(DefenderChannel channel, int index) {
        return switch (channel) {
            case LEFT -> new ChannelSlot("S22-1", 18.0);
            case RIGHT -> new ChannelSlot("S24-3", 82.0);
            case CENTER -> index % 2 == 0
                ? new ChannelSlot("S23-1", 42.0)
                : new ChannelSlot("S23-3", 58.0);
        };
    }

    List<SessionPlayer> firstSquadDefenders(List<SessionPlayer> squad, int limit) {
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

    List<SessionPlayer> squadDefendersByChannelOrder(
            List<SessionPlayer> squad,
            DefenderChannel channel,
            int limit) {
        if (squad == null || squad.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<SessionPlayer> defenders = squad.stream()
            .filter(p -> p != null
                && p.getSessionPlayerId() != null
                && "DEF".equals(p.getPosition()))
            .toList();
        List<SessionPlayer> sideCandidates = defenders.size() >= Math.max(2, limit)
            ? defenders
            : squad.stream()
                .filter(p -> p != null && p.getSessionPlayerId() != null)
                .filter(p -> !"GK".equals(p.getPosition()))
                .toList();
        if (channel == DefenderChannel.RIGHT) {
            return IntStream.range(0, sideCandidates.size())
                .mapToObj(i -> sideCandidates.get(sideCandidates.size() - 1 - i))
                .limit(limit)
                .toList();
        }
        return sideCandidates.stream()
            .limit(limit)
            .toList();
    }

    Double slotXPercent(LineupSlot slot) {
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

    int[] parseSlotSubdivision(LineupSlot slot) {
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

    SessionPlayer findSquadPlayerByName(List<SessionPlayer> squad, String name) {
        if (squad == null || name == null) {
            return null;
        }
        return squad.stream()
            .filter(p -> p != null && name.equalsIgnoreCase(p.getName()))
            .findFirst()
            .orElse(null);
    }

    void applyLabStats(SessionPlayer player,
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

    void rememberLabSnapshot(UUID userId,
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

    Optional<LabMutationResult> restoreLabSnapshot(UUID userId,
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

    Mono<LabMutationResult> persistLabMutation(CareerSave career, LabMutationResult result) {
        return careerSessionService.saveCareer(career).then()
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())))
            .thenReturn(result);
    }

    String labSnapshotKey(UUID userId, String labName) {
        return userId + ":" + labName;
    }

    Map<String, Object> labPlayerDetails(SessionPlayer player) {
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

    record LabSnapshot(
        String teamId,
        Map<String, PlayerStatSnapshot> playerStats,
        Map<String, LineupSlot> slots
    ) {}

    record PlayerStatSnapshot(
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

    enum DefenderChannel {
        LEFT {
                    boolean matches(double x) {
                return x < 35.0;
            }
        },
        CENTER {
                    boolean matches(double x) {
                return x >= 35.0 && x <= 65.0;
            }
        },
        RIGHT {
                    boolean matches(double x) {
                return x > 65.0;
            }
        };

        abstract boolean matches(double x);
    }

    record ChannelSlot(String subdivisionId, double x) {}
}
