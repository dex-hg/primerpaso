-- Ampliación aditiva para bases existentes. No ejecutar de nuevo el esquema base.
BEGIN;

CREATE TABLE IF NOT EXISTS mensajes_chat (
    id_mensaje BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_postulacion BIGINT NOT NULL REFERENCES applications(id_application) ON DELETE RESTRICT,
    id_emisor BIGINT NOT NULL REFERENCES users(id_user) ON DELETE RESTRICT,
    id_envio UUID NOT NULL,
    nombre_emisor VARCHAR(241) NOT NULL CHECK (length(btrim(nombre_emisor)) > 0),
    tipo_emisor VARCHAR(10) NOT NULL CHECK (tipo_emisor IN ('empresa', 'postulante')),
    contenido TEXT NOT NULL CHECK (length(btrim(contenido)) BETWEEN 1 AND 4000),
    fecha_envio TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_mensajes_chat_envio UNIQUE (id_postulacion, id_emisor, id_envio)
);

CREATE INDEX IF NOT EXISTS idx_mensajes_chat_historial
    ON mensajes_chat (id_postulacion, id_mensaje DESC);
CREATE INDEX IF NOT EXISTS idx_mensajes_chat_frecuencia
    ON mensajes_chat (id_postulacion, id_emisor, fecha_envio DESC);

COMMIT;
