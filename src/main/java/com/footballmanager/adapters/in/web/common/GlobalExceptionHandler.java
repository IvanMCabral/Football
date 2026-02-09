package com.footballmanager.adapters.in.web.common;

import com.footballmanager.application.exception.NotEnoughPlayersException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones.
 * Ubicado en common/ por ser transversal a todos los controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotEnoughPlayersException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleNotEnoughPlayers(NotEnoughPlayersException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        return Mono.just(
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    // Agregar más handlers según se necesiten
}
