package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementación de UseCase para comandos del lineup.
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
                List<SessionPlayer> lineup = performAutoSelect(career, userTeamId, formation);

                List<String> lineupIds = lineup.stream()
                    .map(SessionPlayer::getSessionPlayerId)
                    .toList();
                career.getTeamStarting11().put(userTeamId, lineupIds);

                return careerRepository.save(career)
                    .thenReturn(buildLineupDTO(lineup, formation));
            });
    }

    @Override
    public Mono<LineupDTO> manualSelectLineup(UUID userId, String formationCode, List<String> playerIds) {
        Formation formation = Formation.fromString(formationCode);

        if (playerIds.size() != 11) {
            return Mono.error(new IllegalArgumentException("Must select exactly 11 players"));
        }

        if (playerIds.stream().distinct().count() != 11) {
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

                lineupHelper.validateLineupBasic(selectedPlayers);
                lineupHelper.validateLineupFormation(selectedPlayers, formation);
                lineupHelper.validatePlayerFitness(selectedPlayers);

                career.getTeamStarting11().put(userTeamId, playerIds);

                return careerRepository.save(career)
                    .thenReturn(buildLineupDTO(selectedPlayers, formation));
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

                if (lineupIds == null || lineupIds.size() != 11) {
                    return Mono.error(new IllegalStateException("No valid lineup selected"));
                }

                return careerRepository.save(career).then();
            });
    }

    private List<SessionPlayer> performAutoSelect(CareerSave career, String teamId, Formation formation) {
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

        List<SessionPlayer> lineup = new ArrayList<>();

        // GK
        availablePlayers.stream()
            .filter(p -> "GK".equals(p.getPosition()))
            .findFirst()
            .ifPresentOrElse(
                lineup::add,
                () -> { throw new NotEnoughPlayersException("No available goalkeeper"); }
            );

        // Defenders
        List<SessionPlayer> defenders = availablePlayers.stream()
            .filter(p -> lineupHelper.isDefender(p.getPosition()))
            .limit(formation.getDefenders())
            .toList();
        if (defenders.size() < formation.getDefenders()) {
            throw new NotEnoughPlayersException("Not enough defenders");
        }
        lineup.addAll(defenders);

        // Midfielders
        List<SessionPlayer> midfielders = availablePlayers.stream()
            .filter(p -> lineupHelper.isMidfielder(p.getPosition()))
            .limit(formation.getMidfielders())
            .toList();
        if (midfielders.size() < formation.getMidfielders()) {
            throw new NotEnoughPlayersException("Not enough midfielders");
        }
        lineup.addAll(midfielders);

        // Attackers
        List<SessionPlayer> attackers = availablePlayers.stream()
            .filter(p -> lineupHelper.isAttacker(p.getPosition()))
            .limit(formation.getAttackers())
            .toList();
        if (attackers.size() < formation.getAttackers()) {
            throw new NotEnoughPlayersException("Not enough attackers");
        }
        lineup.addAll(attackers);

        return lineup;
    }

    private LineupDTO buildLineupDTO(List<SessionPlayer> players, Formation formation) {
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

        return new LineupDTO(formation.getCode(), playerDTOs, false);
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
