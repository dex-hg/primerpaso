-- Guarda la lectura individual sin alterar los avisos de contratación existentes.
BEGIN;

CREATE TABLE IF NOT EXISTS lecturas_notificaciones (
    id_aviso BIGINT NOT NULL REFERENCES avisos_contratacion(id_aviso) ON DELETE CASCADE,
    id_usuario BIGINT NOT NULL REFERENCES users(id_user) ON DELETE CASCADE,
    leida_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (id_aviso, id_usuario)
);

COMMIT;
