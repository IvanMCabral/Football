package com.footballmanager.adapters.in.web.career.lineup.editor;

import com.footballmanager.adapters.in.web.career.lineup.dto.FormationDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationPositionDTO;
import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.application.service.editor.FormationPosition;
import com.footballmanager.application.service.editor.FormationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Controller para exponer las formaciones tÃ¡cticas disponibles.
 *
 * <p>Endpoints nuevos del sprint MVP1-lineup-cancha-1.
 * Vive bajo el namespace {@code /api/v1/editor/*}.
 *
 * <p>El modal {@code SquadEditorModalComponent} consume este endpoint al
 * inicializar para saber quÃ© slots del campo marcar como "recommended"
 * segÃºn la formaciÃ³n seleccionada por el usuario.
 */
@RestController
@RequestMapping("/api/v1/editor/formations")
@RequiredArgsConstructor
public class FormationController {

    private final FormationService formationService;

    /**
     * GET /api/v1/editor/formations
     *
     * <p>Retorna las 12 formaciones hardcoded con sus posiciones.
     * 4-2-2-2 y 4-1-2-3.
     */
    @GetMapping
    public Mono<List<FormationDTO>> getAllFormations() {
        return Mono.just(formationService.getAllFormations().stream()
            .map(FormationController::toDto)
            .toList());
    }

    /**
     * GET /api/v1/editor/formations/{name}
     *
     * <p>Retorna una formaciÃ³n especÃ­fica por nombre ({@code 4-4-2}, etc.).
     * 404 si no existe.
     */
    @GetMapping("/{name}")
    public Mono<FormationDTO> getFormationByName(@PathVariable String name) {
        FormationDefinition formation = formationService.getFormationByName(name);
        if (formation == null) {
            return Mono.error(new IllegalArgumentException("Unknown formation: " + name));
        }
        return Mono.just(toDto(formation));
    }

    private static FormationDTO toDto(FormationDefinition formation) {
        return new FormationDTO(
            formation.name(),
            formation.description(),
            formation.defenders(),
            formation.midfielders(),
            formation.attackers(),
            formation.outfieldPlayers(),
            formation.positions().stream().map(FormationController::toDto).toList());
    }

    private static FormationPositionDTO toDto(FormationPosition position) {
        return new FormationPositionDTO(
            position.index(),
            position.role(),
            position.xPercent(),
            position.yPercent(),
            position.actionRangePercent(),
            position.subdivisionId());
    }
}

