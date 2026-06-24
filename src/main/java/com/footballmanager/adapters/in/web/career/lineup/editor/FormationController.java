package com.footballmanager.adapters.in.web.career.lineup.editor;

import com.footballmanager.adapters.in.web.career.lineup.dto.FormationDTO;
import com.footballmanager.application.service.editor.FormationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Controller para exponer las formaciones tácticas disponibles.
 *
 * <p>Endpoints nuevos del sprint MVP1-lineup-cancha-1.
 * Vive bajo el namespace {@code /api/v1/editor/*}.
 *
 * <p>El modal {@code SquadEditorModalComponent} consume este endpoint al
 * inicializar para saber qué slots del campo marcar como "recommended"
 * según la formación seleccionada por el usuario.
 */
@RestController
@RequestMapping("/api/v1/editor/formations")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class FormationController {

    private final FormationService formationService;

    /**
     * GET /api/v1/editor/formations
     *
     * <p>Retorna las 4 formaciones hardcoded con sus posiciones
     * (4-4-2, 4-3-3, 3-5-2, 4-2-3-1).
     */
    @GetMapping
    public Mono<List<FormationDTO>> getAllFormations() {
        return Mono.just(formationService.getAllFormations());
    }

    /**
     * GET /api/v1/editor/formations/{name}
     *
     * <p>Retorna una formación específica por nombre ({@code 4-4-2}, etc.).
     * 404 si no existe.
     */
    @GetMapping("/{name}")
    public Mono<FormationDTO> getFormationByName(@PathVariable String name) {
        FormationDTO formation = formationService.getFormationByName(name);
        if (formation == null) {
            return Mono.error(new IllegalArgumentException("Unknown formation: " + name));
        }
        return Mono.just(formation);
    }
}