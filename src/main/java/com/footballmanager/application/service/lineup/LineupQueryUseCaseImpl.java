package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.lineup.LineupQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementación de UseCase para consultas del lineup.
 */
@Service
@RequiredArgsConstructor
public class LineupQueryUseCaseImpl implements LineupQueryUseCase {

    private final CareerRepository careerRepository;
    private final LineupHelper lineupHelper;

    @Override
    public Mono<LineupDTO> getCurrentLineup(UUID userId) {
        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> optionalCareer.isPresent()
                ? Mono.just(optionalCareer.get())
                : Mono.empty())
            .map(this::buildLineupDTO);
    }

    private LineupDTO buildLineupDTO(CareerSave career) {
        String userTeamId = career.getUserSessionTeamId();
        List<String> lineupIds = career.getTeamStarting11().get(userTeamId);

        if (lineupIds == null || lineupIds.isEmpty()) {
            return new LineupDTO(null, Collections.emptyList(), false);
        }

        List<SessionPlayer> lineup = lineupIds.stream()
            .map(id -> career.getSessionPlayers().get(id))
            .filter(Objects::nonNull)
            .toList();

        String formationCode = lineupHelper.inferFormation(lineup);

        List<PlayerLineupDTO> playerDTOs = lineup.stream()
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

        return new LineupDTO(formationCode, playerDTOs, true);
    }
}
