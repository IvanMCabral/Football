package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.application.service.career.CareerNotificationService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * CareerEventController - SSE Events para Career.
 * Base path: /api/v1/career/events
 *
 * Responsabilidad: Streaming de eventos en tiempo real.
 *
 * Endpoints:
 * - GET /api/v1/career/events → SSE stream de eventos
 */
@RestController
@RequestMapping("/api/v1/career/events")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CareerEventController {

    private final CareerNotificationService notificationService;

    public CareerEventController(CareerNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * GET /api/v1/career/events
     * Endpoint SSE para suscribirse a eventos de career en tiempo real.
     * El frontend puede conectarse aquí para recibir actualizaciones del fixture.
     */
    @GetMapping(value = "", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CareerNotificationService.CareerEvent> streamCareerEvents(Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        return notificationService.getEventsForUser(userId.toString());
    }

    // ========== Helper Methods ==========

    private UUID getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthorized: no user id in authentication");
        }
        return UUID.fromString(authentication.getName());
    }
}
