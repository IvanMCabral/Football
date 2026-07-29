-- MANAGER MVP 1 clean database baseline.
-- Flyway V1 is the technical first schema migration, not a product version.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE countries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(3) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL UNIQUE,
    demonym VARCHAR(120),
    confederation VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(40) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    team_id UUID NULL
);

CREATE TABLE leagues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id UUID NULL REFERENCES countries(id),
    code VARCHAR(80) UNIQUE,
    name VARCHAR(160) NOT NULL,
    country VARCHAR(120) NOT NULL,
    tier INTEGER NOT NULL DEFAULT 1 CHECK (tier > 0),
    team_count INTEGER NOT NULL DEFAULT 20 CHECK (team_count > 0),
    rules_json TEXT NULL,
    season_id UUID NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    winner_id UUID NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE divisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    tier INTEGER NOT NULL CHECK (tier > 0),
    team_count INTEGER NOT NULL DEFAULT 20 CHECK (team_count > 0),
    sort_order INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_divisions_league_code UNIQUE (league_id, code),
    CONSTRAINT uq_divisions_league_tier UNIQUE (league_id, tier)
);

CREATE TABLE stadiums (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id UUID NULL REFERENCES countries(id),
    name VARCHAR(160) NOT NULL,
    city VARCHAR(120),
    capacity INTEGER CHECK (capacity IS NULL OR capacity > 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE clubs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_system VARCHAR(80),
    source_id VARCHAR(120),
    country_id UUID NULL REFERENCES countries(id),
    stadium_id UUID NULL REFERENCES stadiums(id),
    name VARCHAR(160) NOT NULL,
    short_name VARCHAR(80),
    reputation INTEGER NOT NULL DEFAULT 50 CHECK (reputation BETWEEN 1 AND 100),
    budget NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (budget >= 0),
    primary_color VARCHAR(20),
    secondary_color VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_clubs_source UNIQUE (source_system, source_id)
);

CREATE TABLE teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id UUID NULL REFERENCES clubs(id),
    manager_id UUID NOT NULL REFERENCES users(id),
    league_id UUID NULL REFERENCES leagues(id),
    name VARCHAR(160) NOT NULL,
    country VARCHAR(120) NOT NULL DEFAULT '',
    budget NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (budget >= 0),
    formation VARCHAR(20) NOT NULL DEFAULT '4-3-3',
    division VARCHAR(20) NOT NULL DEFAULT 'PRIMERA',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE users
    ADD CONSTRAINT fk_users_team_id
    FOREIGN KEY (team_id) REFERENCES teams(id);

ALTER TABLE leagues
    ADD CONSTRAINT fk_leagues_winner_id
    FOREIGN KEY (winner_id) REFERENCES teams(id);

CREATE TABLE seasons (
    id SERIAL PRIMARY KEY,
    external_id UUID UNIQUE DEFAULT gen_random_uuid(),
    season_year INTEGER NOT NULL CHECK (season_year BETWEEN 1900 AND 2200),
    league_id UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    starts_at DATE NULL,
    ends_at DATE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE leagues
    ADD CONSTRAINT fk_leagues_current_season
    FOREIGN KEY (season_id) REFERENCES seasons(external_id);

CREATE TABLE season_competitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id INTEGER NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    league_id UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    division_id UUID NULL REFERENCES divisions(id),
    name VARCHAR(160) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT uq_season_competitions UNIQUE (season_id, league_id, division_id)
);

CREATE TABLE club_division_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    season_id INTEGER NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    league_id UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    division_id UUID NOT NULL REFERENCES divisions(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_club_membership UNIQUE (club_id, season_id)
);

CREATE TABLE players (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_system VARCHAR(80),
    source_id VARCHAR(120),
    country_id UUID NULL REFERENCES countries(id),
    name VARCHAR(160) NOT NULL,
    display_name VARCHAR(120),
    age INTEGER NOT NULL CHECK (age BETWEEN 14 AND 60),
    birth_date DATE NULL,
    position VARCHAR(20) NOT NULL,
    dominant_foot VARCHAR(20) NOT NULL DEFAULT 'RIGHT',
    shirt_number INTEGER NULL CHECK (shirt_number IS NULL OR shirt_number BETWEEN 1 AND 99),
    attack INTEGER NOT NULL DEFAULT 50 CHECK (attack BETWEEN 1 AND 99),
    defense INTEGER NOT NULL DEFAULT 50 CHECK (defense BETWEEN 1 AND 99),
    technique INTEGER NOT NULL DEFAULT 50 CHECK (technique BETWEEN 1 AND 99),
    speed INTEGER NOT NULL DEFAULT 50 CHECK (speed BETWEEN 1 AND 99),
    stamina INTEGER NOT NULL DEFAULT 50 CHECK (stamina BETWEEN 1 AND 99),
    mentality INTEGER NOT NULL DEFAULT 50 CHECK (mentality BETWEEN 1 AND 99),
    market_value NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (market_value >= 0),
    weekly_salary NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (weekly_salary >= 0),
    energy INTEGER NOT NULL DEFAULT 100 CHECK (energy BETWEEN 0 AND 100),
    injured BOOLEAN NOT NULL DEFAULT FALSE,
    height_cm INTEGER NULL CHECK (height_cm IS NULL OR height_cm BETWEEN 160 AND 210),
    weight_kg INTEGER NULL CHECK (weight_kg IS NULL OR weight_kg BETWEEN 45 AND 130),
    skill_levels_json TEXT NULL,
    contract_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_players_source UNIQUE (source_system, source_id)
);

CREATE TABLE player_secondary_positions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    position VARCHAR(20) NOT NULL,
    CONSTRAINT uq_player_secondary_position UNIQUE (player_id, position)
);

CREATE TABLE player_attribute_catalog (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    min_value INTEGER NOT NULL DEFAULT 1,
    max_value INTEGER NOT NULL DEFAULT 99,
    default_value INTEGER NOT NULL DEFAULT 50,
    description TEXT,
    CONSTRAINT ck_attribute_range CHECK (
        min_value <= default_value
        AND default_value <= max_value
        AND min_value >= 0
        AND max_value <= 100
    )
);

INSERT INTO player_attribute_catalog (code, name, description) VALUES
    ('attack', 'Attack', 'Chance creation and finishing contribution.'),
    ('defense', 'Defense', 'Marking, tackling and defensive reliability.'),
    ('technique', 'Technique', 'Ball control, passing and execution quality.'),
    ('speed', 'Speed', 'Acceleration, pace and mobility.'),
    ('stamina', 'Stamina', 'Physical endurance and fatigue resistance.'),
    ('mentality', 'Mentality', 'Composure, concentration and pressure handling.');

CREATE TABLE special_attributes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO special_attributes (code, name, description) VALUES
    ('clutch_finisher', 'Clutch finisher', 'Improves late high-pressure finishing.'),
    ('press_resistant', 'Press resistant', 'Keeps technique under pressure.'),
    ('aerial_specialist', 'Aerial specialist', 'Strong influence in aerial duels.'),
    ('line_breaker', 'Line breaker', 'Finds vertical passes and runs.'),
    ('leader', 'Leader', 'Improves collective mentality stability.'),
    ('workhorse', 'Workhorse', 'Sustains intensity and recovery actions.');

CREATE TABLE player_special_attributes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    special_attribute_id UUID NOT NULL REFERENCES special_attributes(id),
    slot SMALLINT NOT NULL CHECK (slot IN (1, 2)),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_player_special_attribute UNIQUE (player_id, special_attribute_id),
    CONSTRAINT uq_player_special_attribute_slot UNIQUE (player_id, slot)
);

CREATE TABLE team_squad (
    id BIGSERIAL PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_team_squad UNIQUE (team_id, player_id)
);

CREATE TABLE league_teams (
    league_id UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    PRIMARY KEY (league_id, team_id)
);

CREATE TABLE games (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id UUID NULL REFERENCES teams(id),
    name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NULL REFERENCES games(id) ON DELETE CASCADE,
    home_team_id UUID NOT NULL REFERENCES teams(id),
    away_team_id UUID NOT NULL REFERENCES teams(id),
    round INTEGER NULL CHECK (round IS NULL OR round > 0),
    scheduled_at TIMESTAMP NOT NULL DEFAULT NOW(),
    status VARCHAR(40) NOT NULL DEFAULT 'SCHEDULED',
    home_goals INTEGER NULL CHECK (home_goals IS NULL OR home_goals >= 0),
    away_goals INTEGER NULL CHECK (away_goals IS NULL OR away_goals >= 0),
    home_possession INTEGER NULL CHECK (home_possession IS NULL OR home_possession BETWEEN 0 AND 100),
    away_possession INTEGER NULL CHECK (away_possession IS NULL OR away_possession BETWEEN 0 AND 100),
    home_shots INTEGER NULL CHECK (home_shots IS NULL OR home_shots >= 0),
    away_shots INTEGER NULL CHECK (away_shots IS NULL OR away_shots >= 0),
    summary TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    simulated_at TIMESTAMP NULL
);

CREATE TABLE match_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    event_type VARCHAR(60) NOT NULL,
    minute INTEGER NOT NULL CHECK (minute BETWEEN 0 AND 130),
    player_name VARCHAR(160) NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT ''
);

CREATE TABLE standings (
    id SERIAL PRIMARY KEY,
    season_id INTEGER NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    played INTEGER NOT NULL DEFAULT 0 CHECK (played >= 0),
    won INTEGER NOT NULL DEFAULT 0 CHECK (won >= 0),
    drawn INTEGER NOT NULL DEFAULT 0 CHECK (drawn >= 0),
    lost INTEGER NOT NULL DEFAULT 0 CHECK (lost >= 0),
    wins INTEGER NULL CHECK (wins IS NULL OR wins >= 0),
    draws INTEGER NULL CHECK (draws IS NULL OR draws >= 0),
    losses INTEGER NULL CHECK (losses IS NULL OR losses >= 0),
    goals_for INTEGER NOT NULL DEFAULT 0 CHECK (goals_for >= 0),
    goals_against INTEGER NOT NULL DEFAULT 0 CHECK (goals_against >= 0),
    goal_difference INTEGER NULL,
    points INTEGER NULL CHECK (points IS NULL OR points >= 0),
    CONSTRAINT uq_standings_season_team UNIQUE (season_id, team_id)
);

CREATE TABLE contracts (
    id SERIAL PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    weekly_salary NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (weekly_salary >= 0),
    duration_years INTEGER NOT NULL DEFAULT 1 CHECK (duration_years BETWEEN 1 AND 10),
    start_date TIMESTAMP NOT NULL DEFAULT NOW(),
    end_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE transfers (
    id VARCHAR(80) PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id),
    from_team_id UUID NOT NULL REFERENCES teams(id),
    to_team_id UUID NOT NULL REFERENCES teams(id),
    offer_amount NUMERIC(15, 2) NOT NULL CHECK (offer_amount >= 0),
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP NULL
);

CREATE TABLE player_match_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    minutes_played INTEGER NOT NULL DEFAULT 0 CHECK (minutes_played BETWEEN 0 AND 130),
    goals INTEGER NOT NULL DEFAULT 0 CHECK (goals >= 0),
    assists INTEGER NOT NULL DEFAULT 0 CHECK (assists >= 0),
    shots INTEGER NOT NULL DEFAULT 0 CHECK (shots >= 0),
    rating NUMERIC(4, 2) NULL CHECK (rating IS NULL OR rating BETWEEN 0 AND 10),
    CONSTRAINT uq_player_match_statistics UNIQUE (match_id, player_id)
);

CREATE TABLE player_season_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id INTEGER NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    appearances INTEGER NOT NULL DEFAULT 0 CHECK (appearances >= 0),
    goals INTEGER NOT NULL DEFAULT 0 CHECK (goals >= 0),
    assists INTEGER NOT NULL DEFAULT 0 CHECK (assists >= 0),
    average_rating NUMERIC(4, 2) NULL CHECK (average_rating IS NULL OR average_rating BETWEEN 0 AND 10),
    CONSTRAINT uq_player_season_statistics UNIQUE (season_id, player_id, team_id)
);

CREATE INDEX idx_countries_code ON countries(code);
CREATE INDEX idx_leagues_country_id ON leagues(country_id);
CREATE INDEX idx_leagues_code ON leagues(code);
CREATE INDEX idx_divisions_league_id ON divisions(league_id);
CREATE INDEX idx_clubs_country_id ON clubs(country_id);
CREATE INDEX idx_teams_manager_id ON teams(manager_id);
CREATE INDEX idx_teams_league_id ON teams(league_id);
CREATE INDEX idx_teams_division ON teams(division);
CREATE INDEX idx_players_country_id ON players(country_id);
CREATE INDEX idx_players_name ON players(name);
CREATE INDEX idx_players_position ON players(position);
CREATE INDEX idx_team_squad_team_id ON team_squad(team_id);
CREATE INDEX idx_team_squad_player_id ON team_squad(player_id);
CREATE INDEX idx_matches_game_round ON matches(game_id, round);
CREATE INDEX idx_matches_home_team ON matches(home_team_id);
CREATE INDEX idx_matches_away_team ON matches(away_team_id);
CREATE INDEX idx_match_events_match_id ON match_events(match_id);
CREATE INDEX idx_standings_season_team ON standings(season_id, team_id);
CREATE INDEX idx_player_special_attributes_player ON player_special_attributes(player_id);
