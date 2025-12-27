# Football Manager - Turn-Based Simulation Platform

A production-ready football team management and match simulation system built with Java Spring Boot, WebFlux, and Hexagonal Architecture.

## Technology Stack

**Backend:**
- Java 17+
- Spring Boot 3.2.1
- Spring WebFlux (Reactive)
- Spring Data R2DBC (Reactive Database)
- PostgreSQL
- JWT Authentication
- Lombok
- JUnit 5 + Reactor Test

**Architecture:**
- Hexagonal Architecture (Ports & Adapters)
- Domain-Driven Design
- Clean Code Principles
- Reactive Programming (Project Reactor)

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 12+
- Docker (optional, for PostgreSQL)

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd football-manager
   ```

2. **Set up PostgreSQL**
   ```bash
   # Using Docker
   docker run --name football-db \
     -e POSTGRES_PASSWORD=postgres \
     -e POSTGRES_DB=football_manager \
     -p 5432:5432 \
     -d postgres:15
   ```

3. **Configure environment variables**
   ```bash
   cat > .env << EOF
   DB_HOST=localhost
   DB_PORT=5432
   DB_NAME=football_manager
   DB_USER=postgres
   DB_PASSWORD=postgres
   JWT_SECRET=your-secure-secret-key-change-in-production
   EOF
   ```

4. **Build the project**
   ```bash
   mvn clean install
   ```

5. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

   The API will be available at: `http://localhost:8080/api/v1`

## Project Structure

```
football-manager/
├── src/main/java/com/footballmanager/
│   ├── domain/              # Core business logic (DDD)
│   │   ├── model/           # Rich domain entities
│   │   └── ports/           # Interfaces for inbound/outbound adapters
│   │       ├── in/          # Input ports (use cases)
│   │       └── out/         # Output ports (repositories, services)
│   │
│   ├── application/         # Application layer
│   │   ├── service/         # Business logic orchestration
│   │   └── dto/             # Data Transfer Objects
│   │
│   ├── adapters/            # Hexagonal adapters
│   │   ├── in/              # Input adapters (REST controllers)
│   │   └── out/             # Output adapters (persistence, external APIs)
│   │
│   └── infrastructure/      # Framework-specific implementations
│       ├── persistence/     # R2DBC repositories and mapping
│       ├── security/        # JWT and Spring Security
│       └── config/          # Spring configuration
│
├── src/test/java/           # Unit and integration tests
├── src/main/resources/
│   ├── application.yaml     # Configuration
│   └── db/migration/        # Flyway SQL migrations
│
├── pom.xml                  # Maven dependencies
├── API_DOCUMENTATION.md     # Full API reference
└── ARCHITECTURE.md          # Detailed architecture guide
```

## Core Domain Models

### User
```java
User {
  id: UserId (UUID)
  email: String (unique)
  username: String (unique, 3-50 chars)
  passwordHash: String (bcrypt)
  role: UserRole (USER | ADMIN)
  createdAt: Instant
  updatedAt: Instant
}
```

### Team
```java
Team {
  id: TeamId (UUID)
  managerId: UserId
  name: String (3-100 chars)
  country: String
  budget: BigDecimal
  formation: Formation (4-3-3, 4-2-3-1, etc.)
  squad: Set<PlayerId> (max 30)
  createdAt: Instant
  updatedAt: Instant
}
```

### Player
```java
Player {
  id: PlayerId (UUID)
  name: String
  age: int (16-45)
  position: Position (GK, CB, LB, RB, CDM, CM, CAM, LM, RM, ST, LW, RW)
  attributes: PlayerAttributes
    - attack: 0-100
    - defense: 0-100
    - technique: 0-100
    - speed: 0-100
    - stamina: 0-100
    - mentality: 0-100
    - overall: calculated average
  energy: 0-100
  injured: boolean
  marketValue: BigDecimal
  createdAt: Instant
  updatedAt: Instant
}
```

### Match
```java
Match {
  id: MatchId (UUID)
  homeTeamId: TeamId
  awayTeamId: TeamId
  scheduledAt: Instant
  status: MatchStatus (SCHEDULED | SIMULATED | CANCELLED)
  result: MatchResult (after simulation)
    - homeGoals: int
    - awayGoals: int
    - homePossession: 0-100%
    - awayPossession: 0-100%
    - homeShots: int
    - awayShots: int
    - events: List<MatchEvent>
    - summary: String
  createdAt: Instant
  simulatedAt: Instant
}
```

## API Endpoints

### Authentication
```
POST   /api/v1/auth/register      - Register new user
POST   /api/v1/auth/login         - Login and get JWT token
```

### Teams
```
POST   /api/v1/teams              - Create team
GET    /api/v1/teams/{id}         - Get team details
GET    /api/v1/teams              - Get user's teams
GET    /api/v1/teams/{id}/squad   - Get team squad
POST   /api/v1/teams/{id}/players/{playerId}  - Add player
DELETE /api/v1/teams/{id}/players/{playerId}  - Remove player
```

### Players
```
POST   /api/v1/players            - Create player
GET    /api/v1/players/{id}       - Get player details
GET    /api/v1/players/available  - Get transfer market
PUT    /api/v1/players/{id}       - Update player (energy, injury)
```

### Matches
```
POST   /api/v1/matches            - Create match
POST   /api/v1/matches/{id}/simulate - Simulate match
GET    /api/v1/matches/{id}       - Get match details
GET    /api/v1/matches/scheduled  - Get scheduled matches
```

### Leagues
```
POST   /api/v1/leagues            - Create league (ADMIN)
GET    /api/v1/leagues/{id}       - Get league details
GET    /api/v1/leagues            - Get all leagues
GET    /api/v1/leagues/{id}/standings - Get league standings
```

### Transfers
```
POST   /api/v1/transfers          - Create transfer offer
POST   /api/v1/transfers/{id}/accept - Accept transfer
POST   /api/v1/transfers/{id}/reject - Reject transfer
POST   /api/v1/transfers/{id}/complete - Complete transfer
GET    /api/v1/transfers/pending  - Get pending transfers
```

## Authentication

### JWT Token Flow

1. **Register/Login**
   - User sends credentials
   - Server validates and issues JWT token
   - Token included in `Authorization: Bearer {token}` header

2. **Token Structure**
   ```
   Header: {"alg": "HS512"}
   Payload: {"sub": "userId", "role": "USER", "iat": ..., "exp": ...}
   Signature: HMAC-SHA512(header.payload, secret)
   ```

3. **Token Validation**
   - Verified on each protected endpoint
   - Signature validated using secret key
   - Expiration checked (24 hours default)

### Example Request
```bash
# Get access token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'

# Use token in subsequent requests
curl -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..." \
  http://localhost:8080/api/v1/teams
```

## Match Simulation

The match engine simulates realistic football outcomes:

### Algorithm
1. **Team Strength Calculation**
   - Based on squad size and formation
   - Typical range: 70-90 overall rating

2. **Possession Distribution**
   - Stronger team gets more possession
   - Random variance: ±10%
   - Realistic range: 30%-70%

3. **Shot Generation**
   - Possession-based calculation
   - Formula: `possession / 15 + random(0-5)`
   - Minimum 3 shots per team

4. **Goal Probability**
   - Formula: `(teamOverall / 100) * (possession / 100) * 3.5`
   - Stochastic generation with probability distribution
   - Realistic outcomes: 0-5 goals per team

5. **Event Generation**
   - Goals at random minutes (10-90)
   - Cards: 30% probability
   - Injuries: 20% probability
   - Sorted chronologically

## Testing

### Run All Tests
```bash
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=PlayerTest
```

### Run with Coverage
```bash
mvn clean test jacoco:report
# Coverage report: target/site/jacoco/index.html
```

### Test Structure
- **Domain Tests** - Entity behavior, invariants, state transitions
- **Service Tests** - Business logic orchestration, mocking
- **Integration Tests** - Database operations, API endpoints

### Example Test
```java
@Test
void shouldCreateTeam() {
    TeamId teamId = TeamId.generate();
    Team team = Team.create(teamId, managerId, "Test Team", "England",
            new BigDecimal("10000000"), Formation.ofDefault());

    assertEquals(teamId, team.getId());
    assertEquals("Test Team", team.getName());
}
```

## Database Schema

### Tables
- **users** - User accounts and authentication
- **teams** - Team information and budget
- **players** - Player attributes and condition
- **team_squad** - Team-Player junction (many-to-many)
- **matches** - Match scheduling and results
- **match_events** - Match events (goals, cards, injuries)
- **leagues** - League information
- **league_teams** - League-Team junction (many-to-many)
- **seasons** - Annual competitions
- **standings** - League table standings
- **transfers** - Player transfer offers
- **contracts** - Player employment contracts

### Indexes
- team manager lookups (teams.manager_id)
- match status queries (matches.status)
- transfer status queries (transfers.status)
- player-team relationships (team_squad)
- contract lookups (contracts.player_id, contracts.team_id)

## Configuration

### Application Properties
```yaml
# src/main/resources/application.yaml

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/football_manager
    username: postgres
    password: postgres

jwt:
  secret: your-secret-key
  expiration: 86400000        # 24 hours
  refresh-expiration: 604800000  # 7 days

app:
  name: Football Manager
  version: 1.0.0
```

### Environment Variables
```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=football_manager
DB_USER=postgres
DB_PASSWORD=postgres
JWT_SECRET=your-production-secret
```

## Performance Optimization

### Caching Strategy
- User lookups: 1 hour TTL
- League standings: 5 minutes TTL
- Player market values: 15 minutes TTL

### Database Optimization
- Connection pooling: 20 connections
- Query timeout: 10 seconds
- Indexes on frequently queried columns
- Foreign key constraints for data integrity

### Reactive Performance
- Non-blocking I/O throughout
- Optimized for high concurrency
- Backpressure handling with Flux/Mono
- Thread pool: 2 × CPU cores

## Security Best Practices

1. **Password Hashing**
   - BCrypt with strength 12
   - Never store plaintext passwords

2. **JWT Security**
   - HS512 algorithm
   - 24-hour expiration
   - Secure secret key in production

3. **Data Protection**
   - HTTPS only in production
   - SQL injection prevention via R2DBC
   - XSS prevention via response encoding
   - CSRF protection for state-changing operations

4. **Access Control**
   - Role-based authorization (USER, ADMIN)
   - Resource ownership validation
   - Team-specific data isolation

## Deployment

### Docker Build
```dockerfile
FROM openjdk:17-slim
COPY target/football-manager.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: football-manager
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: api
        image: football-manager:latest
        ports:
        - containerPort: 8080
        env:
        - name: DB_HOST
          value: postgres-service
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
```

### Environment-Specific Configs
- `application.yaml` - Development (defaults)
- `application-prod.yaml` - Production
- `application-test.yaml` - Testing

## Contributing

### Code Standards
- Follow clean code principles
- Write domain tests for all business logic
- Document complex algorithms
- Keep services small and focused
- Use meaningful variable names

### Pull Request Checklist
- [ ] Tests pass locally (`mvn test`)
- [ ] New tests added for new features
- [ ] Documentation updated
- [ ] Code follows project style
- [ ] No hardcoded secrets/credentials

## Troubleshooting

### Database Connection Issues
```
Error: Connection refused
Solution: Ensure PostgreSQL is running and configured correctly
```

### JWT Token Expired
```
Error: 401 Unauthorized
Solution: Login again to get a new token
```

### R2DBC Connection Pool Exhausted
```
Error: Failed to obtain connection
Solution: Increase pool size in application.yaml (pool.max-size)
```

## Resources

- [Full API Documentation](API_DOCUMENTATION.md)
- [Architecture Guide](ARCHITECTURE.md)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring WebFlux Guide](https://spring.io/guides/gs/serving-web-content/)
- [R2DBC Documentation](https://r2dbc.io/)
- [Project Reactor](https://projectreactor.io/)

## Future Enhancements

1. **Advanced Simulation**
   - Tactical formations impact
   - Individual player chemistry
   - Weather conditions
   - Fan support effects

2. **Analytics**
   - Player performance trends
   - Team statistics dashboard
   - Historical data analysis
   - Prediction models

3. **Multiplayer Features**
   - Real-time match spectating
   - League tournaments
   - AI team management
   - Global leaderboards

4. **Mobile App**
   - React Native client
   - Offline mode support
   - Push notifications
   - Mobile-optimized UI

## License

MIT License - See LICENSE file for details

## Support

For issues and questions:
- Create GitHub issue
- Check existing documentation
- Review test examples
