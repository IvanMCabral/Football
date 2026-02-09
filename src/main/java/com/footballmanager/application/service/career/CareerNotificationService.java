package com.footballmanager.application.service.career;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de notificaciones observable para eventos de Career.
 * Permite que el frontend se suscriba a cambios en tiempo real.
 */
@Service
@RequiredArgsConstructor
public class CareerNotificationService {

    private final Map<String, Sinks.Many<CareerEvent>> userEventSinks = new ConcurrentHashMap<>();

    /**
     * Emite un evento de actualización de career para un usuario específico.
     */
    public void emitCareerUpdate(String userId, CareerEventType type, Map<String, Object> data) {
        Sinks.Many<CareerEvent> sink = userEventSinks.get(userId);
        if (sink != null) {
            CareerEvent event = new CareerEvent(type, data);
            sink.tryEmitNext(event);
        }
    }

    /**
     * Emite evento de resultados actualizados (para actualizar el fixture/summary).
     */
    public void emitResultsUpdated(String userId, String roundNumber, int matchesUpdated) {
        emitCareerUpdate(userId, CareerEventType.RESULTS_UPDATED, Map.of(
            "round", roundNumber,
            "matchesUpdated", matchesUpdated
        ));
    }

    /**
     * Emite evento de ronda completada.
     */
    public void emitRoundCompleted(String userId, int roundNumber) {
        emitCareerUpdate(userId, CareerEventType.ROUND_COMPLETED, Map.of(
            "round", roundNumber
        ));
    }

    /**
     * Obtiene el Flux de eventos para un usuario específico.
     * El Flux mantiene el estado último y emite actualizaciones.
     */
    public Flux<CareerEvent> getEventsForUser(String userId) {
        Sinks.Many<CareerEvent> sink = userEventSinks.computeIfAbsent(userId,
            k -> {
                return Sinks.many().replay().latest();
            });

        return sink.asFlux()
            .doOnSubscribe(s -> {})
            .doOnCancel(() -> {})
            .doOnError(e -> {});
    }

    /**
     * Limpia los sinks de un usuario (útil cuando el usuario cierra la sesión).
     */
    public void cleanupUser(String userId) {
        Sinks.Many<CareerEvent> sink = userEventSinks.remove(userId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    /**
     * Verifica si un usuario tiene suscripciones activas.
     */
    public boolean hasActiveSubscriptions(String userId) {
        return userEventSinks.containsKey(userId);
    }

    // ========== CLASES DE EVENTO ==========

    public enum CareerEventType {
        RESULTS_UPDATED,
        ROUND_COMPLETED,
        CAREER_UPDATED,
        STANDINGS_UPDATED
    }

    public static class CareerEvent {
        private final CareerEventType type;
        private final Map<String, Object> data;
        private final long timestamp;

        public CareerEvent(CareerEventType type, Map<String, Object> data) {
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public CareerEventType getType() {
            return type;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "CareerEvent{type=" + type + ", timestamp=" + timestamp + "}";
        }
    }
}
