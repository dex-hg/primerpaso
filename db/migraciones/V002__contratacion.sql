-- Ampliación de selección, ofertas y contratación. Conserva las vacantes y postulaciones existentes.
BEGIN;

ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS plazas SMALLINT NOT NULL DEFAULT 1 CHECK (plazas BETWEEN 1 AND 1000),
    ADD COLUMN IF NOT EXISTS motivo_fin_proceso VARCHAR(20),
    ADD COLUMN IF NOT EXISTS detalle_cancelacion VARCHAR(500),
    ADD COLUMN IF NOT EXISTS fecha_fin_proceso TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS id_responsable_fin_proceso BIGINT REFERENCES users(id_user) ON DELETE RESTRICT;
ALTER TABLE jobs DROP CONSTRAINT IF EXISTS ck_jobs_fin_proceso;
ALTER TABLE jobs ADD CONSTRAINT ck_jobs_fin_proceso CHECK (
        (motivo_fin_proceso IS NULL AND detalle_cancelacion IS NULL AND fecha_fin_proceso IS NULL
            AND id_responsable_fin_proceso IS NULL)
        OR (motivo_fin_proceso IS NOT NULL AND status_job = 'cerrada' AND fecha_fin_proceso IS NOT NULL
            AND id_responsable_fin_proceso IS NOT NULL
            AND ((motivo_fin_proceso = 'vacante_cubierta' AND detalle_cancelacion IS NULL)
                OR (motivo_fin_proceso = 'proceso_cancelado' AND detalle_cancelacion IS NOT NULL
                    AND length(btrim(detalle_cancelacion)) > 0)))
    );

ALTER TABLE applications DROP CONSTRAINT IF EXISTS applications_status_application_check;
ALTER TABLE applications DROP CONSTRAINT IF EXISTS ck_applications_estado;
ALTER TABLE applications ADD CONSTRAINT ck_applications_estado CHECK (status_application IN (
            'enviada', 'en_revision', 'seleccionada', 'descartada', 'retirada', 'contratada', 'finalizada'
        ));

-- Cada envío conserva las condiciones que vio el postulante.
CREATE TABLE IF NOT EXISTS ofertas_contratacion (
    id_oferta BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_postulacion BIGINT NOT NULL REFERENCES applications(id_application) ON DELETE RESTRICT,
    id_emisor BIGINT NOT NULL REFERENCES users(id_user) ON DELETE RESTRICT,
    id_envio UUID NOT NULL,
    condiciones VARCHAR(4000) NOT NULL CHECK (length(btrim(condiciones)) > 0),
    fecha_inicio DATE NOT NULL,
    vence_en TIMESTAMPTZ NOT NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'pendiente'
        CHECK (estado IN ('pendiente', 'aceptada', 'rechazada', 'vencida', 'retirada', 'contratada')),
    enviada_en TIMESTAMPTZ NOT NULL,
    respondida_en TIMESTAMPTZ,
    resuelta_en TIMESTAMPTZ,
    id_responsable_resolucion BIGINT REFERENCES users(id_user) ON DELETE RESTRICT,
    CONSTRAINT uq_ofertas_envio UNIQUE (id_postulacion, id_envio),
    CONSTRAINT ck_ofertas_plazo CHECK (vence_en > enviada_en AND fecha_inicio >= (vence_en AT TIME ZONE 'America/Lima')::date),
    CONSTRAINT ck_ofertas_respuesta CHECK (
        (respondida_en IS NULL OR (respondida_en >= enviada_en AND respondida_en < vence_en))
        AND (estado NOT IN ('aceptada', 'rechazada', 'contratada') OR respondida_en IS NOT NULL)
        AND (estado NOT IN ('pendiente', 'vencida') OR respondida_en IS NULL)
    ),
    CONSTRAINT ck_ofertas_resolucion CHECK (
        (estado IN ('pendiente', 'aceptada') AND resuelta_en IS NULL AND id_responsable_resolucion IS NULL)
        OR (estado = 'vencida' AND resuelta_en IS NOT NULL AND resuelta_en = vence_en
            AND id_responsable_resolucion IS NULL)
        OR (estado IN ('rechazada', 'retirada', 'contratada') AND resuelta_en IS NOT NULL
            AND resuelta_en >= COALESCE(respondida_en, enviada_en) AND id_responsable_resolucion IS NOT NULL)
    )
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ofertas_reserva ON ofertas_contratacion (id_postulacion)
    WHERE estado IN ('pendiente', 'aceptada');
CREATE UNIQUE INDEX IF NOT EXISTS uq_ofertas_contratada ON ofertas_contratacion (id_postulacion)
    WHERE estado = 'contratada';
CREATE INDEX IF NOT EXISTS idx_ofertas_historial ON ofertas_contratacion (id_postulacion, id_oferta DESC);

CREATE TABLE IF NOT EXISTS avisos_contratacion (
    id_aviso BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_postulacion BIGINT NOT NULL REFERENCES applications(id_application) ON DELETE RESTRICT,
    destinatario VARCHAR(10) NOT NULL CHECK (destinatario IN ('postulante', 'empresa')),
    tipo VARCHAR(24) NOT NULL CHECK (tipo IN (
        'oferta_enviada', 'oferta_aceptada', 'oferta_rechazada', 'oferta_retirada',
        'contratacion_confirmada', 'vacante_cubierta', 'proceso_cancelado', 'postulacion_retirada'
    )),
    fecha_aviso TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX IF NOT EXISTS idx_avisos_postulacion ON avisos_contratacion (id_postulacion, destinatario, id_aviso DESC);

COMMIT;
