-- Contadores temporales, separados de las cuentas y sin correos en claro.
BEGIN;

CREATE TABLE IF NOT EXISTS limites_inicio_sesion (
    clave BYTEA PRIMARY KEY CHECK (octet_length(clave) = 32),
    intentos SMALLINT NOT NULL CHECK (intentos >= 0),
    vence_en TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_limites_inicio_sesion_vence ON limites_inicio_sesion (vence_en);

COMMIT;
