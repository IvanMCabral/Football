package com.footballmanager.adapters.in.web.career.lineup.dto;

import java.util.List;

/**
 * V25D45 (Sprint C10): Request body para {@code POST /career/lineup/preview-chemistry}.
 *
 * <p>El body es minimal — solo el set hipotético de 11 playerIds que el manager
 * está editando. El back calcula el chemistry SIN persistir (read-only preview,
 * útil mientras el manager drag-and-drop players antes de confirmar).
 *
 * <p>Validaciones:
 * <ul>
 *   <li>{@code playerIds} no nulo, exactamente 11 elementos.</li>
 *   <li>Cada playerId debe existir en el career del user (404 si alguno falla,
 *       evaluated server-side en {@code LineupController#previewChemistry}).</li>
 * </ul>
 *
 * <p>No incluimos {@code formation} ni {@code slots} en este DTO porque el
 * chemistry es ortogonal a esos — solo depende de los 11 SessionPlayers.
 * Si en el futuro el preview quiere considerar formation (e.g., bonus por
 * role-match), se agrega.
 */
public record PreviewChemistryRequest(
    List<String> playerIds
) {

    public PreviewChemistryRequest {
        if (playerIds == null) {
            throw new IllegalArgumentException("playerIds is required");
        }
        if (playerIds.size() != 11) {
            throw new IllegalArgumentException(
                "playerIds must contain exactly 11 elements, got " + playerIds.size());
        }
    }
}