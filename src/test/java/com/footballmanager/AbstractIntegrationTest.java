package com.footballmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * V24D7 FASE A — Base class for HTTP/E2E integration tests.
 *
 * <p>Boots the full Spring context against the isolated test environment:
 * <ul>
 *   <li>Postgres database {@code football_manager_test} (separate from dev DB)</li>
 *   <li>Redis logical database 15 (dev backend uses database 0)</li>
 *   <li>RANDOM_PORT to avoid clashes with the dev backend on :8080</li>
 * </ul>
 *
 * <p>Subclasses can inject {@link #webTestClient} and write tests against real
 * HTTP request/response flows. JWT-bypassing slices should use {@code @WebFluxTest}
 * with {@code @MockBean} for the use case instead — see FASE B/C.
 *
 * <p>Test isolation strategy:
 * <ul>
 *   <li>{@link #cleanRedis()} flushes Redis DB 15 between tests (cheap, fast)</li>
 *   <li>The DB is NOT truncated between tests — tests must be idempotent and
 *       must not rely on row counts. The seeded LaLiga data is stable.</li>
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        // Belt-and-suspenders: the application-test.yml also sets these,
        // but pinning them here makes the contract obvious to subclasses.
        "spring.flyway.enabled=false",
        "spring.data.redis.database=15"
    }
)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected DatabaseClient databaseClient;

    @Autowired
    protected ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @BeforeEach
    void cleanRedis() {
        // Wipe test Redis DB so state from a previous test does not leak.
        // Cheap (in-memory flush), avoids test-order coupling.
        reactiveRedisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands()
            .flushDb()
            .block();
    }
}
