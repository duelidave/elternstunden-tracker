#!/bin/sh
# Creates the application login role (OPS-14). The postgres image runs this once on an empty
# data volume (/docker-entrypoint-initdb.d). For an existing volume run it once by hand.
# Idempotent: an existing role is left untouched.
#
# Password source: DB_APP_PASSWORD_FILE (Docker secret) or DB_APP_PASSWORD (local dev only).
set -eu

if [ -n "${DB_APP_PASSWORD_FILE:-}" ]; then
  DB_APP_PASSWORD="$(cat "$DB_APP_PASSWORD_FILE")"
fi
: "${DB_APP_PASSWORD:?DB_APP_PASSWORD or DB_APP_PASSWORD_FILE must be set}"
DB_APP_ROLE="${DB_APP_ROLE:-elternstunden_app}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_role="$DB_APP_ROLE" -v app_password="$DB_APP_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app_role', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_role')
\gexec
SQL
