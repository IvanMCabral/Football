package com.footballmanager.application.service.career;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de notificaciones observable para eventos de Career.
 * Permite que el frontend se suscriba a cambios en tiempo real.
 */
@Service
@RequiredArgsConstructor
@Slf4j
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

        // V24D13-1: heartbeat cada 15s para mantener la conexion SSE viva.
        // Proxy/ingress cierra conexiones idle a los 30s por default
        // (nginx proxy_read_timeout 60s default pero cloud-native ingress
        // suelen usar 30s). 15s da margen: 1 heartbeat entre cada
        // ventana de 30s. Patron estandar de SSE.
        Flux<CareerEvent> events = sink.asFlux();
        Flux<CareerEvent> heartbeat = Flux.interval(Duration.ofSeconds(15))
            .map(tick -> new CareerEvent(
                CareerEventType.HEARTBEAT,
                Map.of("timestamp", System.currentTimeMillis())
            ));

        return Flux.merge(events, heartbeat)
            .doOnSubscribe(s -> log.info("[SSE] subscribed userId={}", userId))
            .doOnCancel(() -> log.info("[SSE] cancelled userId={}", userId))
            .doOnError(e -> log.error("[SSE] error userId={}", userId, e));
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
        STANDINGS_UPDATED,
        HEARTBEAT  // V24D13-1: keepalive SSE
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
