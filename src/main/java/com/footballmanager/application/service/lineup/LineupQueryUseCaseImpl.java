package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.ChemistryBreakdownDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationEffectivenessDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.FormationEffectiveness;
import com.footballmanager.domain.model.valueobject.TeamChemistryCalculator;
import com.footballmanager.domain.port.in.lineup.LineupQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
            // V25D41 (Sprint C6): empty lineup → chemistry = 0 (no lineup, no chemistry).
            // V25D43 (Sprint C8): empty breakdown (4 groups, all empty) for shape stability.
            // V25D47 (Sprint C11a): empty formation effectiveness (default formation,
            // empty per-player map, teamAverage=1.0).
            return new LineupDTO(null, Collections.emptyList(), false, List.of(), List.of(), 0,
                    ChemistryBreakdownDTO.empty(),
                    FormationEffectivenessDTO.empty());
        }

        List<SessionPlayer> lineup = lineupIds.stream()
            .map(id -> career.getSessionPlayers().get(id))
            .filter(Objects::nonNull)
            .toList();

        // MVP1-lineup-cancha-1.6: leer formación persistida con fallback a
        // inferFormation para saves viejos que no tienen teamStarting11Formation.
        String formationCode = readPersistedFormation(career, userTeamId, lineup);

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

        // V25D41 (Sprint C6): compute team chemistry from the actual SessionPlayer
        // objects (we have the lineup List<SessionPlayer> here, not just the DTOs).
        // V25D43 (Sprint C8): calculate() now returns ChemistryDetail (score + breakdown).
        ChemistryDetail chemistryDetail = TeamChemistryCalculator.calculate(lineup);

        // V25D47 (Sprint C11a): formation effectiveness — inferred formation label
        // + per-player effectiveness multipliers (natural position vs slot category).
        // For empty/malformed slots → defaults to "4-4-2" + empty map + 1.0 team avg
        // (backward compat with lineups persisted before subdivisionId mapping).
        Map<String, String> naturalByPlayer = new HashMap<>();
        for (SessionPlayer p : lineup) {
            if (p.getSessionPlayerId() != null && p.getPosition() != null) {
                naturalByPlayer.put(p.getSessionPlayerId(), p.getPosition());
            }
        }
        FormationEffectiveness formationEffectiveness =
                FormationEffectiveness.from(slots, naturalByPlayer);

        return new LineupDTO(formationCode, playerDTOs, true, List.of(), slots,
                chemistryDetail.score(),
                ChemistryBreakdownDTO.from(chemistryDetail),
                FormationEffectivenessDTO.from(formationEffectiveness));
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

    /**
     * MVP1-lineup-cancha-1.6: lee la formación persistida para el team.
     * Si el save es viejo (no tiene teamStarting11Formation, o el team no
     * tiene entry) → fallback a {@code lineupHelper.inferFormation(lineup)}.
     * Esto preserva el comportamiento de 1.5 y anteriores para saves previos
     * sin requerir migración explícita.
     */
    private String readPersistedFormation(CareerSave career, String userTeamId, List<SessionPlayer> lineup) {
        Map<String, String> formationMap = career.getTeamStarting11Formation();
        String persisted = (formationMap != null) ? formationMap.get(userTeamId) : null;
        if (persisted != null && !persisted.isBlank()) {
            return persisted;
        }
        // Backward compat: careerSave sin teamStarting11Formation (saves viejos
        // de sprints 1.5 o anteriores) → inferir del role distribution.
        return lineupHelper.inferFormation(lineup);
    }
}
