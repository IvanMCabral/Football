package com.footballmanager.application.service.infrastructure;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.exception.TeamAlreadyAssignedException;
import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.domain.ports.out.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

@AutoConfigureWebTestClient
class SingleOwnerTeamAssignmentIntegrationTest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private AuthUseCaseImpl authUseCase;

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @Test
    void firstOwnerCanAssignAndMultipleUnassignedUsersRemainValid() {
        UUID owner = createUser();
        UUID anotherUnassignedUser = createUser();
        UUID team = createTeam(owner);

        authUseCase.assignTeam(owner.toString(), team).block();

        assertEquals(team, teamOf(owner));
        assertNull(teamOf(anotherUnassignedUser));
        assertEquals(1L, ownerCount(team));
    }

    @Test
    void secondOwnerReceivesSanitized409AndCurrentOwnerRemainsUnchanged() {
        UUID firstOwner = createUser();
        UUID secondOwner = createUser();
        UUID team = createTeam(firstOwner);

        assignViaHttp(firstOwner, team).expectStatus().isOk();
        assignViaHttp(secondOwner, team)
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo("TEAM_ALREADY_ASSIGNED")
            .jsonPath("$.status").isEqualTo(409)
            .jsonPath("$.message").value(message -> {
                String body = String.valueOf(message);
                assertFalse(body.contains(firstOwner.toString()));
                assertFalse(body.contains(secondOwner.toString()));
                assertFalse(body.contains(team.toString()));
            });

        assertEquals(team, teamOf(firstOwner));
        assertNull(teamOf(secondOwner));
        assertEquals(1L, ownerCount(team));
    }

    @Test
    void differentTeamsCanHaveDifferentOwners() {
        UUID firstOwner = createUser();
        UUID secondOwner = createUser();
        UUID firstTeam = createTeam(firstOwner);
        UUID secondTeam = createTeam(secondOwner);

        authUseCase.assignTeam(firstOwner.toString(), firstTeam).block();
        authUseCase.assignTeam(secondOwner.toString(), secondTeam).block();

        assertEquals(firstTeam, teamOf(firstOwner));
        assertEquals(secondTeam, teamOf(secondOwner));
    }

    @Test
    void sameUserSameTeamIsIdempotentAndReassignmentRemainsTheExistingPolicy() {
        UUID owner = createUser();
        UUID firstTeam = createTeam(owner);
        UUID secondTeam = createTeam(owner);

        authUseCase.assignTeam(owner.toString(), firstTeam).block();
        authUseCase.assignTeam(owner.toString(), firstTeam).block();
        assertEquals(1L, ownerCount(firstTeam));

        authUseCase.assignTeam(owner.toString(), secondTeam).block();
        assertEquals(secondTeam, teamOf(owner));
        assertEquals(0L, ownerCount(firstTeam));
        assertEquals(1L, ownerCount(secondTeam));
    }

    @Test
    void unrelatedForeignKeyViolationDoesNotBecomeTeamAlreadyAssigned() {
        UUID owner = createUser();

        assignViaHttp(owner, UUID.randomUUID())
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").doesNotExist();
    }

    @Test
    void persistenceConstraintRejectsDuplicateNonNullAndAllowsMultipleNulls() {
        UUID firstOwner = createUser();
        UUID secondOwner = createUser();
        UUID team = createTeam(firstOwner);

        databaseClient.sql("UPDATE users SET team_id = :teamId WHERE id = :userId")
            .bind("teamId", team)
            .bind("userId", firstOwner)
            .fetch().rowsUpdated().block();

        StepVerifier.create(databaseClient.sql("UPDATE users SET team_id = :teamId WHERE id = :userId")
                .bind("teamId", team)
                .bind("userId", secondOwner)
                .fetch().rowsUpdated())
            .expectErrorSatisfies(error -> assertTrue(hasSingleOwnerConstraint(error)))
            .verify();

        assertNull(teamOf(secondOwner));
        assertEquals(1L, ownerCount(team));
    }

    @Test
    void flywayAppliesTheSingleOwnerMigrationToTheIntegrationSchema() {
        Long applied = databaseClient.sql("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '2' AND success = true
                """)
            .map((row, metadata) -> row.get(0, Long.class))
            .one()
            .block();

        assertEquals(1L, applied);
    }

    @Test
    void concurrentAssignmentsUseDatabaseUniquenessAndReturnOneControlledConflict() {
        UUID firstOwner = createUser();
        UUID secondOwner = createUser();
        UUID team = createTeam(firstOwner);
        AuthUseCaseImpl concurrentUseCase = new AuthUseCaseImpl(
            new BarrierUserRepository(userRepository), null, null, null);

        Mono<AssignmentResult> first = concurrentUseCase.assignTeam(firstOwner.toString(), team)
            .thenReturn(AssignmentResult.SUCCESS)
            .onErrorResume(TeamAlreadyAssignedException.class,
                ignored -> Mono.just(AssignmentResult.CONFLICT))
            .subscribeOn(Schedulers.parallel());
        Mono<AssignmentResult> second = concurrentUseCase.assignTeam(secondOwner.toString(), team)
            .thenReturn(AssignmentResult.SUCCESS)
            .onErrorResume(TeamAlreadyAssignedException.class,
                ignored -> Mono.just(AssignmentResult.CONFLICT))
            .subscribeOn(Schedulers.parallel());

        List<AssignmentResult> results = Mono.zip(first, second)
            .map(tuple -> List.of(tuple.getT1(), tuple.getT2()))
            .block(Duration.ofSeconds(20));

        assertEquals(1L, results.stream().filter(AssignmentResult.SUCCESS::equals).count());
        assertEquals(1L, results.stream().filter(AssignmentResult.CONFLICT::equals).count());
        assertEquals(1L, ownerCount(team));
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec assignViaHttp(UUID userId, UUID teamId) {
        return webTestClient.mutateWith(mockUser(userId.toString()))
            .post().uri("/api/v1/auth/assign-team")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"teamId\":\"" + teamId + "\"}")
            .exchange();
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().replace("-", "");
        databaseClient.sql("""
                INSERT INTO users (id, email, username, password_hash, role)
                VALUES (:id, :email, :username, :passwordHash, 'USER')
                """)
            .bind("id", id)
            .bind("email", "owner-" + suffix + "@test.local")
            .bind("username", "owner-" + suffix)
            .bind("passwordHash", "test-password-hash")
            .fetch().rowsUpdated().block();
        return id;
    }

    private UUID createTeam(UUID managerId) {
        UUID id = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO teams (id, manager_id, name, country)
                VALUES (:id, :managerId, :name, 'Test')
                """)
            .bind("id", id)
            .bind("managerId", managerId)
            .bind("name", "Team-" + id)
            .fetch().rowsUpdated().block();
        return id;
    }

    private UUID teamOf(UUID userId) {
        Optional<UUID> teamId = databaseClient.sql("SELECT team_id FROM users WHERE id = :id")
            .bind("id", userId)
            .map((row, metadata) -> Optional.ofNullable(row.get("team_id", UUID.class)))
            .one()
            .block();
        return teamId == null ? null : teamId.orElse(null);
    }

    private long ownerCount(UUID teamId) {
        Long count = databaseClient.sql("SELECT COUNT(*) FROM users WHERE team_id = :teamId")
            .bind("teamId", teamId)
            .map((row, metadata) -> row.get(0, Long.class))
            .one()
            .block();
        return count == null ? 0L : count;
    }

    private static boolean hasSingleOwnerConstraint(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof io.r2dbc.postgresql.api.PostgresqlException postgresqlException
                    && "23505".equals(postgresqlException.getErrorDetails().getCode())
                    && "uk_users_team_id_single_owner".equals(
                        postgresqlException.getErrorDetails().getConstraintName().orElse(null))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private enum AssignmentResult {
        SUCCESS, CONFLICT
    }

    private static final class BarrierUserRepository implements UserRepository {
        private final UserRepository delegate;
        private final CyclicBarrier barrier = new CyclicBarrier(2);

        private BarrierUserRepository(UserRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Mono<User> save(User user) {
            return delegate.save(user);
        }

        @Override
        public Mono<User> createNew(String email, String username, String passwordHash) {
            return delegate.createNew(email, username, passwordHash);
        }

        @Override
        public Mono<User> findById(UUID id) {
            return delegate.findById(id);
        }

        @Override
        public Mono<User> findByEmail(String email) {
            return delegate.findByEmail(email);
        }

        @Override
        public Mono<User> findByUsername(String username) {
            return delegate.findByUsername(username);
        }

        @Override
        public Mono<User> findByTeamId(UUID teamId) {
            return delegate.findByTeamId(teamId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(owner -> awaitBothPreflights()
                    .then(owner.map(Mono::just).orElseGet(Mono::empty)));
        }

        @Override
        public Mono<Boolean> existsByEmail(String email) {
            return delegate.existsByEmail(email);
        }

        @Override
        public Mono<Boolean> existsByUsername(String username) {
            return delegate.existsByUsername(username);
        }

        @Override
        public Mono<Void> deleteById(UUID id) {
            return delegate.deleteById(id);
        }

        private Mono<Void> awaitBothPreflights() {
            return Mono.fromCallable(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
        }
    }
}
