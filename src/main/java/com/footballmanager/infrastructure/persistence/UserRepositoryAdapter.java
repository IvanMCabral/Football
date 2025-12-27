package com.footballmanager.infrastructure.persistence;

import com.footballmanager.domain.model.User;
import com.footballmanager.domain.model.UserId;
import com.footballmanager.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {
    private final UserR2dbcRepository r2dbcRepository;

    @Override
    public Mono<Void> save(User user) {
        return r2dbcRepository.save(UserEntity.fromDomain(user)).then();
    }

    @Override
    public Mono<User> findById(UserId id) {
        return r2dbcRepository.findById(id.getValue())
                .map(UserEntity::toDomain);
    }

    @Override
    public Mono<User> findByEmail(String email) {
        return r2dbcRepository.findByEmail(email)
                .map(UserEntity::toDomain);
    }

    @Override
    public Mono<User> findByUsername(String username) {
        return r2dbcRepository.findByUsername(username)
                .map(UserEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return r2dbcRepository.findByEmail(email).hasElement();
    }

    @Override
    public Mono<Boolean> existsByUsername(String username) {
        return r2dbcRepository.findByUsername(username).hasElement();
    }

    @Override
    public Mono<Void> deleteById(UserId id) {
        return r2dbcRepository.deleteById(id.getValue());
    }
}
