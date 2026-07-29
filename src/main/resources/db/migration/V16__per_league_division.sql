-- Redistribute divisions independently within each league.

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
