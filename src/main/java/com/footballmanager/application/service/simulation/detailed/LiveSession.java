package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.FormationSlot;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class LiveSession {

    private static final Logger log = LoggerFactory.getLogger(LiveSession.class);

    private volatile MatchContext effectiveContext;

    private final long seed;

    private final CachingRandomWrapper cachedRandom;

    private final DoubleCacheIndex cacheIndex;

    private final DetailedMatchEngine engine;

    private volatile DetailedMatchResult cachedResult;

    private final List<DetailedMatchEvent> engineTimeline;

    private final List<DetailedMatchEvent> manualEvents;

    private int homeGoals;
    private int awayGoals;
    private int currentMinute;
    private int ticksRun;
    private boolean finished;
    private boolean cachedResultFinal;

    public LiveSession(MatchContext context, long seed) {
        this.effectiveContext = context;
        this.seed = seed;
        this.cachedRandom = new CachingRandomWrapper(seed);
        this.engine = new DetailedMatchEngine();
        this.cacheIndex = DoubleCacheIndex.buildUniformApproximation(13_500);
        this.engineTimeline = new ArrayList<>();
        this.manualEvents = new ArrayList<>();
        this.homeGoals = 0;
        this.awayGoals = 0;
        this.currentMinute = 0;
        this.ticksRun = 0;
        this.finished = false;
        this.cachedResultFinal = false;
    }

    public synchronized LiveSnapshot tick() {
        if (finished) {
            return buildSnapshot();
        }
        cachedRandom.rewind();
        int maxMinute = Math.min(ticksRun + 1, 90);
        DetailedMatchResult result = engine.simulate(effectiveContext, cachedRandom, maxMinute);
        this.cachedResult = result;
        this.cachedResultFinal = maxMinute >= 90;
        this.homeGoals = result.homeGoals();
        this.awayGoals = result.awayGoals();
        ticksRun++;
        currentMinute = Math.min(ticksRun, 90);
        mergeVisibleEngineTimeline(result.timeline().events(), currentMinute);
        if (currentMinute >= 90) {
            finished = true;
        }

        return buildSnapshot();
    }

    private void mergeVisibleEngineTimeline(List<DetailedMatchEvent> newEvents, int upToMinute) {
        Map<String, DetailedMatchEvent> merged = new LinkedHashMap<>();
        for (DetailedMatchEvent event : this.engineTimeline) {
            if (event.minute() <= upToMinute) {
                merged.put(eventKey(event), event);
            }
        }
        for (DetailedMatchEvent event : newEvents) {
            if (event.minute() <= upToMinute) {
                merged.putIfAbsent(eventKey(event), event);
            }
        }
        this.engineTimeline.clear();
        this.engineTimeline.addAll(merged.values());
        this.engineTimeline.sort(Comparator.comparingInt(DetailedMatchEvent::minute));
    }

    private String eventKey(DetailedMatchEvent event) {
        return event.minute()
            + "|" + event.type()
            + "|" + nullSafe(event.teamId())
            + "|" + nullSafe(event.playerId())
            + "|" + nullSafe(event.relatedPlayerId())
            + "|" + event.description()
            + "|" + event.xg();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public synchronized LiveSnapshot snapshot() {
        return buildSnapshot();
    }

    public synchronized boolean isFinished() {
        return finished;
    }

    public static final int NOISE_EVENT_THRESHOLD_MIN = 5;

    private static final java.util.Set<DetailedMatchEventType> NOISE_EVENTS = java.util.Set.of(
        DetailedMatchEventType.CHANCE_CREATED,
        DetailedMatchEventType.OFFSIDE,
        DetailedMatchEventType.CORNER,
        DetailedMatchEventType.FOUL,
        DetailedMatchEventType.MISS
    );

    static {
        if (NOISE_EVENTS.size() < NOISE_EVENT_THRESHOLD_MIN) {
            throw new IllegalStateException(
                "NOISE_EVENTS Set shrunk below the BUG-009 threshold: "
                    + "expected at least " + NOISE_EVENT_THRESHOLD_MIN
                    + " filtered types, found " + NOISE_EVENTS.size()
                    + ". Review the F5.2 noise filter contract before shipping.");
        }
    }

    private LiveSnapshot buildSnapshot() {
        Map<String, DetailedMatchEvent> eventsByVisibleKey = new LinkedHashMap<>();
        for (DetailedMatchEvent e : engineTimeline) {
            if (e.minute() <= currentMinute) {
                eventsByVisibleKey.put(visibleEventKey(e), e);
            }
        }
        for (DetailedMatchEvent e : manualEvents) {
            if (e.minute() <= currentMinute) {
                eventsByVisibleKey.put(visibleEventKey(e), e);
            }
        }
        List<DetailedMatchEvent> eventsSoFar = new ArrayList<>(eventsByVisibleKey.values());
        int homeGoalsSoFar = 0;
        int awayGoalsSoFar = 0;
        for (DetailedMatchEvent e : eventsSoFar) {
            if (e.type() == DetailedMatchEventType.GOAL) {
                if (e.teamId() != null && e.teamId().equals(effectiveContext.homeTeamId())) {
                    homeGoalsSoFar++;
                } else if (e.teamId() != null && e.teamId().equals(effectiveContext.awayTeamId())) {
                    awayGoalsSoFar++;
                }
            }
        }
        int homePossession = derivePossessionFromEvents(eventsSoFar, true);
        int awayPossession = 100 - homePossession;
        List<DetailedMatchEvent> eventsForSse = new ArrayList<>(eventsSoFar.size());
        for (DetailedMatchEvent e : eventsSoFar) {
            if (!NOISE_EVENTS.contains(e.type())) {
                eventsForSse.add(e);
            }
        }

        return new LiveSnapshot(
                effectiveContext.matchId(),
                currentMinute,
                homeGoalsSoFar,
                awayGoalsSoFar,
                effectiveContext.homeTeamId(),
                effectiveContext.awayTeamId(),
                finished,
                eventsForSse,
                homePossession,
                awayPossession,
                effectiveContext.homeStyle() != null ? effectiveContext.homeStyle().name() : null,
                effectiveContext.awayStyle() != null ? effectiveContext.awayStyle().name() : null,
                effectiveContext.homeFormation(),
                effectiveContext.awayFormation(),
                buildLiveSlotsForSnapshot(
                        startersForSnapshot(true),
                        slotsForSnapshot(true)),
                buildLiveSlotsForSnapshot(
                        startersForSnapshot(false),
                        slotsForSnapshot(false))
        );
    }

    private List<SessionPlayer> startersForSnapshot(boolean home) {
        String teamId = home ? effectiveContext.homeTeamId() : effectiveContext.awayTeamId();
        List<SessionPlayer> starters = new ArrayList<>(
                home ? effectiveContext.homeStartingPlayers() : effectiveContext.awayStartingPlayers());
        List<SessionPlayer> bench = home ? effectiveContext.homeBenchPlayers() : effectiveContext.awayBenchPlayers();

        for (MatchContext.ScheduledSub sub : scheduledSubsForSnapshot()) {
            if (!teamId.equals(sub.teamId()) || sub.effectiveMinute() > currentMinute) {
                continue;
            }
            SessionPlayer playerOn = findPlayerById(playersForSnapshotLookup(home), sub.playerOnId());
            if (playerOn == null) {
                continue;
            }
            for (int i = 0; i < starters.size(); i++) {
                SessionPlayer player = starters.get(i);
                if (player != null && sub.playerOffId().equals(player.getSessionPlayerId())) {
                    starters.set(i, playerOn);
                    break;
                }
            }
        }
        return starters;
    }

    private Map<String, LineupSlot> slotsForSnapshot(boolean home) {
        String teamId = home ? effectiveContext.homeTeamId() : effectiveContext.awayTeamId();
        Map<String, LineupSlot> base = home
                ? effectiveContext.homeSlotsByPlayerId()
                : effectiveContext.awaySlotsByPlayerId();
        Map<String, LineupSlot> slots = new LinkedHashMap<>();
        if (base != null) {
            slots.putAll(base);
        }

        for (MatchContext.ScheduledSub sub : scheduledSubsForSnapshot()) {
            if (!teamId.equals(sub.teamId()) || sub.effectiveMinute() > currentMinute) {
                continue;
            }
            LineupSlot offSlot = slots.remove(sub.playerOffId());
            if (offSlot != null) {
                slots.put(sub.playerOnId(), offSlot);
            }
        }
        return slots;
    }

    private List<MatchContext.ScheduledSub> scheduledSubsForSnapshot() {
        List<MatchContext.ScheduledSub> subs =
                new ArrayList<>(effectiveContext.manualSubstitutions());
        for (DetailedMatchEvent event : manualEvents) {
            if (event.type() != DetailedMatchEventType.SUBSTITUTION
                    || event.teamId() == null
                    || event.playerId() == null
                    || event.relatedPlayerId() == null) {
                continue;
            }
            boolean alreadyPresent = false;
            for (MatchContext.ScheduledSub sub : subs) {
                if (event.teamId().equals(sub.teamId())
                        && event.playerId().equals(sub.playerOffId())
                        && event.relatedPlayerId().equals(sub.playerOnId())
                        && event.minute() == sub.effectiveMinute()) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                subs.add(new MatchContext.ScheduledSub(
                        event.teamId(),
                        event.playerId(),
                        event.relatedPlayerId(),
                        event.minute()
                ));
            }
        }
        return subs;
    }

    private List<SessionPlayer> playersForSnapshotLookup(boolean home) {
        List<SessionPlayer> players = new ArrayList<>();
        List<SessionPlayer> starters = home
                ? effectiveContext.homeStartingPlayers()
                : effectiveContext.awayStartingPlayers();
        List<SessionPlayer> bench = home
                ? effectiveContext.homeBenchPlayers()
                : effectiveContext.awayBenchPlayers();
        if (starters != null) {
            players.addAll(starters);
        }
        if (bench != null) {
            players.addAll(bench);
        }
        return players;
    }

    private SessionPlayer findPlayerById(List<SessionPlayer> players, String sessionPlayerId) {
        if (sessionPlayerId == null || players == null) {
            return null;
        }
        for (SessionPlayer player : players) {
            if (player != null && sessionPlayerId.equals(player.getSessionPlayerId())) {
                return player;
            }
        }
        return null;
    }

    private List<FormationSlot> buildLiveSlotsForSnapshot(
            List<SessionPlayer> starters,
            Map<String, LineupSlot> slotsByPlayerId) {
        if (starters == null || starters.isEmpty()) {
            return List.of();
        }
        List<FormationSlot> slots = new ArrayList<>();
        for (int i = 0; i < starters.size(); i++) {
            SessionPlayer player = starters.get(i);
            if (player == null || player.getSessionPlayerId() == null) {
                continue;
            }
            LineupSlot liveSlot = slotsByPlayerId != null
                    ? slotsByPlayerId.get(player.getSessionPlayerId())
                    : null;
            slots.add(new FormationSlot(
                    player.getSessionPlayerId(),
                    player.getPosition(),
                    resolveLiveSlotIndex(liveSlot, i),
                    liveSlot != null ? liveSlot.customXPercent() : null,
                    liveSlot != null ? liveSlot.customYPercent() : null
            ));
        }
        return slots;
    }

    private Integer resolveLiveSlotIndex(LineupSlot slot, int fallbackIndex) {
        if (slot == null || slot.subdivisionId() == null) {
            return fallbackIndex;
        }
        String id = slot.subdivisionId();
        if (id.startsWith("LIVE-")) {
            try {
                return Integer.parseInt(id.substring("LIVE-".length()));
            } catch (NumberFormatException ignored) {
                return fallbackIndex;
            }
        }
        if ("GK-1".equalsIgnoreCase(id)) {
            return 0;
        }
        return fallbackIndex;
    }

    private int derivePossessionFromEvents(List<DetailedMatchEvent> eventsSoFar, boolean isHome) {
        int homeCount = 0;
        int awayCount = 0;
        for (DetailedMatchEvent e : eventsSoFar) {
            if (e.teamId() == null) {
                continue;
            }
            if (e.type() == DetailedMatchEventType.SUBSTITUTION
                || e.type() == DetailedMatchEventType.TACTICAL_CHANGE) {
                continue;
            }
            if (e.teamId().equals(effectiveContext.homeTeamId())) {
                homeCount++;
            } else if (e.teamId().equals(effectiveContext.awayTeamId())) {
                awayCount++;
            }
        }
        int total = homeCount + awayCount;
        if (total == 0) {
            return 50;
        }
        if (isHome) {
            return (int) Math.round(homeCount * 100.0 / total);
        } else {
            return (int) Math.round(awayCount * 100.0 / total);
        }
    }

    public synchronized DetailedMatchResult finalResult() {
        if (cachedResult == null || !cachedResultFinal) {
            cachedRandom.rewind();
            this.cachedResult = engine.simulate(effectiveContext, cachedRandom);
            this.cachedResultFinal = true;
            this.homeGoals = cachedResult.homeGoals();
            this.awayGoals = cachedResult.awayGoals();
            this.engineTimeline.clear();
            this.engineTimeline.addAll(cachedResult.timeline().events());
            this.currentMinute = 90;
            this.ticksRun = Math.max(ticksRun, 90);
            this.finished = true;
        }
        return cachedResult;
    }

    public synchronized void recordManualSubstitution(DetailedMatchEvent event) {
        if (finished) {
            throw new IllegalStateException("Match already finished");
        }
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (event.type() != DetailedMatchEventType.SUBSTITUTION) {
            throw new IllegalArgumentException(
                "Expected SUBSTITUTION event, got " + event.type());
        }
        final String teamId = event.teamId();
        final String playerOffId = event.playerId();
        final String playerOnId = event.relatedPlayerId();
        final int minute = event.minute();

        mutateContext(ctx -> ctx.withManualSubstitution(
            teamId, playerOffId, playerOnId, minute));
        this.manualEvents.add(event);
        log.trace("Manual substitution recorded + applied: teamId={} off={} on={} minute={}",
            teamId, playerOffId, playerOnId, minute);
    }

    public synchronized void recordTacticalChange(DetailedMatchEvent event) {
        if (finished) {
            throw new IllegalStateException("Match already finished");
        }
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (event.type() != DetailedMatchEventType.TACTICAL_CHANGE) {
            throw new IllegalArgumentException(
                "Expected TACTICAL_CHANGE event, got " + event.type());
        }
        this.manualEvents.add(event);
        log.trace("Tactical change recorded: minute={} teamId={} description='{}'",
            event.minute(), event.teamId(), event.description());
    }

    public synchronized void replayFromMinute(int fromMinute) {
        if (finished) {
            throw new IllegalStateException("Match already finished - cannot replay");
        }
        if (fromMinute < 1 || fromMinute > currentMinute) {
            throw new IllegalArgumentException(
                "fromMinute must be in [1, " + currentMinute + "], got " + fromMinute);
        }
        int index = cacheIndex.indexForMinute(fromMinute);
        cachedRandom.invalidateFromIndex(index);
        log.trace("replayFromMinute({}) invalidated cache index {}, replaying engine",
            fromMinute, index);
        DetailedMatchResult result = engine.simulate(effectiveContext, cachedRandom);
        this.cachedResult = result;
        this.cachedResultFinal = true;
        this.homeGoals = result.homeGoals();
        this.awayGoals = result.awayGoals();
        this.engineTimeline.clear();
        this.engineTimeline.addAll(result.timeline().events());
        log.trace("replay complete: homeGoals={} awayGoals={} events={}",
            homeGoals, awayGoals, engineTimeline.size() + manualEvents.size());
    }

    public synchronized void changeTeamStyle(String teamId, TeamStyle newStyle) {
        mutateContext(ctx -> ctx.withNewStyle(teamId, newStyle));
    }

    public synchronized void changeFormation(
            String teamId,
            String formation,
            Map<String, String> tacticalPositionsByPlayerId,
            Map<String, LineupSlot> slotsByPlayerId) {
        Map<String, String> safePositions = tacticalPositionsByPlayerId != null
                ? Map.copyOf(tacticalPositionsByPlayerId)
                : Map.of();
        Map<String, LineupSlot> safeSlots = slotsByPlayerId != null
                ? Map.copyOf(slotsByPlayerId)
                : Map.of();
        mutateContext(ctx -> {
            MatchContext changed = ctx.withNewFormation(teamId, formation);
            changed = safeSlots.isEmpty() ? changed : changed.withSlots(teamId, safeSlots);
            if (safePositions.isEmpty()) {
                return changed;
            }
            for (SessionPlayer player : playersForTeam(changed, teamId)) {
                String position = safePositions.get(player.getSessionPlayerId());
                if (position != null && !position.equals(player.getPosition())) {
                    player.setPosition(position);
                }
            }
            return changed;
        });
    }

    public synchronized void scheduleManualSubstitution(
            String teamId,
            String playerOffId,
            String playerOnId,
            int minute) {
        mutateContext(ctx -> ctx.withManualSubstitution(
                teamId, playerOffId, playerOnId, minute));
    }

    public synchronized void replayCurrentMinute() {
        if (currentMinute >= 1) {
            replayFromMinute(currentMinute);
        }
    }

    synchronized void mutateContext(UnaryOperator<MatchContext> mutator) {
        if (mutator == null) {
            throw new IllegalArgumentException("mutator must not be null");
        }
        if (finished) {
            throw new IllegalStateException("Match already finished - cannot mutate");
        }
        MatchContext next = mutator.apply(copyContext(effectiveContext));
        if (next == null) {
            throw new IllegalArgumentException("mutator must not return null");
        }
        this.effectiveContext = copyContext(next);
        log.trace("mutateContext applied, triggering replay from currentMinute={}",
            currentMinute);
        if (currentMinute >= 1) {
            replayFromMinute(currentMinute);
        }
    }

    public synchronized int currentMinute() {
        return currentMinute;
    }

    synchronized MatchContext context() {
        return copyContext(effectiveContext);
    }

    public synchronized LiveSessionContextView contextView() {
        return contextView(effectiveContext);
    }

    public synchronized List<DetailedMatchEvent> accumulatedEvents() {
        java.util.Set<String> manualVisibleKeys = new java.util.LinkedHashSet<>();
        for (DetailedMatchEvent event : manualEvents) {
            manualVisibleKeys.add(visibleEventKey(event));
        }

        List<DetailedMatchEvent> combined = new ArrayList<>(engineTimeline.size() + manualEvents.size());
        for (DetailedMatchEvent event : engineTimeline) {
            if (!manualVisibleKeys.contains(visibleEventKey(event))) {
                combined.add(event);
            }
        }
        for (DetailedMatchEvent event : manualEvents) {
            if (combined.stream().noneMatch(existing ->
                    visibleEventKey(existing).equals(visibleEventKey(event)))) {
                combined.add(event);
            }
        }
        return java.util.Collections.unmodifiableList(combined);
    }

    private String visibleEventKey(DetailedMatchEvent event) {
        if (event.type() == DetailedMatchEventType.SUBSTITUTION) {
            return event.minute()
                + "|" + event.type()
                + "|" + visibleSubstitutionTeamKey(event)
                + "|" + nullSafe(event.playerId())
                + "|" + nullSafe(event.relatedPlayerId());
        }
        return eventKey(event);
    }

    private String visibleSubstitutionTeamKey(DetailedMatchEvent event) {
        if (isContextTeamId(event.teamId())) {
            return event.teamId();
        }
        String inferred = inferTeamIdFromPlayerId(event.playerId());
        return inferred != null ? inferred : nullSafe(event.teamId());
    }

    private boolean isContextTeamId(String teamId) {
        return teamId != null
            && (teamId.equals(effectiveContext.homeTeamId()) || teamId.equals(effectiveContext.awayTeamId()));
    }

    private String inferTeamIdFromPlayerId(String playerId) {
        boolean home = containsPlayer(effectiveContext.homeStartingPlayers(), playerId)
            || containsPlayer(effectiveContext.homeBenchPlayers(), playerId);
        boolean away = containsPlayer(effectiveContext.awayStartingPlayers(), playerId)
            || containsPlayer(effectiveContext.awayBenchPlayers(), playerId);
        if (home == away) {
            return null;
        }
        return home ? effectiveContext.homeTeamId() : effectiveContext.awayTeamId();
    }

    private boolean containsPlayer(List<SessionPlayer> players, String playerId) {
        if (playerId == null || players == null) {
            return false;
        }
        for (SessionPlayer player : players) {
            if (player != null && playerId.equals(player.getSessionPlayerId())) {
                return true;
            }
        }
        return false;
    }

    private List<SessionPlayer> playersForTeam(MatchContext context, String teamId) {
        if (context.homeTeamId().equals(teamId)) {
            List<SessionPlayer> players = new ArrayList<>(
                    context.homeStartingPlayers().size() + context.homeBenchPlayers().size());
            players.addAll(context.homeStartingPlayers());
            players.addAll(context.homeBenchPlayers());
            return players;
        }
        if (context.awayTeamId().equals(teamId)) {
            List<SessionPlayer> players = new ArrayList<>(
                    context.awayStartingPlayers().size() + context.awayBenchPlayers().size());
            players.addAll(context.awayStartingPlayers());
            players.addAll(context.awayBenchPlayers());
            return players;
        }
        throw new IllegalArgumentException(
                "teamId " + teamId + " does not match home (" + context.homeTeamId()
                        + ") or away (" + context.awayTeamId() + ") of this match");
    }

    private LiveSessionContextView contextView(MatchContext context) {
        return new LiveSessionContextView(
                context.matchId(),
                teamView(context.homeTeam(), context.homeFormation(), context.homeStyle(), context.homeSlotsByPlayerId()),
                teamView(context.awayTeam(), context.awayFormation(), context.awayStyle(), context.awaySlotsByPlayerId()),
                playerViews(context.homeStartingPlayers()),
                playerViews(context.awayStartingPlayers()),
                playerViews(context.homeBenchPlayers()),
                playerViews(context.awayBenchPlayers()),
                substitutionViews(context.manualSubstitutions()));
    }

    private LiveSessionContextView.TeamContextView teamView(
            SessionTeam team,
            String formation,
            TeamStyle style,
            Map<String, LineupSlot> slotsByPlayerId) {
        return new LiveSessionContextView.TeamContextView(
                team.getSessionTeamId(),
                team.getBaseTeamId(),
                team.getWorldTeamId(),
                team.getName(),
                team.getCountry(),
                team.getBudget(),
                formation != null ? formation : team.getFormation(),
                style != null ? style : team.getStyle(),
                team.getManagerName(),
                team.getMorale(),
                team.getReputation(),
                slotsByPlayerId != null ? slotsByPlayerId : Map.of());
    }

    private List<LiveSessionContextView.PlayerContextView> playerViews(List<SessionPlayer> players) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }
        List<LiveSessionContextView.PlayerContextView> views = new ArrayList<>(players.size());
        for (SessionPlayer player : players) {
            views.add(playerView(player));
        }
        return List.copyOf(views);
    }

    private LiveSessionContextView.PlayerContextView playerView(SessionPlayer player) {
        return new LiveSessionContextView.PlayerContextView(
                player.getSessionPlayerId(),
                player.getBasePlayerId(),
                player.getWorldPlayerId(),
                player.getName(),
                player.getAge(),
                player.getPosition(),
                player.getAttack(),
                player.getDefense(),
                player.getTechnique(),
                player.getSpeed(),
                player.getStamina(),
                player.getMentality(),
                player.getMarketValue(),
                player.getEnergy(),
                player.getForm(),
                player.getInjured(),
                player.getInjuryType(),
                player.getInjuryRemainingMatches(),
                player.getMatchesPlayedInRow(),
                player.getYellowCards(),
                player.getRedCards(),
                player.getSuspended(),
                player.getSuspensionRemainingMatches(),
                player.getHeightCm(),
                player.getSkillLevels(),
                player.getSpecialTraits());
    }

    private List<LiveSessionContextView.ScheduledSubstitutionView> substitutionViews(
            List<MatchContext.ScheduledSub> substitutions) {
        if (substitutions == null || substitutions.isEmpty()) {
            return List.of();
        }
        List<LiveSessionContextView.ScheduledSubstitutionView> views = new ArrayList<>(substitutions.size());
        for (MatchContext.ScheduledSub sub : substitutions) {
            views.add(new LiveSessionContextView.ScheduledSubstitutionView(
                    sub.teamId(),
                    sub.playerOffId(),
                    sub.playerOnId(),
                    sub.effectiveMinute()));
        }
        return List.copyOf(views);
    }

    private MatchContext copyContext(MatchContext context) {
        return new MatchContext(
                context.matchId(),
                context.homeTeamId(),
                context.awayTeamId(),
                copyTeam(context.homeTeam()),
                copyTeam(context.awayTeam()),
                copyPlayers(context.homeStartingPlayers()),
                copyPlayers(context.awayStartingPlayers()),
                copyPlayers(context.homeBenchPlayers()),
                copyPlayers(context.awayBenchPlayers()),
                context.homeFormation(),
                context.awayFormation(),
                context.homeStyle(),
                context.awayStyle(),
                context.manualSubstitutions(),
                context.homeSlotsByPlayerId(),
                context.awaySlotsByPlayerId());
    }

    private SessionTeam copyTeam(SessionTeam team) {
        SessionTeam copy = SessionTeam.fromRealTeam(
                team.getBaseTeamId(),
                team.getWorldTeamId(),
                team.getName(),
                team.getCountry(),
                team.getBudget(),
                team.getFormation(),
                team.getManagerName());
        copy.setSessionTeamId(team.getSessionTeamId());
        copy.setStyle(team.getStyle());
        copy.setMorale(team.getMorale());
        copy.setReputation(team.getReputation());
        copy.setOrigin(team.getOrigin());
        copy.setCreatedAt(team.getCreatedAt());
        copy.setLastUpdated(team.getLastUpdated());
        return copy;
    }

    private List<SessionPlayer> copyPlayers(List<SessionPlayer> players) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }
        List<SessionPlayer> copy = new ArrayList<>(players.size());
        for (SessionPlayer player : players) {
            copy.add(copyPlayer(player));
        }
        return List.copyOf(copy);
    }

    private SessionPlayer copyPlayer(SessionPlayer player) {
        SessionPlayer copy = SessionPlayer.custom(
                player.getName(),
                player.getAge(),
                player.getPosition(),
                player.getAttack(),
                player.getDefense(),
                player.getTechnique(),
                player.getSpeed(),
                player.getStamina(),
                player.getMentality(),
                player.getMarketValue());
        copy.setSessionPlayerId(player.getSessionPlayerId());
        copy.setBasePlayerId(player.getBasePlayerId());
        copy.setWorldPlayerId(player.getWorldPlayerId());
        copy.setEnergy(player.getEnergy());
        copy.setForm(player.getForm());
        copy.setInjured(player.getInjured());
        copy.setInjuryType(player.getInjuryType());
        copy.setInjuryRemainingMatches(player.getInjuryRemainingMatches());
        copy.setMatchesPlayedInRow(player.getMatchesPlayedInRow());
        copy.setYellowCards(player.getYellowCards());
        copy.setRedCards(player.getRedCards());
        copy.setSuspended(player.getSuspended());
        copy.setSuspensionRemainingMatches(player.getSuspensionRemainingMatches());
        copy.setOrigin(player.getOrigin());
        copy.setHeightCm(player.getHeightCm());
        for (Map.Entry<com.footballmanager.domain.model.valueobject.PlayerSkill, Integer> entry
                : player.getSkillLevels().entrySet()) {
            copy.setSkillLevel(entry.getKey(), entry.getValue());
        }
        copy.setSpecialTraits(player.getSpecialTraits());
        return copy;
    }
}
