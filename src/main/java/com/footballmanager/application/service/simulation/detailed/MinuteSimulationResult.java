package com.footballmanager.application.service.simulation.detailed;

record MinuteSimulationResult(
        int minute,
        int eventsBefore,
        int eventsAfter) {

    int eventsAdded() {
        return eventsAfter - eventsBefore;
    }
}
