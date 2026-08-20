-- A team can have at most one user owner. PostgreSQL UNIQUE semantics allow
-- any number of rows whose team_id is NULL, preserving unassigned users.
-- Existing duplicates make this migration fail without modifying ownership.
ALTER TABLE users
    ADD CONSTRAINT uk_users_team_id_single_owner UNIQUE (team_id);
