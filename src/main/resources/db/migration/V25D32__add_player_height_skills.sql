-- V25D32-F2: Player entity persistence — height + skills metadata.
--
-- Source of truth: Player directo (NO PlayerAttributes, deprecado en V25D32-F1).
-- Las dos columnas son NULLABLE para backward-compat con filas existentes
-- (players viejos en Postgres sin height/skills). El engine en V25D33 usara
-- defaults si son null/empty.
--
-- Decisiones:
-- - height_cm: INTEGER NULL. Bound [160, 210] validado en domain (Player.setHeightCm).
-- - skill_levels_json: TEXT NULL. JSONB seria ideal pero requiere hypersistence-utils
--   o hibernate-types, los cuales NO estan en pom.xml. Trade-off: serializar con
--   Jackson como String (Map<PlayerSkill, Integer> como JSON object) es portable
--   en R2DBC sin dependencies extra. Engine deserializa on-read.
--   V25D33 puede migrar a JSONB si la latencia de parseo se vuelve issue.
--
-- Backward-compat:
--   Players viejos: height_cm = NULL, skill_levels_json = NULL. OK.
--   PlayerEntity.toDomain() usa el overload 12-args de Player.reconstruct con
--   null/null para backward-compat con data pre-V25D32.

ALTER TABLE players
    ADD COLUMN height_cm INTEGER NULL;

ALTER TABLE players
    ADD COLUMN skill_levels_json TEXT NULL;

-- No constraints on these columns: nullable + validacion en domain.
-- V25D33 puede agregar CHECK (height_cm BETWEEN 160 AND 210) si la calidad
-- de datos lo requiere.
