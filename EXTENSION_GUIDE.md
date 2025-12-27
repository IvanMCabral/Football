# Extension Guide - Adding Features to Football Manager

This guide demonstrates how to add new features following Hexagonal Architecture principles and the project's established patterns.

## Step-by-Step: Adding a New Feature

Let's walk through adding a **Training System** feature as an example.

### Phase 1: Domain Design

#### 1.1 Create Domain Models

**File: `domain/model/TrainingId.java`**
```java
public class TrainingId {
    private final UUID value;

    private TrainingId(UUID value) {
        Objects.requireNonNull(value, "TrainingId cannot be null");
        this.value = value;
    }

    public static TrainingId generate() {
        return new TrainingId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }
    // equals, hashCode, toString
}
```

**File: `domain/model/Training.java`**
```java
public class Training {
    public enum TrainingType {
        ATTACKING,    // +Attack, +Technique
        DEFENDING,    // +Defense
        STAMINA,      // +Stamina
        SPEED,        // +Speed
        MENTAL        // +Mentality
    }

    private final TrainingId id;
    private final PlayerId playerId;
    private final TeamId teamId;
    private final TrainingType type;
    private final int intensity;    // 1-10
    private final int durationMinutes;
    private final Instant completedAt;
    private final Instant createdAt;

    private Training(TrainingId id, PlayerId playerId, TeamId teamId,
                     TrainingType type, int intensity, int durationMinutes,
                     Instant completedAt, Instant createdAt) {
        // Validation and initialization
    }

    public static Training create(PlayerId playerId, TeamId teamId,
                                 TrainingType type, int intensity) {
        validateIntensity(intensity);
        return new Training(TrainingId.generate(), playerId, teamId, type, intensity,
                          60, Instant.now(), Instant.now());
    }

    public TrainingResult complete() {
        // Calculate attribute improvements based on type and intensity
        int improvementPoints = calculateImprovement();
        int fatigueIncrease = calculateFatigue();

        return TrainingResult.of(improvementPoints, fatigueIncrease);
    }

    private int calculateImprovement() {
        return intensity / 2 + 1;  // 1-6 points based on intensity
    }

    private int calculateFatigue() {
        return intensity * 2;  // 2-20 fatigue points
    }

    // Getters...
}
```

**File: `domain/model/TrainingResult.java`**
```java
public class TrainingResult {
    private final int improvementPoints;
    private final int fatigueIncrease;
    private final Instant completedAt;

    // Immutable value object
    public static TrainingResult of(int improvementPoints, int fatigueIncrease) {
        return new TrainingResult(improvementPoints, fatigueIncrease, Instant.now());
    }

    // Getters...
}
```

### Phase 2: Define Ports

#### 2.1 Output Port (Repository)

**File: `domain/ports/out/TrainingRepository.java`**
```java
public interface TrainingRepository {
    Mono<Void> save(Training training);
    Mono<Training> findById(TrainingId id);
    Flux<Training> findByPlayerId(PlayerId playerId);
    Flux<Training> findByTeamId(TeamId teamId);
    Mono<Void> delete(TrainingId id);
}
```

### Phase 3: Application Services

#### 3.1 Create Service

**File: `application/service/TrainingService.java`**
```java
@Service
@RequiredArgsConstructor
public class TrainingService {
    private final TrainingRepository trainingRepository;
    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    public Mono<TrainingResult> conductTraining(PlayerId playerId, TeamId teamId,
                                                Training.TrainingType type, int intensity) {
        return playerRepository.findById(playerId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Player not found")))
                .flatMap(player -> {
                    Training training = Training.create(playerId, teamId, type, intensity);
                    return trainingRepository.save(training)
                            .then(Mono.just(training.complete()));
                })
                .flatMap(result -> applyTrainingEffects(playerId, result))
                .onErrorResume(e -> Mono.error(new BusinessException(e.getMessage())));
    }

    private Mono<TrainingResult> applyTrainingEffects(PlayerId playerId, TrainingResult result) {
        return playerRepository.findById(playerId)
                .flatMap(player -> {
                    player.updateEnergy(-result.getFatigueIncrease());
                    return playerRepository.save(player)
                            .then(Mono.just(result));
                });
    }

    public Flux<Training> getPlayerTrainings(PlayerId playerId) {
        return trainingRepository.findByPlayerId(playerId);
    }
}
```

### Phase 4: Create DTOs

**File: `application/dto/TrainingRequest.java`**
```java
public record TrainingRequest(
    String playerId,
    String teamId,
    String trainingType,  // ATTACKING, DEFENDING, etc.
    int intensity         // 1-10
) {}
```

**File: `application/dto/TrainingResponseDTO.java`**
```java
public record TrainingResponseDTO(
    String id,
    String playerId,
    String teamId,
    String trainingType,
    int intensity,
    int improvementPoints,
    int fatigueIncrease,
    Instant completedAt
) {}
```

### Phase 5: Persistence Layer

#### 5.1 Create Entity

**File: `infrastructure/persistence/TrainingEntity.java`**
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("trainings")
public class TrainingEntity {
    @Id
    private UUID id;
    private UUID playerId;
    private UUID teamId;
    private String trainingType;
    private int intensity;
    private int durationMinutes;
    private Instant completedAt;
    private Instant createdAt;

    public static TrainingEntity fromDomain(Training training) {
        return new TrainingEntity(
                training.getId().getValue(),
                training.getPlayerId().getValue(),
                training.getTeamId().getValue(),
                training.getType().name(),
                training.getIntensity(),
                training.getDurationMinutes(),
                training.getCompletedAt(),
                training.getCreatedAt()
        );
    }

    public Training toDomain() {
        return Training.reconstruct(
                TrainingId.of(id),
                PlayerId.of(playerId),
                TeamId.of(teamId),
                Training.TrainingType.valueOf(trainingType),
                intensity,
                durationMinutes,
                completedAt,
                createdAt
        );
    }
}
```

#### 5.2 Create R2DBC Repository

**File: `infrastructure/persistence/TrainingR2dbcRepository.java`**
```java
@Repository
public interface TrainingR2dbcRepository extends R2dbcRepository<TrainingEntity, UUID> {
    Flux<TrainingEntity> findByPlayerId(UUID playerId);
    Flux<TrainingEntity> findByTeamId(UUID teamId);
}
```

#### 5.3 Create Repository Adapter

**File: `infrastructure/persistence/TrainingRepositoryAdapter.java`**
```java
@Component
@RequiredArgsConstructor
public class TrainingRepositoryAdapter implements TrainingRepository {
    private final TrainingR2dbcRepository r2dbcRepository;

    @Override
    public Mono<Void> save(Training training) {
        return r2dbcRepository.save(TrainingEntity.fromDomain(training)).then();
    }

    @Override
    public Mono<Training> findById(TrainingId id) {
        return r2dbcRepository.findById(id.getValue())
                .map(TrainingEntity::toDomain);
    }

    @Override
    public Flux<Training> findByPlayerId(PlayerId playerId) {
        return r2dbcRepository.findByPlayerId(playerId.getValue())
                .map(TrainingEntity::toDomain);
    }

    @Override
    public Flux<Training> findByTeamId(TeamId teamId) {
        return r2dbcRepository.findByTeamId(teamId.getValue())
                .map(TrainingEntity::toDomain);
    }

    @Override
    public Mono<Void> delete(TrainingId id) {
        return r2dbcRepository.deleteById(id.getValue());
    }
}
```

### Phase 6: REST Controller

**File: `adapters/in/web/TrainingController.java`**
```java
@RestController
@RequestMapping("/api/v1/training")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class TrainingController {
    private final TrainingService trainingService;

    @PostMapping
    public Mono<ResponseEntity<TrainingResponseDTO>> conductTraining(
            @RequestBody TrainingRequest request) {
        return trainingService.conductTraining(
                        PlayerId.of(UUID.fromString(request.playerId())),
                        TeamId.of(UUID.fromString(request.teamId())),
                        Training.TrainingType.valueOf(request.trainingType()),
                        request.intensity())
                .map(result -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(mapToDTO(result)))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping("/players/{playerId}")
    public Flux<TrainingResponseDTO> getPlayerTrainings(@PathVariable String playerId) {
        return trainingService.getPlayerTrainings(PlayerId.of(UUID.fromString(playerId)))
                .map(this::mapTrainingToDTO);
    }

    private TrainingResponseDTO mapToDTO(TrainingResult result) {
        return new TrainingResponseDTO(
                null,
                null,
                null,
                null,
                0,
                result.getImprovementPoints(),
                result.getFatigueIncrease(),
                result.getCompletedAt()
        );
    }

    private TrainingResponseDTO mapTrainingToDTO(Training training) {
        // Map to DTO...
        return null;
    }
}
```

### Phase 7: Database Migration

**File: `src/main/resources/db/migration/V2__Add_Training_Feature.sql`**
```sql
-- Create trainings table
CREATE TABLE IF NOT EXISTS trainings (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id),
    team_id UUID NOT NULL REFERENCES teams(id),
    training_type VARCHAR(20) NOT NULL,
    intensity INTEGER NOT NULL CHECK (intensity >= 1 AND intensity <= 10),
    duration_minutes INTEGER NOT NULL DEFAULT 60,
    completed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_trainings_player_id ON trainings(player_id);
CREATE INDEX idx_trainings_team_id ON trainings(team_id);
CREATE INDEX idx_trainings_created_at ON trainings(created_at);
```

### Phase 8: Testing

**File: `src/test/java/com/footballmanager/domain/model/TrainingTest.java`**
```java
class TrainingTest {

    @Test
    void shouldCreateTraining() {
        Training training = Training.create(
                PlayerId.generate(),
                TeamId.generate(),
                Training.TrainingType.ATTACKING,
                8
        );

        assertNotNull(training);
        assertEquals(Training.TrainingType.ATTACKING, training.getType());
        assertEquals(8, training.getIntensity());
    }

    @Test
    void shouldCalculateImprovement() {
        Training training = Training.create(
                PlayerId.generate(),
                TeamId.generate(),
                Training.TrainingType.ATTACKING,
                10
        );

        TrainingResult result = training.complete();

        assertTrue(result.getImprovementPoints() > 0);
        assertTrue(result.getFatigueIncrease() > 0);
    }

    @Test
    void shouldValidateIntensity() {
        assertThrows(IllegalArgumentException.class, () -> {
            Training.create(
                    PlayerId.generate(),
                    TeamId.generate(),
                    Training.TrainingType.ATTACKING,
                    11  // Invalid: > 10
            );
        });
    }
}
```

**File: `src/test/java/com/footballmanager/application/service/TrainingServiceTest.java`**
```java
class TrainingServiceTest {

    @Test
    void shouldConductTraining() {
        // Mock repositories
        PlayerId playerId = PlayerId.generate();
        TeamId teamId = TeamId.generate();

        StepVerifier.create(trainingService.conductTraining(
                        playerId,
                        teamId,
                        Training.TrainingType.ATTACKING,
                        8))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertTrue(result.getImprovementPoints() > 0);
                })
                .verifyComplete();
    }
}
```

## Best Practices for Extensions

### 1. Start with Domain
- Define rich domain entities
- Implement business logic at domain level
- Write tests for domain behavior first

### 2. Minimize Dependencies
- Domain models should have zero external dependencies
- Services use dependency injection for ports
- Controllers are thin - delegate to services

### 3. Follow Naming Conventions
- **Entities**: Singular noun (Player, Match, Training)
- **Repositories**: `XyzRepository` (interface), `XyzRepositoryAdapter` (implementation)
- **Services**: `XyzService`
- **Controllers**: `XyzController`
- **DTOs**: `XyzRequest`, `XyzDTO`, `XyzResponse`

### 4. Test Each Layer
- **Domain Tests**: No mocks, pure logic
- **Service Tests**: Mock repositories, test orchestration
- **Controller Tests**: Mock services, test HTTP handling

### 5. Database Changes
- One migration per feature
- Use `V{N}__Description.sql` naming
- Include rollback considerations
- Index frequently queried columns

### 6. API Versioning
- Use `/api/v1/` prefix
- Break changes require new version
- Deprecate gradually

### 7. Error Handling
- Domain throws unchecked exceptions for invariant violations
- Services catch and convert to business exceptions
- Controllers return appropriate HTTP status codes

## Adding Related Entities

If adding multiple related features (e.g., Training + Coaching):

1. **Identify relationships** at domain level
2. **Use aggregate roots** to manage consistency
3. **Create composite tests** covering interactions
4. **Update migrations** for all related tables

Example:
```java
// Team is aggregate root
public class Team {
    private Set<PlayerId> squadPlayerIds;      // Related players
    private Set<TrainingId> trainingIds;       // Related trainings
    private Set<CoachId> coachIds;             // Related coaches

    // Operations ensure consistency
}
```

## Async Operations

For long-running operations (match simulation, bulk training):

```java
@Service
public class TrainingService {

    public Mono<TrainingResult> conductTraining(...) {
        return Mono.fromCallable(() -> {
            // CPU-intensive calculation
            return performSimulation();
        })
        .subscribeOn(Schedulers.boundedElastic());  // Run on separate thread
    }
}
```

## Event Sourcing (Future Enhancement)

For audit trail and event replay:

```java
public interface DomainEvent {
    TrainingId getAggregateId();
    Instant getOccurredAt();
}

public class TrainingCompletedEvent implements DomainEvent {
    private final TrainingId trainingId;
    private final int improvementPoints;
    // ...
}
```

## Caching Strategy

For frequently accessed data:

```java
@Service
public class TrainingService {

    @Cacheable("player-trainings")
    public Flux<Training> getPlayerTrainings(PlayerId playerId) {
        return trainingRepository.findByPlayerId(playerId);
    }

    @CacheEvict(value = "player-trainings", key = "#playerId")
    public Mono<Void> save(Training training, PlayerId playerId) {
        return trainingRepository.save(training);
    }
}
```

## Summary Checklist

- [ ] Created domain models with business logic
- [ ] Created output port interfaces
- [ ] Implemented application service
- [ ] Created DTOs for API
- [ ] Implemented R2DBC entity and repositories
- [ ] Created repository adapter
- [ ] Implemented REST controller
- [ ] Added database migration
- [ ] Written domain tests
- [ ] Written service tests
- [ ] Updated API documentation
- [ ] Added error handling
- [ ] Validated input parameters
