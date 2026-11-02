package com.footballmanager.adapters.in.web.dashboard;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.adapters.in.web.dashboard.dto.UserStatsResponse;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.domain.UserStatsService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.port.in.auth.AuthUseCase;
import com.footballmanager.domain.port.in.auth.AuthUserInfo;
import com.footballmanager.domain.port.in.career.GetCareerStatusUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.Optional;
import java.util.UUID;

/** One authenticated request for the visible entry screen. No world hydration or fixture prefetch. */
@RestController
@RequiredArgsConstructor
public class DashboardEntryController {
    private final ControllerHelper helper;
    private final AuthUseCase auth;
    private final GetCareerStatusUseCase status;
    private final CareerSessionService sessions;

    public record Entry(AuthUserInfo user, GetCareerStatusUseCase.CareerStatusDto careerStatus,
                        UserStatsResponse userStats, SquadConditions squadConditions, DashboardCareerOverview careerOverview) {}

    public record SquadConditions(int suspended, int injured, int exhausted, int veryTired) {
        static SquadConditions from(CareerSave career) {
            int suspended = 0, injured = 0, exhausted = 0, tired = 0;
            if (career != null && career.getUserSessionTeamId() != null) {
                for (SessionPlayer player : career.getTeamSquad(career.getUserSessionTeamId())) {
                    boolean banned = Boolean.TRUE.equals(player.getSuspended()) || player.getSuspensionRemainingMatches() > 0;
                    boolean hurt = Boolean.TRUE.equals(player.getInjured());
                    int energy = player.getEnergy() == null ? 100 : player.getEnergy();
                    if (banned) suspended++;
                    if (hurt) injured++;
                    if (!banned && !hurt && energy < 20) exhausted++;
                    if (!banned && !hurt && energy >= 20 && energy <= 39) tired++;
                }
            }
            return new SquadConditions(suspended, injured, exhausted, tired);
        }
    }

    @GetMapping("/api/v1/dashboard/entry")
    public Mono<Entry> entry(Authentication authentication) {
        UUID owner = helper.getUserId(authentication);
        // The session service coalesces these concurrent reads into one career load.
        Mono<GetCareerStatusUseCase.CareerStatusDto> careerStatus = status.execute(owner);
        Mono<Optional<CareerSave>> career = sessions.getCareerFromCache(owner)
                .map(Optional::of).defaultIfEmpty(Optional.empty());
        return Mono.zip(auth.getUserInfo(owner.toString()), careerStatus, career)
                .map(tuple -> {
                    var profile = tuple.getT1();
                    var summary = UserStatsService.summarizeCareer(profile.username(), tuple.getT3().orElse(null));
                    return new Entry(profile, tuple.getT2(), new UserStatsResponse(summary.userName(),
                            summary.matchesPlayed(), summary.wins(), summary.losses(), summary.winPercentage()),
                            SquadConditions.from(tuple.getT3().orElse(null)), DashboardCareerOverview.from(tuple.getT3().orElse(null)));
                });
    }
}
