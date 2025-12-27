# Football Manager - Architecture Guide

## Overview

This project implements a **Hexagonal Architecture (Ports & Adapters)** with reactive programming using Spring Boot WebFlux and R2DBC. This ensures clean separation of concerns, testability, and scalability.

## Architecture Layers

### 1. Domain Layer (`domain/`)

The core of the application containing business logic that is **completely independent** of frameworks.

#### Models (`domain/model/`)
Rich domain entities with behavior, not just data containers:

- **UserId, TeamId, PlayerId, LeagueId, MatchId** - Strongly typed IDs
- **User** - Represents a user account with email/username/password
- **Team** - Manages squad, budget, formation, and players
- **Player** - Represents a player with attributes and condition
- **PlayerAttributes** - Immutable value object for player stats
- **Formation** - Value object representing team formation
- **Match** - Simulated match with result and events
- **MatchResult** - Value object containing match outcome
- **MatchEvent** - Event occurring during match (goal, card, injury)
- **League** - Container for teams and seasons
- **Season** - Annual competition with standings
- **Standing** - Team's position in league table
- **Transfer** - Player trading between teams
- **Contract** - Employment agreement between player and team

**Key Principles:**
- Entities have identity and lifecycle
- Value objects are immutable
- Business rules are enforced in the domain
- State transitions are explicit (e.g., `match.simulate(result)`)
- No dependencies on external frameworks

#### Ports (`domain/ports/`)

**Input Ports (Use Cases):**
These define what operations the application supports. Implemented by application services.

**Output Ports (Interfaces):**
These define dependencies the domain has. Implemented by adapters.

```java
// Output port - implemented by adapters
public interface UserRepository {
    Mono<Void> save(User user);
    Mono<User> findById(UserId id);
}

public interface MatchEngine {
    Mono<MatchResult> simulate(Team homeTeam, Team awayTeam);
}
```

### 2. Application Layer (`application/`)

Orchestrates domain logic and coordinates between services.

#### Services (`application/service/`)

**AuthService** - User registration and authentication
```java
- register(RegisterUserRequest) -> Mono<JwtTokenResponse>
- login(LoginRequest) -> Mono<JwtTokenResponse>
```

**TeamService** - Team management operations
```java
- createTeam(...) -> Mono<Team>
- addPlayerToTeam(...) -> Mono<Team>
- getTeamSquad(...) -> Flux<Player>
```

**MatchService** - Match simulation and management
```java
- createMatch(...) -> Mono<Match>
- simulateMatch(...) -> Mono<Match>
- getMatchResult(...) -> Mono<MatchResult>
```

**MatchEngineImpl** - Core simulation logic
- Calculates team strength based on squad composition
- Generates possession percentage
- Simulates goals, events, and final result
- Uses controlled randomness for realistic outcomes

#### DTOs (`application/dto/`)

Data Transfer Objects for API communication:
- `RegisterUserRequest`, `LoginRequest`, `JwtTokenResponse`
- `CreateTeamRequest`, `TeamDTO`
- `CreateMatchRequest`, `MatchDTO`

**Rationale:** DTOs shield the domain model from external changes and API versioning.

### 3. Adapters Layer (`adapters/`)

Bridges between domain/application and external systems.

#### Input Adapters (`adapters/in/`)

**REST Controllers** - HTTP endpoints
```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    // Handles HTTP requests and delegates to AuthService
}
```

**Responsibility:**
- Parse HTTP requests
- Validate input parameters
- Call application services
- Return HTTP responses
- Error handling and status codes

#### Output Adapters (`adapters/out/`)

- Persistence adapters
- External service clients
- Notification services

### 4. Infrastructure Layer (`infrastructure/`)

Framework-specific implementations.

#### Persistence (`infrastructure/persistence/`)

**R2DBC Entities** - Database mapping
```java
@Table("users")
public class UserEntity {
    // Maps to database schema
}
```

**R2DBC Repositories** - Spring Data reactive repositories
```java
public interface UserR2dbcRepository extends R2dbcRepository<UserEntity, UUID> {
    Mono<UserEntity> findByEmail(String email);
}
```

**Repository Adapters** - Implement output ports
```java
@Component
public class UserRepositoryAdapter implements UserRepository {
    // Converts between domain models and database entities
}
```

#### Security (`infrastructure/security/`)

**JwtTokenProvider** - JWT token generation and validation
**SecurityConfig** - Spring Security reactive configuration

#### Configuration (`infrastructure/config/`)

Database, caching, scheduler configuration

## Reactive Flow

The entire application uses **Project Reactor** (Mono/Flux) for non-blocking operations:

```
HTTP Request
    ↓
Controller (Mono<HttpRequest>)
    ↓
Service (Mono<DomainModel> or Flux<DomainModel>)
    ↓
Repository (Mono<Entity>)
    ↓
Database (R2DBC)
    ↓
Entity → Domain Model conversion
    ↓
Service processing
    ↓
Controller response (Mono<ResponseEntity>)
    ↓
HTTP Response
```

## Dependency Injection

Dependencies flow inward:
- Infrastructure depends on Application
- Application depends on Domain
- Domain has no external dependencies

```
Domain (no dependencies)
  ↑
Application (depends on domain)
  ↑
Adapters & Infrastructure (depend on application)
  ↑
Frameworks (Spring, R2DBC, JWT)
```

## Database Schema

### User-Team Relationship
```sql
users (1) ──→ (many) teams
```

### Team-Player Relationship
```sql
teams (many) ←── (many) players
(via team_squad junction table)
```

### Match Components
```sql
matches (1) ──→ (many) match_events
matches (many) ──→ (1) teams (home)
matches (many) ──→ (1) teams (away)
```

### League Structure
```sql
leagues (1) ──→ (many) seasons (1) ──→ (many) standings
leagues (many) ←── (many) teams (via league_teams)
```

## Error Handling

### Domain Level
Domain logic throws exceptions for business rule violations:
```java
if (team.getSquadSize() >= 30) {
    throw new IllegalStateException("Squad cannot exceed 30 players");
}
```

### Application Level
Services catch domain exceptions and convert to application errors:
```java
try {
    team.addPlayer(playerId);
} catch (IllegalStateException e) {
    return Mono.error(new BusinessException(e.getMessage()));
}
```

### Adapter Level
Controllers catch exceptions and return appropriate HTTP responses:
```java
.onErrorResume(BusinessException.class, e ->
    Mono.just(ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage())))
)
```

## Testing Strategy

### Domain Layer Tests
- Test entity behavior and invariants
- Test value object equality
- Test state transitions
- No mocks needed (pure logic)

### Application Layer Tests
- Test service orchestration
- Mock repositories and external services
- Test error handling

### Adapter Layer Tests
- Test controller request/response handling
- Test persistence mapping
- Integration tests with embedded database

## Scalability Considerations

### Reactive Performance
- Non-blocking I/O throughout the stack
- Optimized for high concurrency
- Backpressure handling with Flux/Mono

### Database Optimization
- Indexed frequently queried columns
- Denormalized views for complex queries
- Connection pooling (R2DBC pool)
- N+1 query prevention with joins

### Caching Strategy
- User lookup cache (TTL: 1 hour)
- Team standings cache (TTL: 5 minutes)
- Player market value cache (TTL: 15 minutes)

### Microservices Ready
- Clear service boundaries
- Independent domain models
- Event-driven communication possible
- API versioning built in

## Extension Points

### Adding New Features

1. **Define Domain Model**
   ```java
   public class Training {
       // New entity with business logic
   }
   ```

2. **Create Output Port**
   ```java
   public interface TrainingRepository {
       Mono<Void> save(Training training);
   }
   ```

3. **Implement Service**
   ```java
   @Service
   public class TrainingService {
       // Application logic
   }
   ```

4. **Create REST Endpoint**
   ```java
   @RestController
   public class TrainingController {
       // HTTP interface
   }
   ```

5. **Implement Persistence**
   ```java
   @Component
   public class TrainingRepositoryAdapter implements TrainingRepository {
       // Database mapping
   }
   ```

## Configuration Management

### Environment-Specific Configuration

**application.yaml** (development defaults)
**application-prod.yaml** (production)
**application-test.yaml** (test)

Override via environment variables:
```bash
DB_HOST=prod.database.com
DB_USER=produser
JWT_SECRET=your-production-secret
```

## Deployment

### Docker Image
```dockerfile
FROM openjdk:17-slim
COPY target/football-manager.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Kubernetes Ready
- Liveness probe: `/actuator/health`
- Readiness probe: `/actuator/health/readiness`
- Graceful shutdown: 30 seconds
- Request logging for debugging

### Performance Tuning
- Thread pool size: 2 × CPU cores
- Connection pool size: 20 connections
- JWT cache size: 1000 tokens
- Database query timeout: 10 seconds

## Security

### Authentication
- JWT tokens with HS512 algorithm
- 24-hour token expiration
- Refresh token support

### Authorization
- Role-based access control (USER, ADMIN)
- Team ownership validation
- League admin verification

### Data Protection
- Password hashing with BCrypt (strength: 12)
- HTTPS only in production
- SQL injection prevention via R2DBC parameterization
- XSS prevention via response encoding
