-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Teams table
CREATE TABLE IF NOT EXISTS teams (
    id UUID PRIMARY KEY,
    manager_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL,
    budget DECIMAL(15, 2) NOT NULL DEFAULT 10000000,
    formation VARCHAR(20) NOT NULL DEFAULT 'FORMATION_4_3_3',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Players table
CREATE TABLE IF NOT EXISTS players (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    age INTEGER NOT NULL,
    position VARCHAR(20) NOT NULL,
    attack INTEGER NOT NULL,
    defense INTEGER NOT NULL,
    technique INTEGER NOT NULL,
    speed INTEGER NOT NULL,
    stamina INTEGER NOT NULL,
    mentality INTEGER NOT NULL,
    market_value DECIMAL(15, 2) NOT NULL,
    energy INTEGER NOT NULL DEFAULT 100,
    injured BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Team squad junction table
CREATE TABLE IF NOT EXISTS team_squad (
    team_id UUID NOT NULL REFERENCES teams(id),
    player_id UUID NOT NULL REFERENCES players(id),
    PRIMARY KEY (team_id, player_id)
);

-- Leagues table
CREATE TABLE IF NOT EXISTS leagues (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- League teams junction table
CREATE TABLE IF NOT EXISTS league_teams (
    league_id UUID NOT NULL REFERENCES leagues(id),
    team_id UUID NOT NULL REFERENCES teams(id),
    PRIMARY KEY (league_id, team_id)
);

-- Matches table
CREATE TABLE IF NOT EXISTS matches (
    id UUID PRIMARY KEY,
    home_team_id UUID NOT NULL REFERENCES teams(id),
    away_team_id UUID NOT NULL REFERENCES teams(id),
    scheduled_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    home_goals INTEGER,
    away_goals INTEGER,
    home_possession INTEGER,
    away_possession INTEGER,
    home_shots INTEGER,
    away_shots INTEGER,
    summary TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    simulated_at TIMESTAMP
);

-- Match events table
CREATE TABLE IF NOT EXISTS match_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID NOT NULL REFERENCES matches(id),
    event_type VARCHAR(20) NOT NULL,
    minute INTEGER NOT NULL,
    player_name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL
);

-- Seasons table
CREATE TABLE IF NOT EXISTS seasons (
    id SERIAL PRIMARY KEY,
    year VARCHAR(10) NOT NULL,
    league_id UUID NOT NULL REFERENCES leagues(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(year, league_id)
);

-- Standings table
CREATE TABLE IF NOT EXISTS standings (
    id SERIAL PRIMARY KEY,
    season_id INTEGER NOT NULL REFERENCES seasons(id),
    team_id UUID NOT NULL REFERENCES teams(id),
    played INTEGER NOT NULL DEFAULT 0,
    won INTEGER NOT NULL DEFAULT 0,
    drawn INTEGER NOT NULL DEFAULT 0,
    lost INTEGER NOT NULL DEFAULT 0,
    goals_for INTEGER NOT NULL DEFAULT 0,
    goals_against INTEGER NOT NULL DEFAULT 0,
    UNIQUE(season_id, team_id)
);

-- Transfers table
CREATE TABLE IF NOT EXISTS transfers (
    id VARCHAR(36) PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id),
    from_team_id UUID NOT NULL REFERENCES teams(id),
    to_team_id UUID NOT NULL REFERENCES teams(id),
    offer_amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Contracts table
CREATE TABLE IF NOT EXISTS contracts (
    id SERIAL PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id),
    team_id UUID NOT NULL REFERENCES teams(id),
    weekly_salary DECIMAL(10, 2) NOT NULL,
    duration_years INTEGER NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_teams_manager_id ON teams(manager_id);
CREATE INDEX idx_team_squad_team_id ON team_squad(team_id);
CREATE INDEX idx_team_squad_player_id ON team_squad(player_id);
CREATE INDEX idx_matches_home_team ON matches(home_team_id);
CREATE INDEX idx_matches_away_team ON matches(away_team_id);
CREATE INDEX idx_matches_status ON matches(status);
CREATE INDEX idx_transfers_player_id ON transfers(player_id);
CREATE INDEX idx_transfers_status ON transfers(status);
CREATE INDEX idx_contracts_player_id ON contracts(player_id);
CREATE INDEX idx_contracts_team_id ON contracts(team_id);
CREATE INDEX idx_standings_season_id ON standings(season_id);
