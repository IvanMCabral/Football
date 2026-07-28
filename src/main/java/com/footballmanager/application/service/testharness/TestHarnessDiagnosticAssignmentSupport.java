package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.application.service.editor.FormationPosition;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.application.service.simulation.v24.BaselineState;
import com.footballmanager.application.service.simulation.v24.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngine;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.application.service.simulation.v24.V24MatchLineupPlayerDto;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.v24.V24ShotLocation;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PositionEffectivenessCalculator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class TestHarnessDiagnosticAssignmentSupport {

int diagnosticRoleBonus(CuratedMatrixRoleProfile profile, String slotRole) {
        if (profile == null || slotRole == null || slotRole.isBlank()) return 0;
        String role = slotRole.toUpperCase(Locale.ROOT);
        if (profile.roles().contains(role)) return 26;
        if (curatedMatrixRoleFamilyMatch(profile.roles(), role)) return 12;
        return 0;
    }

int diagnosticSideBonus(CuratedMatrixRoleProfile profile, String slotSide) {
        if (profile == null || slotSide == null) return 0;
        if ("LEFT".equals(slotSide) || "RIGHT".equals(slotSide)) {
            if (profile.sides().contains(slotSide) || profile.sides().contains("BOTH")) return 22;
            if (profile.sides().contains(matrixOppositeSide(slotSide))) return -34;
        }
        if ("CENTER".equals(slotSide) && profile.sides().contains("CENTER")) return 8;
        return 0;
    }

String assignmentVerdict(String natural, String tactical, int roleBonus, int sideBonus, int assignmentScore) {
        if ("GK".equals(natural)) return "OK";
        if (sideBonus < 0) return "Revisar lado";
        if (assignmentScore < 70) return "Revisar rol";
        if (roleBonus > 0 || sideBonus > 0 || Objects.equals(natural, tactical)) return "OK";
        return "Aceptable";
    }

String assignmentRead(
            SessionPlayer player,
            String natural,
            String slotRole,
            String slotSide,
            CuratedMatrixRoleProfile profile,
            String verdict,
            int roleBonus,
            int sideBonus) {
        String name = player != null ? player.getName() : "Jugador";
        if (isWingbackFallback(slotRole, natural)) {
            return name + " queda en " + slotRole
                + " como fallback de carrilero: faltan perfiles naturales compatibles "
                + compatibleWingbackProfiles(slotRole)
                + ". Es jugable, pero debe penalizarse y leerse como alerta tactica.";
        }
        if (isDefensiveLineFallback(slotRole, natural)) {
            return name + " queda en " + slotRole
                + " como fallback defensivo: faltan perfiles naturales compatibles "
                + compatibleDefensiveProfiles(slotRole)
                + ". Puede sostener la formacion, pero expone duelos y coberturas.";
        }
        if (isAttackingLineFallback(slotRole, natural)) {
            return name + " queda en " + slotRole
                + " como fallback ofensivo: faltan perfiles naturales compatibles "
                + compatibleAttackingProfiles(slotRole)
                + ". Puede completar el once, pero debe afectar amenaza, desmarques y definicion.";
        }
        if ("Revisar lado".equals(verdict)) {
            return name + " queda en " + slotSide + " pero su perfil prefiere "
                + (profile != null ? String.join("/", profile.sides()) : "otro lado") + ".";
        }
        if ("Revisar rol".equals(verdict)) {
            return name + " queda en " + slotRole + " con bajo encaje para su perfil.";
        }
        if (roleBonus > 0 && sideBonus > 0) {
            return "Encaja por rol y lado.";
        }
        if (roleBonus > 0) {
            return "Encaja por rol; lado neutro o no curado.";
        }
        if (sideBonus > 0) {
            return "Encaja por lado; rol aceptable por familia/categoria.";
        }
        return "Asignacion aceptable sin perfil curado fuerte.";
    }

boolean isWingbackFallback(String slotRole, String natural) {
        if (slotRole == null || natural == null) return false;
        String role = slotRole.toUpperCase(Locale.ROOT);
        String playerPosition = natural.toUpperCase(Locale.ROOT);
        if ("LWB".equals(role)) {
            return !Set.of("LWB", "LB", "LM", "LW", "WINGER", "DEF").contains(playerPosition);
        }
        if ("RWB".equals(role)) {
            return !Set.of("RWB", "RB", "RM", "RW", "WINGER", "DEF").contains(playerPosition);
        }
        return false;
    }

String compatibleWingbackProfiles(String slotRole) {
        if (slotRole == null) return "(LWB/RWB/LB/RB/LM/RM/LW/RW/WINGER)";
        return switch (slotRole.toUpperCase(Locale.ROOT)) {
            case "LWB" -> "(LWB/LB/LM/LW/WINGER/DEF)";
            case "RWB" -> "(RWB/RB/RM/RW/WINGER/DEF)";
            default -> "(LWB/RWB/LB/RB/LM/RM/LW/RW/WINGER)";
        };
    }

boolean isDefensiveLineFallback(String slotRole, String natural) {
        if (slotRole == null || natural == null) return false;
        String role = slotRole.toUpperCase(Locale.ROOT);
        String playerPosition = natural.toUpperCase(Locale.ROOT);
        return switch (role) {
            case "CB" -> !Set.of("CB", "DEF", "CDM", "LB", "RB", "LWB", "RWB").contains(playerPosition);
            case "LB" -> !Set.of("LB", "LWB", "LM", "LW", "DEF", "CB").contains(playerPosition);
            case "RB" -> !Set.of("RB", "RWB", "RM", "RW", "DEF", "CB").contains(playerPosition);
            default -> false;
        };
    }

String compatibleDefensiveProfiles(String slotRole) {
        if (slotRole == null) return "(CB/LB/RB/LWB/RWB/DEF/CDM)";
        return switch (slotRole.toUpperCase(Locale.ROOT)) {
            case "CB" -> "(CB/DEF/CDM/LB/RB/LWB/RWB)";
            case "LB" -> "(LB/LWB/LM/LW/DEF/CB)";
            case "RB" -> "(RB/RWB/RM/RW/DEF/CB)";
            default -> "(CB/LB/RB/LWB/RWB/DEF/CDM)";
        };
    }

boolean isAttackingLineFallback(String slotRole, String natural) {
        if (slotRole == null || natural == null) return false;
        String role = slotRole.toUpperCase(Locale.ROOT);
        String playerPosition = natural.toUpperCase(Locale.ROOT);
        return switch (role) {
            case "ST", "CF" -> !Set.of("ST", "CF", "ATT", "CAM", "WINGER", "LW", "RW").contains(playerPosition);
            case "LW" -> !Set.of("LW", "LM", "WINGER", "ATT", "CF", "ST", "LWB").contains(playerPosition);
            case "RW" -> !Set.of("RW", "RM", "WINGER", "ATT", "CF", "ST", "RWB").contains(playerPosition);
            default -> false;
        };
    }

String compatibleAttackingProfiles(String slotRole) {
        if (slotRole == null) return "(ST/CF/ATT/CAM/LW/RW/WINGER)";
        return switch (slotRole.toUpperCase(Locale.ROOT)) {
            case "ST", "CF" -> "(ST/CF/ATT/CAM/WINGER/LW/RW)";
            case "LW" -> "(LW/LM/WINGER/ATT/CF/ST/LWB)";
            case "RW" -> "(RW/RM/WINGER/ATT/CF/ST/RWB)";
            default -> "(ST/CF/ATT/CAM/LW/RW/WINGER)";
        };
    }

int formationPositionFitScore(SessionPlayer player, FormationPosition position) {
        String playerProfile = matrixPlayerProfile(player);
        String slotProfile = matrixSlotProfile(position != null ? position.role() : null);
        int baseScore;
        if (playerProfile.equals(slotProfile)) baseScore = 100;
        else if ("WIDE_DEF".equals(slotProfile) && "DEF".equals(playerProfile)) baseScore = 92;
        else if ("DEF".equals(slotProfile) && "WIDE_DEF".equals(playerProfile)) baseScore = 90;
        else if ("WIDE_ATT".equals(slotProfile) && "ATT".equals(playerProfile)) baseScore = 88;
        else if ("ATT".equals(slotProfile) && "WIDE_ATT".equals(playerProfile)) baseScore = 86;
        else if ("AM".equals(slotProfile) && ("MID".equals(playerProfile) || "WIDE_ATT".equals(playerProfile))) baseScore = 82;
        else if ("MID".equals(slotProfile) && ("DM".equals(playerProfile) || "AM".equals(playerProfile))) baseScore = 80;
        else if ("DM".equals(slotProfile) && ("MID".equals(playerProfile) || "DEF".equals(playerProfile))) baseScore = 78;
        else if ("WIDE_MID".equals(slotProfile) && ("MID".equals(playerProfile) || "WIDE_ATT".equals(playerProfile) || "WIDE_DEF".equals(playerProfile))) baseScore = 76;
        else if ("MID".equals(slotProfile) && "WIDE_MID".equals(playerProfile)) baseScore = 74;
        else if ("ATT".equals(slotProfile) && "AM".equals(playerProfile)) baseScore = 70;
        else if ("AM".equals(slotProfile) && "ATT".equals(playerProfile)) baseScore = 68;
        else if ("DEF".equals(slotProfile) && "DM".equals(playerProfile)) baseScore = 66;
        else if ("DM".equals(slotProfile) && "WIDE_DEF".equals(playerProfile)) baseScore = 62;
        else if ("MID".equals(slotProfile) && ("DEF".equals(playerProfile) || "ATT".equals(playerProfile))) baseScore = 52;
        else if ("DEF".equals(slotProfile) && "MID".equals(playerProfile)) baseScore = 48;
        else if ("ATT".equals(slotProfile) && "MID".equals(playerProfile)) baseScore = 48;
        else if ("GK".equals(slotProfile) || "GK".equals(playerProfile)) baseScore = 0;
        else baseScore = 35;
        return baseScore + curatedMatrixSlotBonus(player, position);
    }

int curatedMatrixSlotBonus(SessionPlayer player, FormationPosition slot) {
        CuratedMatrixRoleProfile profile = curatedMatrixRoleProfile(player);
        if (profile == null || slot == null || slot.role() == null) {
            return 0;
        }
        String role = slot.role().toUpperCase(Locale.ROOT);
        int bonus = 0;
        if (profile.roles().contains(role)) {
            bonus += 26;
        } else if (curatedMatrixRoleFamilyMatch(profile.roles(), role)) {
            bonus += 12;
        }
        String slotSide = matrixSlotSide(slot);
        if ("LEFT".equals(slotSide) || "RIGHT".equals(slotSide)) {
            if (profile.sides().contains(slotSide) || profile.sides().contains("BOTH")) {
                bonus += 22;
            } else if (profile.sides().contains(matrixOppositeSide(slotSide))) {
                bonus -= 34;
            }
        } else if ("CENTER".equals(slotSide) && profile.sides().contains("CENTER")) {
            bonus += 8;
        }
        return bonus;
    }

boolean curatedMatrixRoleFamilyMatch(Set<String> playerRoles, String slotRole) {
        if (Set.of("LB", "LWB", "LM", "LW").contains(slotRole)) {
            return playerRoles.stream().anyMatch(Set.of("LB", "LWB", "LM", "LW")::contains);
        }
        if (Set.of("RB", "RWB", "RM", "RW").contains(slotRole)) {
            return playerRoles.stream().anyMatch(Set.of("RB", "RWB", "RM", "RW")::contains);
        }
        if (Set.of("CB", "CDM", "CM", "CAM", "ST", "CF").contains(slotRole)) {
            return playerRoles.stream().anyMatch(Set.of("CB", "CDM", "CM", "CAM", "ST", "CF")::contains);
        }
        return false;
    }

String matrixSlotSide(FormationPosition slot) {
        String role = slot.role() != null ? slot.role().toUpperCase(Locale.ROOT) : "";
        if (Set.of("LB", "LWB", "LM", "LW").contains(role)) return "LEFT";
        if (Set.of("RB", "RWB", "RM", "RW").contains(role)) return "RIGHT";
        if (Set.of("GK", "CB", "CDM", "CM", "ST", "CF").contains(role)) return "CENTER";
        Double x = slot.xPercent();
        if (x != null && x <= 42) return "LEFT";
        if (x != null && x >= 58) return "RIGHT";
        return "CENTER";
    }

String matrixOppositeSide(String side) {
        return "LEFT".equals(side) ? "RIGHT" : "LEFT";
    }

CuratedMatrixRoleProfile curatedMatrixRoleProfile(SessionPlayer player) {
        if (player == null || player.getName() == null) {
            return null;
        }
        return switch (normalizeMatrixPlayerName(player.getName())) {
            case "dani carvajal" -> new CuratedMatrixRoleProfile(Set.of("RB", "RWB"), Set.of("RIGHT"));
            case "david alaba" -> new CuratedMatrixRoleProfile(Set.of("CB", "LB"), Set.of("LEFT", "CENTER"));
            case "ferland mendy", "fran garcia" -> new CuratedMatrixRoleProfile(Set.of("LB", "LWB"), Set.of("LEFT"));
            case "lucas vazquez" -> new CuratedMatrixRoleProfile(Set.of("RB", "RM", "RWB"), Set.of("RIGHT"));
            case "vinicius junior" -> new CuratedMatrixRoleProfile(Set.of("LW", "LM"), Set.of("LEFT"));
            case "rodrygo goes" -> new CuratedMatrixRoleProfile(Set.of("RW", "LW", "ST", "CF"), Set.of("RIGHT", "BOTH"));
            case "brahim diaz" -> new CuratedMatrixRoleProfile(Set.of("RW", "CAM", "RM"), Set.of("RIGHT", "CENTER"));
            case "federico valverde" -> new CuratedMatrixRoleProfile(Set.of("CM", "RM", "CDM"), Set.of("CENTER", "RIGHT"));
            case "eduardo camavinga" -> new CuratedMatrixRoleProfile(Set.of("CM", "CDM", "LB"), Set.of("CENTER", "LEFT"));
            default -> null;
        };
    }

String normalizeMatrixPlayerName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

String matrixPlayerProfile(SessionPlayer player) {
        if (player == null || player.getPosition() == null) return "MID";
        String position = player.getPosition().toUpperCase(Locale.ROOT);
        return switch (position) {
            case "GK" -> "GK";
            case "DEF" -> "DEF";
            case "MID" -> "MID";
            case "WINGER" -> "WIDE_ATT";
            case "ATT" -> "ATT";
            default -> "MID";
        };
    }

String matrixSlotProfile(String role) {
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

record CuratedMatrixRoleProfile(Set<String> roles, Set<String> sides) {}
}

