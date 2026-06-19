package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupWarningDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO;
import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.lineup.LineupCommandUseCase;
import com.footballmanager.domain.model.valueobject.Formation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementación de UseCase para comandos del lineup.
 *
 * <p>V24D6U2: Supports short-handed lineups. See {@link LineupRules}.
 * Auto-select now produces lineups in {@code [MIN, MAX]} and returns
 * warnings on short-handed or no-GK. Manual select and confirmLineup
 * accept the same range and reject out-of-bounds with a domain
 * exception that maps to 422.
 */
@Service
@RequiredArgsConstructor
public class LineupCommandUseCaseImpl implements LineupCommandUseCase {

    private final CareerRepository careerRepository;
    private final LineupHelper lineupHelper;

    @Override
    public Mono<LineupDTO> autoSelectLineup(UUID userId, String formationCode) {
        Formation formation = Formation.fromString(formationCode);

        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> optionalCareer.isPresent()
                ? Mono.just(optionalCareer.get())
                : Mono.empty())
            .flatMap(career -> {
                String userTeamId = career.getUserSessionTeamId();
                AutoSelectResult result = performAutoSelect(career, userTeamId, formation);
                List<SessionPlayer> lineup = result.lineup();
                List<LineupWarningDTO> warnings = result.warnings();

                List<String> lineupIds = lineup.stream()
                    .map(SessionPlayer::getSessionPlayerId)
                    .toList();
                career.getTeamStarting11().put(userTeamId, lineupIds);

                return careerRepository.save(career)
                    .thenReturn(buildLineupDTO(lineup, formation, warnings));
            });
    }

    @Override
    public Mono<LineupDTO> manualSelectLineup(UUID userId, String formationCode, List<String> playerIds) {
        // Backward compat: legacy callers sin slots.
        return manualSelectLineupWithSlots(userId, formationCode, playerIds, List.of());
    }

    @Override
    public Mono<LineupDTO> manualSelectLineupWithSlots(UUID userId, String formationCode,
                                                      List<String> playerIds,
                                                      List<LineupSlotDTO> slots) {
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

        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> optionalCareer.isPresent()
                ? Mono.just(optionalCareer.get())
                : Mono.empty())
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

                // Reject if selected players are not all available (injured/suspended/low energy)
                lineupHelper.validatePlayerFitness(selectedPlayers);

                // Compute warnings (no-GK, position-deficit informational)
                List<LineupWarningDTO> warnings = lineupHelper.detectShortHandedWarnings(selectedPlayers);
                if (selectedPlayers.size() < LineupRules.TARGET_LINEUP_PLAYERS) {
                    warnings = new ArrayList<>(warnings);
                    warnings.add(LineupWarningDTO.shortHanded(selectedPlayers.size()));
                }

                career.getTeamStarting11().put(userTeamId, playerIds);

                // MVP1-lineup-cancha-1: persist subdivisionId per slot.
                // Si slots está vacío o es null, NO escribir el subdivision map
                // (mantiene backward compat — el front puede leer lineups viejos
                // con subdivisionId inferred on-the-fly desde el role).
                if (slots != null && !slots.isEmpty()) {
                    Map<String, String> slotMap = new HashMap<>();
                    for (LineupSlotDTO slot : slots) {
                        if (slot.subdivisionId() == null || slot.subdivisionId().isBlank()) {
                            continue;
                        }
                        if (slot.playerId() == null || slot.playerId().isBlank()) {
                            continue;
                        }
                        if (!playerIds.contains(slot.playerId())) {
                            // Slot referencia un playerId no incluido en este lineup — ignorar.
                            continue;
                        }
                        // Si dos slots intentan usar el mismo subdivisionId, el último gana.
                        // (Validar duplicados de subdivision se hace en read path; el write
                        // path tolera el caso porque el modal nunca debería mandarlo.)
                        slotMap.put(slot.subdivisionId(), slot.playerId());
                    }
                    if (!slotMap.isEmpty()) {
                        career.getTeamStarting11Subdivision().put(userTeamId, slotMap);
                    } else {
                        // Si todos los slots fueron inválidos, limpiamos el entry
                        // existente para no dejar datos stale.
                        career.getTeamStarting11Subdivision().remove(userTeamId);
                    }
                }

                return careerRepository.save(career)
                    .thenReturn(buildLineupDTO(selectedPlayers, formation, warnings));
            });
    }

    @Override
    public Mono<Void> confirmLineup(UUID userId) {
        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> optionalCareer.isPresent()
                ? Mono.just(optionalCareer.get())
                : Mono.empty())
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

                return careerRepository.save(career).then();
            });
    }

    private record AutoSelectResult(List<SessionPlayer> lineup, List<LineupWarningDTO> warnings) {}

    private AutoSelectResult performAutoSelect(CareerSave career, String teamId, Formation formation) {
        List<String> squadIds = career.getTeamManager().getTeamSquads().get(teamId);

        if (squadIds == null || squadIds.isEmpty()) {
            throw new NotEnoughPlayersException("No squad found for team: " + teamId);
        }

        List<SessionPlayer> availablePlayers = squadIds.stream()
            .map(id -> career.getSessionPlayers().get(id))
            .filter(Objects::nonNull)
            .filter(p -> p.getEnergy() > 20)
            .filter(this::isPlayerAvailable)
            .filter(p -> !Boolean.TRUE.equals(p.getSuspended()))
            .filter(p -> p.getSuspensionRemainingMatches() <= 0)
            .sorted(Comparator.comparing(SessionPlayer::calculateOverall).reversed())
            .toList();

        // V24D6U2: hard floor at MIN_AVAILABLE_PLAYERS, return controlled 422 if not met
        if (availablePlayers.size() < LineupRules.MIN_AVAILABLE_PLAYERS) {
            throw new NotEnoughPlayersException(
                "Minimum " + LineupRules.MIN_AVAILABLE_PLAYERS
                + " available players required, got " + availablePlayers.size());
        }

        List<SessionPlayer> lineup = new ArrayList<>();
        List<LineupWarningDTO> warnings = new ArrayList<>();
        Set<String> alreadyTaken = new HashSet<>();

        // 1. GK — prefer one, but allow missing (warning)
        availablePlayers.stream()
            .filter(p -> "GK".equals(p.getPosition()))
            .findFirst()
            .ifPresent(p -> {
                lineup.add(p);
                alreadyTaken.add(p.getSessionPlayerId());
            });

        // 2. Defenders — best-effort up to formation.getDefenders()
        List<SessionPlayer> defenders = availablePlayers.stream()
            .filter(p -> lineupHelper.isDefender(p.getPosition()))
            .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
            .limit(formation.getDefenders())
            .collect(Collectors.toList());
        lineup.addAll(defenders);
        defenders.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));

        // 3. Midfielders — best-effort up to formation.getMidfielders()
        List<SessionPlayer> midfielders = availablePlayers.stream()
            .filter(p -> lineupHelper.isMidfielder(p.getPosition()))
            .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
            .limit(formation.getMidfielders())
            .collect(Collectors.toList());
        lineup.addAll(midfielders);
        midfielders.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));

        // 4. Attackers — best-effort up to formation.getAttackers()
        List<SessionPlayer> attackers = availablePlayers.stream()
            .filter(p -> lineupHelper.isAttacker(p.getPosition()))
            .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
            .limit(formation.getAttackers())
            .collect(Collectors.toList());
        lineup.addAll(attackers);
        attackers.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));

        // 5. Fill the gap with any remaining available players (best OVR first)
        // so the lineup reaches MAX_LINEUP_PLAYERS when possible.
        if (lineup.size() < LineupRules.MAX_LINEUP_PLAYERS) {
            int slotsLeft = LineupRules.MAX_LINEUP_PLAYERS - lineup.size();
            List<SessionPlayer> fill = availablePlayers.stream()
                .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
                .limit(slotsLeft)
                .collect(Collectors.toList());
            lineup.addAll(fill);
        }

        // Defensive cap in case we have more than MAX (e.g., if formation summed > 11)
        final List<SessionPlayer> finalLineup;
        if (lineup.size() > LineupRules.MAX_LINEUP_PLAYERS) {
            finalLineup = new ArrayList<>(lineup.subList(0, LineupRules.MAX_LINEUP_PLAYERS));
        } else {
            finalLineup = lineup;
        }

        // V24D6U2: warnings
        if (lineupHelper.detectShortHandedWarnings(lineup).size() > 0) {
            warnings.addAll(lineupHelper.detectShortHandedWarnings(lineup));
        }
        if (lineup.size() < LineupRules.TARGET_LINEUP_PLAYERS) {
            warnings.add(LineupWarningDTO.shortHanded(lineup.size()));
        }

        return new AutoSelectResult(lineup, warnings);
    }

    private LineupDTO buildLineupDTO(List<SessionPlayer> players, Formation formation,
                                      List<LineupWarningDTO> warnings) {
        List<PlayerLineupDTO> playerDTOs = players.stream()
            .map(p -> new PlayerLineupDTO(
                p.getSessionPlayerId(),
                p.getName(),
                p.getPosition(),
                p.calculateOverall(),
                p.getEnergy(),
                p.getInjured(),
                p.getAge(),
                p.getYellowCards(),
                p.getRedCards(),
                p.getSuspended(),
                p.getSuspensionRemainingMatches()
            ))
            .toList();
        return new LineupDTO(formation.getCode(), playerDTOs, false, warnings);
    }

    private boolean isPlayerAvailable(SessionPlayer p) {
        if (Boolean.TRUE.equals(p.getInjured())) {
            return false;
        }
        if (p.getInjuryRemainingMatches() != null && p.getInjuryRemainingMatches() > 0) {
            return false;
        }
        return true;
    }
}
