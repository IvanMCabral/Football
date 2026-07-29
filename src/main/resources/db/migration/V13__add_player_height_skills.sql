-- Player entity persistence: height and skills metadata.
--
-- The columns are nullable so existing players keep working. Domain validation
-- owns value ranges and the engine applies defaults when these values are null.

ALTER TABLE players
    ADD COLUMN IF NOT EXISTS height_cm INTEGER NULL;

ALTER TABLE players
    ADD COLUMN IF NOT EXISTS skill_levels_json TEXT NULL;
