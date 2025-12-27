# Football Manager API Documentation

## Architecture Overview

This application follows **Hexagonal Architecture (Ports & Adapters)** principles:

```
domain/
├── model/          # Rich domain entities
├── ports/
│   ├── in/         # Input ports (use cases)
│   └── out/        # Output ports (interfaces for adapters)

application/
├── service/        # Application services
├── dto/            # Data Transfer Objects

adapters/
├── in/             # Input adapters (REST controllers, CLI, etc.)
└── out/            # Output adapters (persistence, external services)

infrastructure/
├── persistence/    # R2DBC repositories and entities
├── security/       # Security and JWT configuration
└── config/         # Spring configuration
```

## Core Entities

### User
- Manages user registration and authentication
- Has one or more teams
- Roles: USER, ADMIN

### Team
- Managed by a User
- Contains up to 30 players
- Has formation and budget
- Tracks financial status

### Player
- Belongs to a team
- Attributes: attack, defense, technique, speed, stamina, mentality
- Can be injured or traded
- Has energy level (0-100)

### Match
- Between two teams
- Can be simulated
- Generates events and results
- Tracks possession, shots, goals

### League
- Contains multiple teams
- Hosts seasons
- Maintains standings table

### Transfer
- Offers to buy/sell players
- States: PENDING, ACCEPTED, REJECTED, COMPLETED

### Contract
- Defines player salary and duration
- Teams manage player payroll

## REST API Endpoints

### Authentication

#### Register User
```
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "username": "johndoe",
  "password": "securePassword123"
}

Response (201):
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "expiresIn": 86400,
  "tokenType": "Bearer"
}
```

#### Login
```
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123"
}

Response (200):
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "expiresIn": 86400,
  "tokenType": "Bearer"
}
```

### Teams

#### Create Team
```
POST /api/v1/teams
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "name": "Manchester United",
  "country": "England",
  "initialBudget": 50000000
}

Response (201):
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "managerId": "550e8400-e29b-41d4-a716-446655440001",
  "name": "Manchester United",
  "country": "England",
  "budget": 50000000,
  "formation": "4-3-3",
  "squadSize": 0,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

#### Get Team
```
GET /api/v1/teams/{teamId}
Authorization: Bearer {accessToken}

Response (200):
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "managerId": "550e8400-e29b-41d4-a716-446655440001",
  "name": "Manchester United",
  "country": "England",
  "budget": 50000000,
  "formation": "4-3-3",
  "squadSize": 11,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

#### Get User's Teams
```
GET /api/v1/teams
Authorization: Bearer {accessToken}

Response (200):
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Manchester United",
    ...
  }
]
```

#### Add Player to Team
```
POST /api/v1/teams/{teamId}/players/{playerId}
Authorization: Bearer {accessToken}

Response (200): Team object
```

#### Remove Player from Team
```
DELETE /api/v1/teams/{teamId}/players/{playerId}
Authorization: Bearer {accessToken}

Response (204): No content
```

#### Get Team Squad
```
GET /api/v1/teams/{teamId}/squad
Authorization: Bearer {accessToken}

Response (200):
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Cristiano Ronaldo",
    "age": 37,
    "position": "ST",
    "overallRating": 89,
    "energy": 95,
    "injured": false,
    "marketValue": 5000000
  }
]
```

### Players

#### Create Player
```
POST /api/v1/players
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "name": "Cristiano Ronaldo",
  "age": 37,
  "position": "ST",
  "attributes": {
    "attack": 92,
    "defense": 35,
    "technique": 88,
    "speed": 81,
    "stamina": 80,
    "mentality": 89
  },
  "marketValue": 5000000
}

Response (201): Player object
```

#### Get Player
```
GET /api/v1/players/{playerId}
Authorization: Bearer {accessToken}

Response (200): Player object
```

#### Get Available Players (Transfer Market)
```
GET /api/v1/players/available
Authorization: Bearer {accessToken}

Response (200):
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Kylian Mbappé",
    "position": "ST",
    "overallRating": 91,
    "marketValue": 180000000
  }
]
```

#### Update Player
```
PUT /api/v1/players/{playerId}
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "energy": 85,
  "injured": false
}

Response (200): Player object
```

### Matches

#### Create Match
```
POST /api/v1/matches
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "homeTeamId": "550e8400-e29b-41d4-a716-446655440000",
  "awayTeamId": "550e8400-e29b-41d4-a716-446655440001",
  "scheduledAt": "2024-02-15T20:00:00Z"
}

Response (201): Match object
```

#### Simulate Match
```
POST /api/v1/matches/{matchId}/simulate
Authorization: Bearer {accessToken}

Response (200):
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "homeTeamId": "550e8400-e29b-41d4-a716-446655440000",
  "awayTeamId": "550e8400-e29b-41d4-a716-446655440001",
  "status": "SIMULATED",
  "result": {
    "homeGoals": 2,
    "awayGoals": 1,
    "homePossession": 58,
    "awayPossession": 42,
    "homeShots": 12,
    "awayShots": 8,
    "summary": "Manchester United 2-1 Liverpool | Possession: 58%-42%",
    "events": [
      {
        "type": "GOAL",
        "minute": 23,
        "playerName": "Bruno Fernandes",
        "description": "Goal scored by home team"
      }
    ]
  }
}
```

#### Get Match
```
GET /api/v1/matches/{matchId}
Authorization: Bearer {accessToken}

Response (200): Match object
```

#### Get Team Matches
```
GET /api/v1/teams/{teamId}/matches
Authorization: Bearer {accessToken}

Response (200): [Match array]
```

#### Get Scheduled Matches
```
GET /api/v1/matches/scheduled
Authorization: Bearer {accessToken}

Response (200): [Match array]
```

### Leagues

#### Create League
```
POST /api/v1/leagues
Authorization: Bearer {accessToken} (ADMIN only)
Content-Type: application/json

{
  "name": "Premier League",
  "country": "England"
}

Response (201): League object
```

#### Get League
```
GET /api/v1/leagues/{leagueId}
Authorization: Bearer {accessToken}

Response (200): League object
```

#### Get All Leagues
```
GET /api/v1/leagues
Authorization: Bearer {accessToken}

Response (200): [League array]
```

#### Add Team to League
```
POST /api/v1/leagues/{leagueId}/teams/{teamId}
Authorization: Bearer {accessToken} (ADMIN only)

Response (200): League object
```

#### Get League Standings
```
GET /api/v1/leagues/{leagueId}/standings?year=2024
Authorization: Bearer {accessToken}

Response (200):
[
  {
    "position": 1,
    "teamId": "550e8400-e29b-41d4-a716-446655440000",
    "teamName": "Manchester City",
    "played": 25,
    "won": 20,
    "drawn": 3,
    "lost": 2,
    "goalsFor": 68,
    "goalsAgainst": 20,
    "goalDifference": 48,
    "points": 63
  }
]
```

### Transfers

#### Create Transfer Offer
```
POST /api/v1/transfers
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "playerId": "550e8400-e29b-41d4-a716-446655440000",
  "fromTeamId": "550e8400-e29b-41d4-a716-446655440000",
  "toTeamId": "550e8400-e29b-41d4-a716-446655440001",
  "offerAmount": 80000000
}

Response (201): Transfer object
```

#### Accept Transfer
```
POST /api/v1/transfers/{transferId}/accept
Authorization: Bearer {accessToken}

Response (200): Transfer object
```

#### Reject Transfer
```
POST /api/v1/transfers/{transferId}/reject
Authorization: Bearer {accessToken}

Response (200): Transfer object
```

#### Complete Transfer
```
POST /api/v1/transfers/{transferId}/complete
Authorization: Bearer {accessToken}

Response (200): Transfer object
```

#### Get Pending Transfers
```
GET /api/v1/transfers/pending
Authorization: Bearer {accessToken}

Response (200): [Transfer array]
```

### Training

#### Train Player
```
POST /api/v1/players/{playerId}/train
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "trainingType": "ATTACKING",
  "intensity": "HIGH"
}

Response (200):
{
  "playerId": "550e8400-e29b-41d4-a716-446655440000",
  "trainingType": "ATTACKING",
  "intensity": "HIGH",
  "attributeImprovement": {
    "attack": 2,
    "technique": 1
  },
  "fatigueIncrease": 15,
  "completedAt": "2024-01-15T14:30:00Z"
}
```

## Authentication

All endpoints (except `/auth/**`) require JWT token in header:

```
Authorization: Bearer {accessToken}
```

Token structure (JWT):
```
Header: {"alg": "HS512"}
Payload: {"sub": "userId", "role": "USER", "iat": 1234567890, "exp": 1234654290}
Signature: HMAC-SHA512(header.payload, secret)
```

## Error Responses

### 400 Bad Request
```json
{
  "error": "Invalid request",
  "message": "Team name is required",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 401 Unauthorized
```json
{
  "error": "Unauthorized",
  "message": "Missing or invalid JWT token",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 403 Forbidden
```json
{
  "error": "Forbidden",
  "message": "You do not have permission to access this resource",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 404 Not Found
```json
{
  "error": "Not Found",
  "message": "Team not found",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 500 Internal Server Error
```json
{
  "error": "Internal Server Error",
  "message": "An unexpected error occurred",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## Rate Limiting

- 100 requests per minute for authenticated users
- 10 requests per minute for unauthenticated requests

## Pagination

List endpoints support pagination:

```
GET /api/v1/players?page=0&size=20&sort=name,asc
```

Parameters:
- `page`: Page number (0-indexed, default: 0)
- `size`: Page size (default: 20, max: 100)
- `sort`: Sort criteria (format: field,direction)

## Database Design

### Normalized Schema
- Proper foreign key relationships
- Indexed frequently queried columns
- Separate junction tables for many-to-many relationships
- Timestamps on all entities for audit trail

### Performance Considerations
- Indexed team manager lookups
- Indexed match status queries
- Indexed transfer status queries
- Player search optimization
