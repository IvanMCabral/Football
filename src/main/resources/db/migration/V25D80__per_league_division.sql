-- V25D78-C55.3 B1: Per-league division distribution (3-tier).
--
-- Background: After V25D79 adds league_id, this migration redistributes
-- division per league based on alphabetical name within each league.
-- Top N/3 = PRIMERA, mid N/3 = SEGUNDA, bottom N/3 = TERCERA.
--
-- For a 60-team league (target after B1): 20 PRIMERA + 20 SEGUNDA + 20 TERCERA.
-- For smaller leagues (e.g., legacy 20-team): N/3 = 6 PRIMERA + 7 SEGUNDA + 7 TERCERA
-- (since integer division floors). For non-multi-division leagues, this may
-- produce uneven distributions — acceptable for backward-compat with C55.2 data.
--
-- IDEMPOTENT: re-running recomputes divisions. Safe to run multiple times.
--
-- This migration ONLY affects rows with non-NULL league_id (i.e., teams that
-- have been re-seeded with B1-aware seeder). Legacy teams (NULL league_id)
-- keep their global division from V25D78.

WITH ranked AS (
    SELECT t.id, t.league_id,
        ROW_NUMBER() OVER (PARTITION BY t.league_id ORDER BY t.name) AS rn,
        COUNT(*) OVER (PARTITION BY t.league_id) AS total
    FROM teams t
    WHERE t.league_id IS NOT NULL
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