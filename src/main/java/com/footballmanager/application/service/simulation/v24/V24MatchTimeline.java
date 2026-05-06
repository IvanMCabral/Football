package com.footballmanager.application.service.simulation.v24;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mutable ordered list of V24MatchEvent.
 * Events are kept sorted by minute after each add.
 */
public class V24MatchTimeline {

    private final List<V24MatchEvent> events = new ArrayList<>();

    public void addEvent(V24MatchEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        events.add(event);
        events.sort((a, b) -> Integer.compare(a.minute(), b.minute()));
    }

    public List<V24MatchEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public List<V24MatchEvent> eventsByMinute(int minute) {
        return events.stream()
                .filter(e -> e.minute() == minute)
                .collect(Collectors.toList());
    }

    public List<V24MatchEvent> goalEvents() {
        return events.stream()
                .filter(e -> e.type() == V24MatchEventType.GOAL)
                .collect(Collectors.toList());
    }

    public List<V24MatchEvent> shotEvents() {
        return events.stream()
                .filter(e -> e.type().name().startsWith("SHOT")
                        || e.type() == V24MatchEventType.SAVE
                        || e.type() == V24MatchEventType.MISS
                        || e.type() == V24MatchEventType.BLOCK)
                .collect(Collectors.toList());
    }

    public int size() { return events.size(); }
}