-- V25D78-C55.3 B1: Add league_id column to teams table for per-league division distribution.
--
-- Background: C55.2 phase 1 migration (V25D78) assigned division globally
-- (alphabetical across ALL teams). C55.3 B1 needs per-league distribution
-- (top 20 = PRIMERA, mid 20 = SEGUNDA, bottom 20 = TERCERA within each league).
--
-- The team-to-league relationship is currently in Redis (WorldTeam.realLeagueId
-- + league_teams Redis set). Postgres teams table doesn't have league_id,
-- so per-league SQL distribution was impossible.
--
-- This migration:
-- 1. Adds nullable league_id column (existing rows have NULL — C55.2 phase 1
--    assigned their division globally; that stays as-is).
-- 2. Creates an index on league_id for fast per-league queries.
--
-- V25D80__per_league_division.sql (next migration) will:
-- - Redistribute division per-league based on alphabetical name within league
-- - Only affect rows where league_id IS NOT NULL (the new B1-seeded teams).
-- - Rows with NULL league_id keep their global division (legacy from C55.2).
--
-- The seeder (WorldSeedService.applySeed) populates this column when inserting
-- new teams via UPSERT.

ALTER TABLE teams
    ADD COLUMN league_id UUID NULL;

CREATE INDEX idx_teams_league_id ON teams(league_id);