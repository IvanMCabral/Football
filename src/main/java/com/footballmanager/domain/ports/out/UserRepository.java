package com.footballmanager.domain.ports.out;

import com.footballmanager.domain.model.User;
import com.footballmanager.domain.model.UserId;
import reactor.core.publisher.Mono;

public interface UserRepository {
    Mono<Void> save(User user);
    
    Mono<User> findById(UserId id);
    
    Mono<User> findByEmail(String email);
    
    Mono<User> findByUsername(String username);
    
    Mono<Boolean> existsByEmail(String email);
    
    Mono<Boolean> existsByUsername(String username);
    
    Mono<Void> deleteById(UserId id);
}
