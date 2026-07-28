package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationPositionDTO;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public final class V24MatchContextFactory {

    private final FormationService formationService = new FormationService();

    public V24MatchContext build(
            CareerSave career,
            MatchFixture fixture,
            SessionTeam homeTeam,
            SessionTeam awayTeam,
            long seed) {
        // test-harness), fall back to the styles persisted on the SessionTeam.
        // This lets the test-harness set-style endpoint (POST /test-harness/career/set-style)
        // actually drive the engine's style-aware chanceProbability, possessionBase,
        // and styleToModifier paths.
        //
        // We have to read the styles BEFORE delegating to buildWithStyles, so
        // validateInputs (which checks for null homeTeam/awayTeam) would NPE on
        // us if homeTeam/awayTeam were null. Manually check those here.
        if (career == null) throw new IllegalArgumentException("career must be non-null");
        if (fixture == null) throw new IllegalArgumentException("fixture must be non-null");
        if (homeTeam == null) throw new IllegalArgumentException("homeTeam must be non-null");
        if (awayTeam == null) throw new IllegalArgumentException("awayTeam must be non-null");
        return buildWithStyles(
            career, fixture, homeTeam, awayTeam,
            homeTeam.getStyle(),
            awayTeam.getStyle(),
            seed);
    }

    public V24MatchContext buildWithStyles(
            CareerSave career,
            MatchFixture fixture,
            SessionTeam homeTeam,
            SessionTeam awayTeam,
            TeamStyle homeStyle,
            TeamStyle awayStyle,
            long seed) {

        validateInputs(career, fixture, homeTeam, awayTeam);

        String matchId = fixture.getMatchId();
        String homeTeamId = resolveTeamId(fixture.getHomeTeamId(), homeTeam);
        String awayTeamId = resolveTeamId(fixture.getAwayTeamId(), awayTeam);

        // LineupCommandUseCaseImpl.autoSelectLineup / manualSelectLineupWithSlots, sprint 1.6).
        // Fall back to SessionTeam.getFormation() for backward compat with saves from sprint 1.5
        // or earlier that pre-date the teamStarting11Formation map.
        Map<String, String> persistedFormations = career.getTeamStarting11Formation();
        String homeFormation = (persistedFormations != null && persistedFormations.containsKey(homeTeamId))
                ? persistedFormations.get(homeTeamId)
                : homeTeam.getFormation();
        String awayFormation = (persistedFormations != null && persistedFormations.containsKey(awayTeamId))
                ? persistedFormations.get(awayTeamId)
                : awayTeam.getFormation();

        List<SessionPlayer> homeStarters = resolveStartingXI(career, homeTeamId, homeFormation, "home");
        List<SessionPlayer> awayStarters = resolveStartingXI(career, awayTeamId, awayFormation, "away");

        validateStarterCount(homeStarters, "home");
        validateStarterCount(awayStarters, "away");
        validateNoDuplicateStarters(homeStarters, "home");
        validateNoDuplicateStarters(awayStarters, "away");

        List<SessionPlayer> homeBench = deriveBench(career, homeTeamId, homeStarters);
        List<SessionPlayer> awayBench = deriveBench(career, awayTeamId, awayStarters);

        Map<String, LineupSlotDTO> homeSlotsByPlayerId =
                resolveSlotsByPlayerId(career, homeTeamId, homeFormation, homeStarters);
        Map<String, LineupSlotDTO> awaySlotsByPlayerId =
                resolveSlotsByPlayerId(career, awayTeamId, awayFormation, awayStarters);

        return new V24MatchContext(
                matchId,
                homeTeamId,
                awayTeamId,
                homeTeam,
                awayTeam,
                homeStarters,
                awayStarters,
                homeBench,
                awayBench,
                homeFormation,
                awayFormation,
                homeStyle,
                awayStyle,
                List.of(),
                homeSlotsByPlayerId,
                awaySlotsByPlayerId);
    }

    public boolean canBuild(
            CareerSave career,
            MatchFixture fixture,
            SessionTeam homeTeam,
            SessionTeam awayTeam) {
        try {
            build(career, fixture, homeTeam, awayTeam, 0L);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private void validateInputs(
            CareerSave career,
            MatchFixture fixture,
            SessionTeam homeTeam,
            SessionTeam awayTeam) {
        if (career == null) throw new IllegalArgumentException("career must not be null");
        if (fixture == null) throw new IllegalArgumentException("fixture must not be null");
        if (homeTeam == null) throw new IllegalArgumentException("homeTeam must not be null");
        if (awayTeam == null) throw new IllegalArgumentException("awayTeam must not be null");
    }

    private void validateStarterCount(List<SessionPlayer> starters, String teamLabel) {
        int min = com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS;
        if (starters.size() < min || starters.size() > 11) {
            throw new IllegalArgumentException(
                    teamLabel + "StartingPlayers must contain between " + min
                    + " and 11 players, got " + starters.size());
        }
    }

    private void validateNoDuplicateStarters(List<SessionPlayer> starters, String teamLabel) {
        Set<String> seen = new HashSet<>();
        for (SessionPlayer p : starters) {
            String id = p.getSessionPlayerId();
            if (!seen.add(id)) {
                throw new IllegalArgumentException(
                        teamLabel + "StartingPlayers contains duplicate playerId: " + id);
            }
        }
    }

    private String resolveTeamId(String fixtureTeamId, SessionTeam team) {
        return team.getSessionTeamId();
    }

    private List<SessionPlayer> resolveStartingXI(CareerSave career, String teamId, String formation, String teamLabel) {
        // Try CareerSave.teamStarting11 first (LineupController writes here)
        List<SessionPlayer> resolved = resolveFromStarting11OrNull(career, teamId, teamLabel);
        if (resolved != null) return resolved;

        resolved = deriveStartingXIfromSquad(career, teamId, formation, teamLabel);
        int min = com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS;
        if (resolved.size() >= min) return resolved;

        throw new IllegalArgumentException(
                teamLabel + " starting XI must contain at least " + min
                + " players, got " + resolved.size()
                + " for teamId: " + teamId);
    }

    private List<SessionPlayer> resolveFromStarting11OrNull(
            CareerSave career, String teamId, String teamLabel) {
        Map<String, List<String>> starting11 = career.getTeamStarting11();
        if (starting11 == null) return null;
        List<String> ids = starting11.get(teamId);
        if (ids == null || ids.isEmpty()) return null;
        if (ids.size() > 11) {
            throw new IllegalArgumentException(
                    teamLabel + " starting XI has " + ids.size()
                    + " entries â€” maximum is 11 for teamId: " + teamId);
        }
        // derivation, but that masks user intent; the user explicitly submitted
        // a short-handed XI and the engine should honour it).
        int min = com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS;
        if (ids.size() < min) {
            throw new IllegalArgumentException(
                    teamLabel + " starting XI has " + ids.size()
                    + " entries â€” minimum is " + min + " for teamId: " + teamId);
        }

        List<SessionPlayer> resolved = new ArrayList<>();
        int staleCount = 0;
        for (String pid : ids) {
            if (pid == null || pid.isBlank()) {
                // Null/blank entries in teamStarting11 are clearly invalid
                // user data; preserve the original IAE so the user can fix
                // their lineup explicitly.
                throw new IllegalArgumentException(
                        teamLabel + " starting XI contains null/blank playerId for teamId: " + teamId);
            }
            SessionPlayer p = career.getSessionPlayer(pid);
            if (p == null) {
                // removed from the playerManager between rounds. Count it
                // and continue; if ALL entries are stale we fall back to
                // the squad (via returning null), otherwise we accept the
                // partial lineup.
                staleCount++;
                continue;
            }
            resolved.add(p);
        }
        if (staleCount > 0) {
            org.slf4j.LoggerFactory.getLogger(V24MatchContextFactory.class).warn(
                "[BUG-003] teamStarting11 for teamId={} has {} stale playerId(s) "
                + "(player removed from playerManager between rounds). "
                + "Resolved {}/{} â€” falling back to squad derivation to ensure "
                + "a complete 11-player starting XI.",
                teamId, staleCount, resolved.size(), ids.size());
            // is partially invalid; fall back to deriveStartingXIfromSquad
            // to ensure a complete 11-player starting XI. Partial lineups
            // (e.g. 10 valid + 1 stale) would short the team by 1 player,
            // which the engine would then complain about at runtime.
            return null;
        }
        if (resolved.size() < min) {
            // The teamStarting11 had too few entries (already validated
            // above for > min, so this means it's between 0 and min). Fall
            // back to the squad so the match can still start.
            return null;
        }
        return resolved;
    }

    private List<SessionPlayer> deriveStartingXIfromSquad(
            CareerSave career, String teamId, String formation, String teamLabel) {
        // Try CareerTeamManager.teamSquads (written by CareerTeamManager.assignPlayerToSquad)
        List<String> squadIds = career.getTeamManager().getSquadPlayerIds(teamId);
        int min = com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS;
        if (squadIds == null || squadIds.size() < min) {
            throw new IllegalArgumentException(
                    teamLabel + " squad has only "
                    + (squadIds != null ? squadIds.size() : 0)
                    + " players for teamId: " + teamId
                    + " â€” need at least " + min + " for starting XI");
        }
        List<SessionPlayer> squad = new ArrayList<>();
        for (String squadId : squadIds) {
            SessionPlayer p = career.getSessionPlayer(squadId);
            if (p == null) {
                throw new IllegalArgumentException(
                        teamLabel + " squad player not found: " + squadId);
            }
            squad.add(p);
        }
        List<SessionPlayer> smartStarters = deriveSmartStartingXIfromSquad(squad, formation);
        if (smartStarters.size() >= min) {
            return smartStarters;
        }
        // Last-resort legacy fallback: use strongest available players, not raw
        // import order, so CPU teams never degrade because of JSON/list order.
        return squad.stream()
                .sorted(Comparator.comparingInt(this::playerOverallSafe).reversed())
                .limit(11)
                .toList();
    }

    private List<SessionPlayer> deriveSmartStartingXIfromSquad(List<SessionPlayer> squad, String formation) {
        FormationDTO formationDto = formationService.getFormationByName(formation);
        if (formationDto == null || formationDto.positions() == null || formationDto.positions().isEmpty()) {
            formationDto = formationService.getFormationByName("4-4-2");
        }
        if (formationDto == null) {
            return List.of();
        }

        List<SessionPlayer> remaining = new ArrayList<>(squad);
        List<SessionPlayer> selected = new ArrayList<>();
        List<FormationPositionDTO> positions = formationDto.positions().stream()
                .sorted(Comparator.comparing(FormationPositionDTO::index))
                .toList();

        for (FormationPositionDTO position : positions) {
            if (remaining.isEmpty()) break;
            SessionPlayer best = remaining.stream()
                    .max(Comparator
                            .comparingInt((SessionPlayer player) -> fallbackRoleFitScore(player, position))
                            .thenComparingInt(this::fallbackRoleStrength)
                            .thenComparingInt(this::playerOverallSafe))
                    .orElse(null);
            if (best != null) {
                selected.add(best);
                remaining.remove(best);
            }
        }
        return selected;
    }

    private int fallbackRoleFitScore(SessionPlayer player, FormationPositionDTO position) {
        String playerProfile = fallbackPlayerProfile(player);
        String slotProfile = fallbackSlotProfile(position != null ? position.role() : null);
        if (playerProfile.equals(slotProfile)) return 100;
        if ("GK".equals(slotProfile) || "GK".equals(playerProfile)) return 0;
        if ("WIDE_DEF".equals(slotProfile) && "DEF".equals(playerProfile)) return 92;
        if ("DEF".equals(slotProfile) && "WIDE_DEF".equals(playerProfile)) return 90;
        if ("WIDE_ATT".equals(slotProfile) && "ATT".equals(playerProfile)) return 88;
        if ("ATT".equals(slotProfile) && "WIDE_ATT".equals(playerProfile)) return 86;
        if ("AM".equals(slotProfile) && ("MID".equals(playerProfile) || "WIDE_ATT".equals(playerProfile))) return 82;
        if ("MID".equals(slotProfile) && ("DM".equals(playerProfile) || "AM".equals(playerProfile))) return 80;
        if ("DM".equals(slotProfile) && ("MID".equals(playerProfile) || "DEF".equals(playerProfile))) return 78;
        if ("WIDE_MID".equals(slotProfile)
                && ("MID".equals(playerProfile) || "WIDE_ATT".equals(playerProfile) || "WIDE_DEF".equals(playerProfile))) {
            return 76;
        }
        if ("MID".equals(slotProfile) && "WIDE_MID".equals(playerProfile)) return 74;
        if ("ATT".equals(slotProfile) && "AM".equals(playerProfile)) return 70;
        if ("AM".equals(slotProfile) && "ATT".equals(playerProfile)) return 68;
        if ("DEF".equals(slotProfile) && "DM".equals(playerProfile)) return 66;
        if ("DM".equals(slotProfile) && "WIDE_DEF".equals(playerProfile)) return 62;
        if ("MID".equals(slotProfile) && ("DEF".equals(playerProfile) || "ATT".equals(playerProfile))) return 52;
        if ("DEF".equals(slotProfile) && "MID".equals(playerProfile)) return 48;
        if ("ATT".equals(slotProfile) && "MID".equals(playerProfile)) return 48;
        return 35;
    }

    private String fallbackPlayerProfile(SessionPlayer player) {
        if (player == null || player.getPosition() == null) return "MID";
        String position = player.getPosition().toUpperCase(Locale.ROOT);
        return switch (position) {
            case "GK" -> "GK";
            case "CB" -> "DEF";
            case "LB", "RB", "LWB", "RWB" -> "WIDE_DEF";
            case "CDM", "DM" -> "DM";
            case "CM" -> "MID";
            case "CAM", "AM" -> "AM";
            case "LM", "RM" -> "WIDE_MID";
            case "LW", "RW" -> "WIDE_ATT";
            case "ST", "CF" -> "ATT";
            case "DEF" -> "DEF";
            case "MID" -> "MID";
            case "WINGER" -> "WIDE_ATT";
            case "ATT" -> "ATT";
            default -> "MID";
        };
    }

    private String fallbackSlotProfile(String role) {
        if (role == null || role.isBlank()) return "MID";
        String normalized = role.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GK" -> "GK";
            case "LB", "RB", "LWB", "RWB" -> "WIDE_DEF";
            case "CB" -> "DEF";
            case "CDM", "DM" -> "DM";
            case "LM", "RM" -> "WIDE_MID";
            case "CAM", "AM" -> "AM";
            case "LW", "RW" -> "WIDE_ATT";
            case "ST", "CF" -> "ATT";
            default -> "MID";
        };
    }

    private int fallbackRoleStrength(SessionPlayer player) {
        if (player == null) return 0;
        String profile = fallbackPlayerProfile(player);
        return switch (profile) {
            case "GK" -> player.getDefense() + player.getMentality();
            case "DEF", "WIDE_DEF", "DM" -> player.getDefense() * 2 + player.getMentality() + player.getStamina();
            case "MID", "AM", "WIDE_MID" -> player.getTechnique() * 2 + player.getMentality() + player.getStamina();
            case "ATT", "WIDE_ATT" -> player.getAttack() * 2 + player.getTechnique() + player.getSpeed();
            default -> playerOverallSafe(player);
        };
    }

    private int playerOverallSafe(SessionPlayer player) {
        if (player == null) return 0;
        Integer overall = player.calculateOverall();
        return overall != null ? overall : 0;
    }

    private List<SessionPlayer> deriveBench(
            CareerSave career, String teamId, List<SessionPlayer> starters) {
        Set<String> starterIds = starters.stream()
                .map(SessionPlayer::getSessionPlayerId)
                .collect(Collectors.toSet());
        List<SessionPlayer> squad = career.getTeamSquad(teamId);
        if (squad == null || squad.isEmpty()) return List.of();
        return squad.stream()
                .filter(p -> !starterIds.contains(p.getSessionPlayerId()))
                .collect(Collectors.toList());
    }

    private Map<String, LineupSlotDTO> resolveSlotsByPlayerId(
            CareerSave career,
            String teamId,
            String formation,
            List<SessionPlayer> starters) {
        if (career == null || teamId == null || teamId.isBlank()) return Map.of();
        Map<String, Map<String, LineupSlotDTO>> allSlots = career.getTeamStarting11SubdivisionSlots();
        if (allSlots == null || allSlots.isEmpty()) return Map.of();
        Map<String, LineupSlotDTO> teamSlots = allSlots.get(teamId);
        if (teamSlots == null || teamSlots.isEmpty()) return Map.of();
        Set<String> starterIds = starters == null
                ? Set.of()
                : starters.stream()
                        .map(SessionPlayer::getSessionPlayerId)
                        .filter(id -> id != null && !id.isBlank())
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (starterIds.isEmpty()) return Map.of();

        Map<String, LineupSlotDTO> byPlayerId = new LinkedHashMap<>();
        for (LineupSlotDTO slot : teamSlots.values()) {
            if (slot == null || slot.playerId() == null || slot.playerId().isBlank()) continue;
            if (!starterIds.contains(slot.playerId())) continue;
            byPlayerId.put(slot.playerId(), enrichWithFormationCoords(slot, formation));
            if (byPlayerId.size() >= starterIds.size()) break;
        }
        return byPlayerId;
    }

    private LineupSlotDTO enrichWithFormationCoords(LineupSlotDTO slot, String formation) {
        if (slot == null || slot.subdivisionId() == null || formation == null || formation.isBlank()) {
            return slot;
        }
        boolean hasManualX = slot.customXPercent() != null && Double.isFinite(slot.customXPercent());
        boolean hasManualY = slot.customYPercent() != null && Double.isFinite(slot.customYPercent());
        if (hasManualX && hasManualY) {
            return slot;
        }
        double[] coords = formationService.getCoordsBySubdivision(formation, slot.subdivisionId());
        if (coords == null || coords.length < 2) {
            return slot;
        }
        return new LineupSlotDTO(
                slot.playerId(),
                slot.subdivisionId(),
                hasManualX ? slot.customXPercent() : coords[0],
                hasManualY ? slot.customYPercent() : coords[1]);
    }
}
