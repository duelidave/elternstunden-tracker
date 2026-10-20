-- Grants for the app role (OPS-14). Flyway runs as the owner role, the application
-- connects as ${appRole}. The role itself is created by ops/db/init/01-create-app-role.sh.

GRANT USAGE ON SCHEMA public TO ${appRole};

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${appRole};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${appRole};

-- SEC-11: the audit log is append-only for the application (no UPDATE, DELETE or TRUNCATE)
REVOKE UPDATE, DELETE ON audit_log FROM ${appRole};

-- The migration history is none of the application's business
REVOKE ALL ON flyway_schema_history FROM ${appRole};

-- Tables and sequences from later migrations get the same DML rights automatically.
-- Append-only tables added later must revoke UPDATE and DELETE explicitly, as above.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${appRole};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO ${appRole};
