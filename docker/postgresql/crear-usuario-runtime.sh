#!/bin/sh
set -eu

psql \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --set=nombre_base="$POSTGRES_DB" \
    --set=usuario_runtime="$TRIAJE_DB_USUARIO" \
    --set=contrasena_runtime="$TRIAJE_DB_PASSWORD" <<'SQL'
CREATE ROLE :"usuario_runtime" LOGIN PASSWORD :'contrasena_runtime';
GRANT CONNECT ON DATABASE :"nombre_base" TO :"usuario_runtime";
SQL
