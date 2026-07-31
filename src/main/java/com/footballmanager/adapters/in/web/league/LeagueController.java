package com.footballmanager.adapters.in.web.league;

import com.footballmanager.application.service.world.WorldLeagueCommandService;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Controller para gestiÃ³n de ligas - VersiÃ³n simplificada.
 * Los endpoints de add-team/remove-team estÃ¡n en LeagueControllerReactive.
 */
@RestController
@RequestMapping("/api/v1/leagues")
public class LeagueController {

    private final WorldLeagueCommandService leagueCommandService;

    public LeagueController(WorldLeagueCommandService leagueCommandService) {
        this.leagueCommandService = leagueCommandService;
    }
}
