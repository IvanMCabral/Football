-- Add league_id column to teams table for per-league division distribution.

ALTER TABLE teams
    ADD COLUMN IF NOT EXISTS league_id UUID NULL;

CREATE INDEX IF NOT EXISTS idx_teams_league_id ON teams(league_id);
