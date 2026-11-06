\set ON_ERROR_STOP on

-- Para una instalación existente que ya usa el esquema privado y el rol de Spring.
SET search_path TO primerpaso, pg_temp;
\ir ../migraciones/V004__limites_inicio_sesion.sql

BEGIN;
ALTER TABLE primerpaso.limites_inicio_sesion ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON primerpaso.limites_inicio_sesion FROM PUBLIC, anon, authenticated, service_role, authenticator;
GRANT SELECT, INSERT, UPDATE, DELETE ON primerpaso.limites_inicio_sesion TO primerpaso_app;
DROP POLICY IF EXISTS acceso_servidor ON primerpaso.limites_inicio_sesion;
CREATE POLICY acceso_servidor ON primerpaso.limites_inicio_sesion TO primerpaso_app
    USING (true) WITH CHECK (true);
COMMIT;
