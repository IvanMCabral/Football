package com.footballmanager.application.service.simulation.detailed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mutable ordered list of DetailedMatchEvent.
 * Events are kept sorted by minute after each add.
 */
public class MatchTimeline {

    private final List<DetailedMatchEvent> events = new ArrayList<>();

    public void addEvent(DetailedMatchEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        events.add(event);
        events.sort((a, b) -> Integer.compare(a.minute(), b.minute()));
    }

    public List<DetailedMatchEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public List<DetailedMatchEvent> eventsByMinute(int minute) {
        return events.stream()
                .filter(e -> e.minute() == minute)
                .collect(Collectors.toList());
    }

    public List<DetailedMatchEvent> goalEvents() {
        return events.stream()
                .filter(e -> e.type() == DetailedMatchEventType.GOAL)
                .collect(Collectors.toList());
    }

    public List<DetailedMatchEvent> shotEvents() {
        return events.stream()
                .filter(e -> e.type().name().startsWith("SHOT")
                        || e.type() == DetailedMatchEventType.SAVE
                        || e.type() == DetailedMatchEventType.MISS
                        || e.type() == DetailedMatchEventType.BLOCK)
                .collect(Collectors.toList());
    }

    public int size() { return events.size(); }
}
