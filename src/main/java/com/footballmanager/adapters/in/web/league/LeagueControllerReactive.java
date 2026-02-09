package com.footballmanager.adapters.in.web.league;

import com.footballmanager.domain.model.aggregate.League;
import com.footballmanager.domain.port.in.league.LeagueManagementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Controller reactivo para gestión de ligas
 */
@RestController
@RequestMapping("/api/v1/leagues")
@RequiredArgsConstructor
public class LeagueControllerReactive {

    private final LeagueManagementUseCase leagueManagementUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<League> createLeague(@RequestBody League league, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        UUID userId = UUID.fromString(userIdStr);
        return leagueManagementUseCase.createLeague(userId, league);
    }

    @GetMapping("/{leagueId}")
    public Mono<League> getLeague(@PathVariable UUID leagueId, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        UUID userId = UUID.fromString(userIdStr);
        return leagueManagementUseCase.getLeague(userId, leagueId);
    }

    @GetMapping
    public Flux<League> getAllLeagues(Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        UUID userId = UUID.fromString(userIdStr);
        return leagueManagementUseCase.getAllLeagues(userId);
    }

    @PutMapping("/{leagueId}")
    public Mono<League> updateLeague(@PathVariable UUID leagueId, @RequestBody League league, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        UUID userId = UUID.fromString(userIdStr);
        return leagueManagementUseCase.updateLeague(userId, leagueId, league);
    }

    @DeleteMapping("/{leagueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteLeague(@PathVariable UUID leagueId, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        UUID userId = UUID.fromString(userIdStr);
        return leagueManagementUseCase.deleteLeague(userId, leagueId);
    }

    @PostMapping("/{leagueId}/start")
    public Mono<League> startLeague(@PathVariable UUID leagueId, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        UUID userId = UUID.fromString(userIdStr);
        return leagueManagementUseCase.startLeague(userId, leagueId);
    }

    @PostMapping("/{leagueId}/finish")
    public Mono<League> finishLeague(@PathVariable UUID leagueId, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        UUID userId = UUID.fromString(userIdStr);
        return leagueManagementUseCase.finishLeague(userId, leagueId);
    }

    @GetMapping("/{leagueId}/teams")
    public Flux<com.footballmanager.domain.model.aggregate.Team> getTeamsInLeague(@PathVariable UUID leagueId, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        UUID userId = UUID.fromString(userIdStr);
        return leagueManagementUseCase.getTeamsInLeague(userId, leagueId);
    }

    @PostMapping("/{leagueId}/add-team")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> addTeamToLeague(@PathVariable UUID leagueId, @RequestBody AddTeamRequest request, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        UUID userId = UUID.fromString(userIdStr);
        return leagueManagementUseCase.addTeamToLeague(userId, leagueId, request.getTeamId());
    }

    @DeleteMapping("/{leagueId}/remove-team/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeTeamFromLeague(@PathVariable UUID leagueId, @PathVariable UUID teamId, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        UUID userId = UUID.fromString(userIdStr);
        return leagueManagementUseCase.removeTeamFromLeague(userId, leagueId, teamId);
    }

    // DTO para add-team request
    public static class AddTeamRequest {
        private UUID userId;
        private UUID teamId;

        public UUID getUserId() {
            return userId;
        }

        public void setUserId(UUID userId) {
            this.userId = userId;
        }

        public UUID getTeamId() {
            return teamId;
        }

        public void setTeamId(UUID teamId) {
            this.teamId = teamId;
        }
    }
}
