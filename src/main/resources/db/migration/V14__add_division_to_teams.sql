-- Add division column to teams table for the three-tier league pyramid.

ALTER TABLE teams
    ADD COLUMN IF NOT EXISTS division VARCHAR(20) NOT NULL DEFAULT 'PRIMERA';

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

CREATE INDEX IF NOT EXISTS idx_teams_division ON teams(division);
