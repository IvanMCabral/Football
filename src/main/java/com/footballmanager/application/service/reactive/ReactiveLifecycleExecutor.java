package com.footballmanager.application.service.reactive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Executes reactive work that is triggered by engine lifecycle callbacks rather
 * than by an HTTP request publisher.
 */
@Component
@Slf4j
public class ReactiveLifecycleExecutor {

    public void execute(String operationName, Mono<Void> work) {
        if (work == null) {
            return;
        }
        work.doOnError(error -> log.warn("{} failed: {}", operationName, error.getMessage(), error))
            .onErrorResume(error -> Mono.empty())
            .subscribe();
    }
}
