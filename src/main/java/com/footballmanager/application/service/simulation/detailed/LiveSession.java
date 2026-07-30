package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.FormationSlot;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    }

    public synchronized LiveSnapshot tick() {
        if (finished) {
            return buildSnapshot();
        }
        cachedRandom.rewind();
        int maxMinute = Math.min(ticksRun + 1, 90);
        DetailedMatchResult result = engine.simulate(effectiveContext, cachedRandom, maxMinute);
        this.cachedResult = result;
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

    public boolean isFinished() {
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

    public DetailedMatchResult finalResult() {
        if (cachedResult == null) {
            cachedRandom.rewind();
            this.cachedResult = engine.simulate(effectiveContext, cachedRandom);
            this.homeGoals = cachedResult.homeGoals();
            this.awayGoals = cachedResult.awayGoals();
            this.engineTimeline.clear();
            this.engineTimeline.addAll(cachedResult.timeline().events());
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
        this.homeGoals = result.homeGoals();
        this.awayGoals = result.awayGoals();
        this.engineTimeline.clear();
        this.engineTimeline.addAll(result.timeline().events());
        log.trace("replay complete: homeGoals={} awayGoals={} events={}",
            homeGoals, awayGoals, engineTimeline.size() + manualEvents.size());
    }

    public synchronized void mutateContext(UnaryOperator<MatchContext> mutator) {
        if (mutator == null) {
            throw new IllegalArgumentException("mutator must not be null");
        }
        if (finished) {
            throw new IllegalStateException("Match already finished - cannot mutate");
        }
        MatchContext next = mutator.apply(effectiveContext);
        if (next == null) {
            throw new IllegalArgumentException("mutator must not return null");
        }
        this.effectiveContext = next;
        log.trace("mutateContext applied, triggering replay from currentMinute={}",
            currentMinute);
        if (currentMinute >= 1) {
            replayFromMinute(currentMinute);
        }
    }

    public int currentMinute() {
        return currentMinute;
    }

    public MatchContext context() {
        return effectiveContext;
    }

    public List<DetailedMatchEvent> accumulatedEvents() {
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
                + "|" + nullSafe(event.playerId())
                + "|" + nullSafe(event.relatedPlayerId());
        }
        return eventKey(event);
    }
}
