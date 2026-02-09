package com.footballmanager.domain.ports.out.user;

import com.footballmanager.domain.model.aggregate.User;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserRepository {
    Mono<User> save(User user);
    
    Mono<User> createNew(String email, String username, String passwordHash);
    
    Mono<User> findById(UUID id);
    
    Mono<User> findByEmail(String email);
    
    Mono<User> findByUsername(String username);
    
    Mono<Boolean> existsByEmail(String email);
    
    Mono<Boolean> existsByUsername(String username);
    
    Mono<Void> deleteById(UUID id);
}

