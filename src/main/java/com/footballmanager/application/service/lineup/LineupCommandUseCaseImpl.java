package com.footballmanager.application.service.lineup;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.port.in.lineup.LineupCommandUseCase;
import com.footballmanager.domain.port.in.lineup.LineupView;
import com.footballmanager.domain.port.in.lineup.LineupWarning;
import com.footballmanager.domain.model.valueobject.Formation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LineupCommandUseCaseImpl implements LineupCommandUseCase {

    private final CareerSessionService careerSessionService;
    private final LineupHelper lineupHelper;
    private final FormationService formationService;


    @Override
    public Mono<LineupView> autoSelectLineup(UUID userId, String formationCode) {
        Formation formation = Formation.fromString(formationCode);
        return careerSessionService.continueCareer(userId)
            .flatMap(career -> {
                String userTeamId = career.getUserSessionTeamId();
                LineupAutoSelector.AutoSelectResult result = new LineupAutoSelector(lineupHelper, formationService).performAutoSelect(career, userTeamId, formation);
                List<SessionPlayer> lineup = result.lineup();
                List<LineupWarning> warnings = result.warnings();

                List<String> lineupIds = lineup.stream()
                    .map(SessionPlayer::getSessionPlayerId)
                    .toList();
                career.getTeamStarting11().put(userTeamId, lineupIds);
                Map<String, LineupSlot> slotMap = new LineupDtoAssembler(formationService, lineupHelper).buildAutoSelectSlotMap(formation, lineup, true);
                if (slotMap.size() != LineupRules.TARGET_LINEUP_PLAYERS) {
                    throw new IllegalStateException(
                        "Auto-select slot assignment incomplete: " + slotMap.size()
                        + " / " + LineupRules.TARGET_LINEUP_PLAYERS
                        + " (formation: " + formation.getCode() + ", squad may be too small)"
                    );
                }
                career.replaceTeamStarting11SubdivisionRaw(userTeamId, slotMap);
                career.getTeamStarting11Formation().put(userTeamId, formation.getCode());
                syncSessionTeamFormation(career, userTeamId, formation.getCode());

                return careerSessionService.saveCareer(career)
                    .thenReturn(new LineupDtoAssembler(formationService, lineupHelper).buildLineupDTO(lineup, formation, warnings, slotMap));
            });
    }

    @Override
    public Mono<LineupView> manualSelectLineup(UUID userId, String formationCode, List<String> playerIds) {
        return manualSelectLineupWithSlots(userId, formationCode, playerIds, List.of());
    }

    @Override
    public Mono<LineupView> manualSelectLineupWithSlots(UUID userId, String formationCode,
                                                      List<String> playerIds,
                                                      List<LineupSlot> slots) {
        Formation formation = Formation.fromString(formationCode);

        if (playerIds.size() < LineupRules.MIN_AVAILABLE_PLAYERS) {
            return Mono.error(new NotEnoughPlayersException(
                "Minimum " + LineupRules.MIN_AVAILABLE_PLAYERS
                + " available players required, got " + playerIds.size()));
        }
        if (playerIds.size() > LineupRules.MAX_LINEUP_PLAYERS) {
            return Mono.error(new IllegalArgumentException(
                "Maximum " + LineupRules.MAX_LINEUP_PLAYERS
                + " players allowed, got " + playerIds.size()));
        }
        if (playerIds.stream().distinct().count() != playerIds.size()) {
            return Mono.error(new IllegalArgumentException("Cannot select same player twice"));
        }

        return careerSessionService.continueCareer(userId)
            .flatMap(career -> {
                String userTeamId = career.getUserSessionTeamId();
                List<String> squadIds = career.getTeamManager().getTeamSquads().get(userTeamId);

                for (String playerId : playerIds) {
                    if (!squadIds.contains(playerId)) {
                        return Mono.error(new IllegalArgumentException(
                            "Player " + playerId + " not in your squad"));
                    }
                }

                List<SessionPlayer> selectedPlayers = playerIds.stream()
                    .map(id -> career.getSessionPlayers().get(id))
                    .filter(Objects::nonNull)
                    .toList();
                lineupHelper.validatePlayerFitness(selectedPlayers);
                List<LineupWarning> warnings = lineupHelper.detectShortHandedWarnings(selectedPlayers);
                if (selectedPlayers.size() < LineupRules.TARGET_LINEUP_PLAYERS) {
                    warnings = new ArrayList<>(warnings);
                    warnings.add(LineupWarning.shortHanded(selectedPlayers.size()));
                }

                career.getTeamStarting11().put(userTeamId, playerIds);
                career.getTeamStarting11Formation().put(userTeamId, formation.getCode());
                syncSessionTeamFormation(career, userTeamId, formation.getCode());
                boolean hasExplicitSlotOverrides = slots != null && !slots.isEmpty();
                boolean hasCustomSlotOverrides = hasExplicitSlotOverrides
                    && slots.stream().anyMatch(this::hasCustomCoordinates);
                Map<String, LineupSlot> slotMap = new LineupDtoAssembler(formationService, lineupHelper).buildAutoSelectSlotMap(
                    formation,
                    selectedPlayers,
                    !hasExplicitSlotOverrides && selectedPlayers.size() == LineupRules.TARGET_LINEUP_PLAYERS);
                if (hasExplicitSlotOverrides) {
                    for (LineupSlot slot : slots) {
                        if (slot.subdivisionId() == null || slot.subdivisionId().isBlank()) {
                            continue;
                        }
                        if (slot.playerId() == null || slot.playerId().isBlank()) {
                            continue;
                        }
                        if (!playerIds.contains(slot.playerId())) {
                            continue;
                        }
                        slotMap.put(slot.subdivisionId(), slot);
                    }
                }
                career.replaceTeamStarting11SubdivisionRaw(userTeamId, slotMap);

                return careerSessionService.saveCareer(career)
                    .thenReturn(new LineupDtoAssembler(formationService, lineupHelper).buildLineupDTO(selectedPlayers, formation, warnings, slotMap));
            });
    }

    private boolean hasCustomCoordinates(LineupSlot slot) {
        return slot != null
            && ((slot.customXPercent() != null && Double.isFinite(slot.customXPercent()))
                || (slot.customYPercent() != null && Double.isFinite(slot.customYPercent())));
    }

    private void syncSessionTeamFormation(CareerSave career, String teamId, String formationCode) {
        SessionTeam team = career.getSessionTeam(teamId);
        if (team != null) {
            team.setFormation(formationCode);
        }
    }

    @Override
    public Mono<Void> confirmLineup(UUID userId) {
        return careerSessionService.continueCareer(userId)
            .flatMap(career -> {
                String userTeamId = career.getUserSessionTeamId();
                List<String> lineupIds = career.getTeamStarting11().get(userTeamId);

                if (lineupIds == null) {
                    return Mono.error(new NotEnoughPlayersException(
                        "No lineup selected. Minimum "
                        + LineupRules.MIN_AVAILABLE_PLAYERS + " players required."));
                }
                int size = lineupIds.size();
                if (size < LineupRules.MIN_AVAILABLE_PLAYERS) {
                    return Mono.error(new NotEnoughPlayersException(
                        "Lineup has only " + size + " players. Minimum "
                        + LineupRules.MIN_AVAILABLE_PLAYERS + " required."));
                }
                if (size > LineupRules.MAX_LINEUP_PLAYERS) {
                    return Mono.error(new IllegalArgumentException(
                        "Lineup has " + size + " players. Maximum "
                        + LineupRules.MAX_LINEUP_PLAYERS + " allowed."));
                }

                return careerSessionService.saveCareer(career).then();
            });
    }


}
