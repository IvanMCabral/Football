-- V25D78-C55.2 phase 1: Add division column to teams table (3-tier Primera/Segunda/Tercera).
--
-- Spec: C55.2 multi-división 3-tier with promotion/relegation.
-- Per league: 20 equipos PRIMERA + 20 SEGUNDA + 20 TERCERA = 60 equipos total.
--
-- Phase 1 only adds the column + global initial distribution. Phase 2 will
-- add a proper league_id FK on teams + per-league distribution.
--
-- Column details:
--   division VARCHAR(20) NOT NULL DEFAULT 'PRIMERA'
--   - VARCHAR (not enum) for forward-compat: we can add new divisions
--     (CUARTA, etc.) without ALTER TYPE.
--   - NOT NULL + DEFAULT 'PRIMERA': existing rows automatically get PRIMERA.
--   - 20 chars is enough for "PRIMERA" (7 chars) with headroom.
--
-- Initial distribution (idempotent):
--   Global alphabetical split into 3 tiers (phase-1 placeholder).
--   Phase 2 will refine to per-league distribution once league_id FK
--   is added to teams.
--
-- Rationale for global split:
--   Without league_id on teams, we cannot do per-league distribution
--   in this migration. A global split gives all teams a starting
--   tier so the schedule generator (phase 2) has something to work
--   with. The redistribution in phase 2 will be the canonical
--   per-league split.

ALTER TABLE teams
    ADD COLUMN division VARCHAR(20) NOT NULL DEFAULT 'PRIMERA';

-- Initial global distribution (idempotent): split by alphabetical name.
-- Top N/3 = PRIMERA, next N/3 = SEGUNDA, bottom N/3 = TERCERA.
WITH ranked AS (
    SELECT id,
        ROW_NUMBER() OVER (ORDER BY name) AS rn,
        COUNT(*) OVER () AS total
    FROM teams
),
tiers AS (
    SELECT id,
        CASE
            WHEN rn <= total / 3 THEN 'PRIMERA'
            WHEN rn <= (total * 2) / 3 THEN 'SEGUNDA'
            ELSE 'TERCERA'
        END AS new_division
    FROM ranked
)
UPDATE teams SET division = tiers.new_division
FROM tiers
WHERE teams.id = tiers.id;

-- Index for schedule generator queries that filter by division.
-- Phase 2 will extend to (league_id, division) once league_id FK is added.
CREATE INDEX idx_teams_division ON teams(division);