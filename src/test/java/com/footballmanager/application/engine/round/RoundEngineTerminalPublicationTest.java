package com.footballmanager.application.engine.round;

import com.footballmanager.application.engine.match.MatchEngine;
import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoundEngineTerminalPublicationTest {

    @Test
    void rawPublicStream_emitsOneCanonicalTerminalWhenHistoricalPathsBothTrigger() {
        RoundEngine engine = completedRound(1, 1);
        List<RoundState> rawEvents = new CopyOnWriteArrayList<>();
        engine.getStateStream().subscribe(rawEvents::add);

        // Historical path A: the final-match callback requested completion.
        engine.emitCompletedState();
        // Historical path B: the scheduler then observed every match finished.
        executeTick(engine);

        assertCanonicalSingleTerminal(rawEvents, 1, 1);
    }

    @Test
    void twoSubscribersReceiveOneDeliveryEachWhilePublisherEmitsOnce() {
        RoundEngine engine = completedRound(3, 1);
        List<RoundState> subscriberA = new CopyOnWriteArrayList<>();
        List<RoundState> subscriberB = new CopyOnWriteArrayList<>();
        engine.getStateStream().subscribe(subscriberA::add);
        engine.getStateStream().subscribe(subscriberB::add);

        engine.emitCompletedState();
        executeTick(engine);

        assertCanonicalSingleTerminal(subscriberA, 3, 1);
        assertCanonicalSingleTerminal(subscriberB, 3, 1);
    }

    @Test
    void lateSubscriberReplaysOneRetainedTerminalWithoutCreatingANewPublication() {
        RoundEngine engine = completedRound(0, 0);
        List<RoundState> connected = new CopyOnWriteArrayList<>();
        engine.getStateStream().subscribe(connected::add);
        engine.emitCompletedState();

        List<RoundState> lateSubscriber = new CopyOnWriteArrayList<>();
        engine.getStateStream().subscribe(lateSubscriber::add);

        assertCanonicalSingleTerminal(connected, 0, 0);
        assertCanonicalSingleTerminal(lateSubscriber, 0, 0);
    }

    @Test
    void repeatedCompletionSignalsAndPostCompletionTickDoNotEmitAgain() {
        RoundEngine engine = completedRound(2, 0);
        List<RoundState> rawEvents = new CopyOnWriteArrayList<>();
        engine.getStateStream().subscribe(rawEvents::add);

        engine.emitCompletedState();
        engine.emitCompletedState();
        executeTick(engine);
        executeTick(engine);

        assertCanonicalSingleTerminal(rawEvents, 2, 0);
    }

    @Test
    void concurrentCompletionSignalsPublishExactlyOnce() throws InterruptedException {
        RoundEngine engine = completedRound(1, 1);
        List<RoundState> rawEvents = new CopyOnWriteArrayList<>();
        engine.getStateStream().subscribe(rawEvents::add);
        CountDownLatch startGate = new CountDownLatch(1);
        Thread first = new Thread(() -> awaitAndEmit(startGate, engine), "round-completion-first");
        Thread second = new Thread(() -> awaitAndEmit(startGate, engine), "round-completion-second");
        first.start();
        second.start();
        startGate.countDown();
        first.join();
        second.join();

        assertCanonicalSingleTerminal(rawEvents, 1, 1);
    }

    private static RoundEngine completedRound(int homeGoals, int awayGoals) {
        UUID roundId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        RoundEngine engine = new RoundEngine(roundId);
        MatchEngine matchEngine = mock(MatchEngine.class);
        MatchStateSnapshot terminalMatch = new MatchStateSnapshot(
                matchId, UUID.randomUUID(), UUID.randomUUID(), 90, MatchStatus.FINISHED,
                new Score(homeGoals, awayGoals), List.of(), "test-career", "test-user");
        when(matchEngine.isFinished()).thenReturn(true);
        when(matchEngine.getCurrentState()).thenReturn(terminalMatch);
        engine.registerMatch(matchId, matchEngine);
        setField(engine, "isRunning", true);
        return engine;
    }

    private static void assertCanonicalSingleTerminal(List<RoundState> rawEvents, int homeGoals, int awayGoals) {
        List<RoundState> terminals = rawEvents.stream()
                .filter(state -> state.getStatus() == RoundState.RoundStatus.COMPLETED)
                .toList();
        assertEquals(1, terminals.size(), "raw public terminal events must not be deduplicated by the subscriber");
        assertEquals(homeGoals, terminals.getFirst().getMatches().getFirst().score().home());
        assertEquals(awayGoals, terminals.getFirst().getMatches().getFirst().score().away());
    }

    private static void executeTick(RoundEngine engine) {
        try {
            Method method = RoundEngine.class.getDeclaredMethod("executeTick");
            method.setAccessible(true);
            method.invoke(engine);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not invoke the RoundEngine scheduler path", exception);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not prepare isolated round lifecycle", exception);
        }
    }

    private static void awaitAndEmit(CountDownLatch startGate, RoundEngine engine) {
        try {
            startGate.await();
            engine.emitCompletedState();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("completion test thread interrupted", exception);
        }
    }
}
