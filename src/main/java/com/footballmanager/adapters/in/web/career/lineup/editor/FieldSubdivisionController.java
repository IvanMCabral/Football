package com.footballmanager.adapters.in.web.career.lineup.editor;

import com.footballmanager.adapters.in.web.career.lineup.dto.FieldSubdivisionDTO;
import com.footballmanager.application.service.editor.FieldSubdivisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Controller para exponer las subdivisiones del campo de fútbol.
 *
 * <p>Endpoints nuevos del sprint MVP1-lineup-cancha-1.
 * Vive bajo el namespace {@code /api/v1/editor/*} (junto a custom-player,
 * custom-team, etc.) — ver {@code EditorController} existente.
 *
 * <p>El modal {@code SquadEditorModalComponent} consume este endpoint al
 * inicializar; sin él el modal no podría renderizar los 82 slots.
 */
@RestController
@RequestMapping("/api/v1/editor/subdivisions")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class FieldSubdivisionController {

    private final FieldSubdivisionService fieldSubdivisionService;

    /**
     * GET /api/v1/editor/subdivisions
     *
     * <p>Retorna las 82 subdivisiones del campo (81 normales + 1 GK).
     * El modal las lee al inicializar para renderizar los slots clickeables.
     *
     * <p>No requiere autenticación: los datos son estáticos y públicos
     * (no contienen info del usuario). De todos modos corre bajo la
     * chain de Spring Security como los demás endpoints /api/v1/editor/*.
     */
    @GetMapping
    public Mono<List<FieldSubdivisionDTO>> getAllSubdivisions() {
        return Mono.just(fieldSubdivisionService.getAllSubdivisions());
    }
}