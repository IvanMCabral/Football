package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.lineup.LineupQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementación de UseCase para consultas del lineup.
 *
 * <p>MVP1-lineup-cancha-1: si el {@code CareerSave.teamStarting11Subdivision}
 * tiene slots persistidos para el team, los incluye en la respuesta.
 * Si está vacío o ausente, retorna {@code slots=[]} (backward compat — el front
 * infiere los slots del role del jugador).
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

        List<LineupSlotDTO> slots = buildSlotsFromSubdivisionMap(career, userTeamId);

        return new LineupDTO(formationCode, playerDTOs, true, List.of(), slots);
    }

    private List<LineupSlotDTO> buildSlotsFromSubdivisionMap(CareerSave career, String userTeamId) {
        Map<String, Map<String, String>> allSlots = career.getTeamStarting11Subdivision();
        if (allSlots == null) {
            return List.of();
        }
        Map<String, String> teamSlots = allSlots.get(userTeamId);
        if (teamSlots == null || teamSlots.isEmpty()) {
            return List.of();
        }

        List<LineupSlotDTO> result = new ArrayList<>(teamSlots.size());
        for (Map.Entry<String, String> entry : teamSlots.entrySet()) {
            result.add(new LineupSlotDTO(entry.getValue(), entry.getKey()));
        }
        return result;
    }
}
