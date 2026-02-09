package com.footballmanager.adapters.in.web.world;

import com.footballmanager.application.service.world.WorldSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for WorldSnapshot operations.
 * Handles only snapshot-level operations (regenerate, etc.)
 */
@RestController
@RequestMapping("/api/v1/world")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class WorldCommandController {

    private final WorldSnapshotService worldSnapshotService;

    /**
     * DELETE /api/v1/world/snapshot?userId={userId}
     * Regenerates the WorldSnapshot from PostgreSQL, overwriting the existing one.
     */
    @DeleteMapping("/snapshot")
    public Mono<ResponseEntity<Map<String, String>>> deleteSnapshot(@RequestParam UUID userId) {
        return worldSnapshotService.reloadFromDatabase(userId)
                .map(snapshot -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("status", "regenerated");
                    response.put("leagues", String.valueOf(snapshot.getLeagues().size()));
                    response.put("teams", String.valueOf(snapshot.getWorldTeams().size()));
                    response.put("players", String.valueOf(snapshot.getWorldPlayers().size()));
                    return ResponseEntity.ok(response);
                });
    }
}
