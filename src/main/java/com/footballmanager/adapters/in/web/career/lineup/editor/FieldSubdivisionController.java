package com.footballmanager.adapters.in.web.career.lineup.editor;

import com.footballmanager.adapters.in.web.career.lineup.dto.FieldSubdivisionDTO;
import com.footballmanager.application.service.editor.FieldSubdivision;
import com.footballmanager.application.service.editor.FieldSubdivisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Controller para exponer las subdivisiones del campo de fÃƒÆ’Ã‚Âºtbol.
 *
 * <p>Endpoints nuevos del sprint MVP1-lineup-cancha-1.
 * Vive bajo el namespace {@code /api/v1/lineup-editor/*} (junto a custom-player,
 * custom-team, etc.) ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â ver {@code EditorController} existente.
 *
 * <p>El modal {@code SquadEditorModalComponent} consume este endpoint al
 * inicializar; sin ÃƒÆ’Ã‚Â©l el modal no podrÃƒÆ’Ã‚Â­a renderizar los 82 slots.
 */
@RestController
@RequestMapping("/api/v1/lineup-editor/subdivisions")
@RequiredArgsConstructor
public class FieldSubdivisionController {

    private final FieldSubdivisionService fieldSubdivisionService;

    /**
     * GET /api/v1/lineup-editor/subdivisions
     *
     * <p>Retorna las 82 subdivisiones del campo (81 normales + 1 GK).
     * El modal las lee al inicializar para renderizar los slots clickeables.
     *
     * <p>No requiere autenticaciÃƒÆ’Ã‚Â³n: los datos son estÃƒÆ’Ã‚Â¡ticos y pÃƒÆ’Ã‚Âºblicos
     * (no contienen info del usuario). De todos modos corre bajo la
     * chain de Spring Security como los demÃƒÆ’Ã‚Â¡s endpoints /api/v1/lineup-editor/*.
     */
    @GetMapping
    public Mono<List<FieldSubdivisionDTO>> getAllSubdivisions() {
        return Mono.just(fieldSubdivisionService.getAllSubdivisions().stream()
            .map(FieldSubdivisionController::toDto)
            .toList());
    }

    private static FieldSubdivisionDTO toDto(FieldSubdivision subdivision) {
        return new FieldSubdivisionDTO(
            subdivision.sector(),
            subdivision.subIndex(),
            subdivision.isGoalkeeper(),
            subdivision.left(),
            subdivision.top(),
            subdivision.width(),
            subdivision.height(),
            subdivision.subdivisionId(),
            subdivision.zone());
    }
}
