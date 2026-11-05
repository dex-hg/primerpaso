-- PrimerPaso: esquema propuesto para PostgreSQL 15+.
-- El núcleo del MVP llega hasta job_skills. Las tablas posteriores son previstas.

BEGIN;

CREATE TABLE users (
    id_user BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email_user VARCHAR(254) NOT NULL UNIQUE,
    password_hash_user VARCHAR(255) NOT NULL,
    status_user VARCHAR(12) NOT NULL DEFAULT 'activo'
        CHECK (status_user IN ('activo', 'suspendido')),
    terms_accepted_at_user TIMESTAMPTZ NOT NULL,
    terms_version_user VARCHAR(32) NOT NULL,
    created_at_user TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at_user TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_email_normalized
        CHECK (email_user = lower(btrim(email_user)) AND position('@' IN email_user) > 1),
    CONSTRAINT ck_users_password_hash_present
        CHECK (length(btrim(password_hash_user)) > 0)
);

CREATE TABLE careers (
    id_career BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_career VARCHAR(160) NOT NULL,
    active_career BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_careers_name_present CHECK (length(btrim(name_career)) > 0)
);
CREATE UNIQUE INDEX uq_careers_name_normalized ON careers (lower(btrim(name_career)));

CREATE TABLE skills (
    id_skill BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_skill VARCHAR(100) NOT NULL,
    active_skill BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_skills_name_present CHECK (length(btrim(name_skill)) > 0)
);
CREATE UNIQUE INDEX uq_skills_name_normalized ON skills (lower(btrim(name_skill)));

CREATE TABLE work_areas (
    id_work_area BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_work_area VARCHAR(100) NOT NULL,
    active_work_area BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_work_areas_name_present CHECK (length(btrim(name_work_area)) > 0)
);
CREATE UNIQUE INDEX uq_work_areas_name_normalized
    ON work_areas (lower(btrim(name_work_area)));

CREATE TABLE companies (
    id_company BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trade_name_company VARCHAR(180) NOT NULL,
    country_code_company CHAR(2) NOT NULL DEFAULT 'PE',
    tax_id_company VARCHAR(32) NOT NULL,
    industry_company VARCHAR(100) NOT NULL,
    city_company VARCHAR(120) NOT NULL,
    website_company TEXT,
    description_company TEXT,
    active_company BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_company TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_companies_tax_id UNIQUE (country_code_company, tax_id_company),
    CONSTRAINT ck_companies_trade_name_present
        CHECK (length(btrim(trade_name_company)) > 0),
    CONSTRAINT ck_companies_country_code
        CHECK (country_code_company ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_companies_tax_id CHECK (
        length(btrim(tax_id_company)) > 0
        AND tax_id_company = upper(btrim(tax_id_company))
        AND (country_code_company <> 'PE' OR tax_id_company ~ '^[0-9]{11}$')
    ),
    CONSTRAINT ck_companies_industry_present
        CHECK (length(btrim(industry_company)) > 0),
    CONSTRAINT ck_companies_city_present
        CHECK (length(btrim(city_company)) > 0),
    CONSTRAINT ck_companies_website
        CHECK (website_company IS NULL OR website_company ~* '^https?://[^[:space:]]+$')
);

CREATE TABLE candidates (
    id_candidate BIGINT PRIMARY KEY REFERENCES users(id_user) ON DELETE RESTRICT,
    first_name_candidate VARCHAR(120) NOT NULL,
    last_name_candidate VARCHAR(120) NOT NULL,
    phone_candidate VARCHAR(30),
    city_candidate VARCHAR(120),
    country_code_candidate CHAR(2),
    institution_candidate VARCHAR(180) NOT NULL,
    career_id_candidate BIGINT NOT NULL REFERENCES careers(id_career) ON DELETE RESTRICT,
    academic_status_candidate VARCHAR(12) NOT NULL
        CHECK (academic_status_candidate IN ('estudiante', 'egresado', 'titulado')),
    current_term_candidate SMALLINT,
    graduation_year_candidate SMALLINT,
    professional_summary_candidate TEXT,
    github_url_candidate TEXT,
    linkedin_url_candidate TEXT,
    marketing_opt_in_candidate BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at_candidate TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_candidates_first_name_present
        CHECK (length(btrim(first_name_candidate)) > 0),
    CONSTRAINT ck_candidates_last_name_present
        CHECK (length(btrim(last_name_candidate)) > 0),
    CONSTRAINT ck_candidates_institution_present
        CHECK (length(btrim(institution_candidate)) > 0),
    CONSTRAINT ck_candidates_country_code
        CHECK (country_code_candidate IS NULL OR country_code_candidate ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_candidates_academic_details CHECK (
        (academic_status_candidate = 'estudiante'
         AND current_term_candidate IS NOT NULL
         AND current_term_candidate BETWEEN 1 AND 30
         AND graduation_year_candidate IS NULL)
        OR
        (academic_status_candidate IN ('egresado', 'titulado')
         AND current_term_candidate IS NULL
         AND graduation_year_candidate IS NOT NULL
         AND graduation_year_candidate BETWEEN 1950 AND 2100)
    )
);
CREATE INDEX idx_candidates_career ON candidates (career_id_candidate);

CREATE TABLE company_members (
    company_id_company_member BIGINT NOT NULL
        REFERENCES companies(id_company) ON DELETE RESTRICT,
    user_id_company_member BIGINT NOT NULL
        REFERENCES users(id_user) ON DELETE RESTRICT,
    role_company_member VARCHAR(13) NOT NULL
        CHECK (role_company_member IN ('administrador', 'reclutador')),
    first_name_company_member VARCHAR(120)
        CONSTRAINT ck_company_members_first_name CHECK (
            first_name_company_member IS NULL OR length(btrim(first_name_company_member)) > 0),
    last_name_company_member VARCHAR(120)
        CONSTRAINT ck_company_members_last_name CHECK (
            last_name_company_member IS NULL OR length(btrim(last_name_company_member)) > 0),
    phone_company_member VARCHAR(30)
        CONSTRAINT ck_company_members_phone CHECK (
            phone_company_member IS NULL OR length(btrim(phone_company_member)) > 0),
    active_company_member BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at_company_member TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id_company_member, user_id_company_member)
);
CREATE INDEX idx_company_members_user ON company_members (user_id_company_member);

CREATE TABLE candidate_skills (
    candidate_id_candidate_skill BIGINT NOT NULL
        REFERENCES candidates(id_candidate) ON DELETE CASCADE,
    skill_id_candidate_skill BIGINT NOT NULL
        REFERENCES skills(id_skill) ON DELETE RESTRICT,
    PRIMARY KEY (candidate_id_candidate_skill, skill_id_candidate_skill)
);
CREATE INDEX idx_candidate_skills_skill ON candidate_skills (skill_id_candidate_skill);

CREATE TABLE candidate_work_areas (
    candidate_id_candidate_work_area BIGINT NOT NULL
        REFERENCES candidates(id_candidate) ON DELETE CASCADE,
    work_area_id_candidate_work_area BIGINT NOT NULL
        REFERENCES work_areas(id_work_area) ON DELETE RESTRICT,
    PRIMARY KEY (candidate_id_candidate_work_area, work_area_id_candidate_work_area)
);
CREATE INDEX idx_candidate_work_areas_area
    ON candidate_work_areas (work_area_id_candidate_work_area);

CREATE TABLE company_work_areas (
    company_id_company_work_area BIGINT NOT NULL
        REFERENCES companies(id_company) ON DELETE CASCADE,
    work_area_id_company_work_area BIGINT NOT NULL
        REFERENCES work_areas(id_work_area) ON DELETE RESTRICT,
    PRIMARY KEY (company_id_company_work_area, work_area_id_company_work_area)
);
CREATE INDEX idx_company_work_areas_area
    ON company_work_areas (work_area_id_company_work_area);

CREATE TABLE jobs (
    id_job BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id_job BIGINT NOT NULL,
    creator_user_id_job BIGINT NOT NULL,
    career_id_job BIGINT NOT NULL REFERENCES careers(id_career) ON DELETE RESTRICT,
    work_area_id_job BIGINT REFERENCES work_areas(id_work_area) ON DELETE RESTRICT,
    title_job VARCHAR(180) NOT NULL,
    description_job TEXT NOT NULL,
    work_mode_job VARCHAR(10) NOT NULL
        CHECK (work_mode_job IN ('presencial', 'remota', 'hibrida')),
    contract_type_job VARCHAR(80),
    city_job VARCHAR(120),
    country_code_job CHAR(2),
    salary_min_job NUMERIC(12,2),
    salary_max_job NUMERIC(12,2),
    currency_job CHAR(3),
    status_job VARCHAR(10) NOT NULL DEFAULT 'borrador'
        CHECK (status_job IN ('borrador', 'publicada', 'cerrada')),
    created_at_job TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at_job TIMESTAMPTZ,
    expires_at_job TIMESTAMPTZ,
    updated_at_job TIMESTAMPTZ NOT NULL DEFAULT now(),
    plazas SMALLINT NOT NULL DEFAULT 1 CHECK (plazas BETWEEN 1 AND 1000),
    motivo_fin_proceso VARCHAR(20),
    detalle_cancelacion VARCHAR(500),
    fecha_fin_proceso TIMESTAMPTZ,
    id_responsable_fin_proceso BIGINT REFERENCES users(id_user) ON DELETE RESTRICT,
    CONSTRAINT fk_jobs_creator_membership
        FOREIGN KEY (company_id_job, creator_user_id_job)
        REFERENCES company_members(company_id_company_member, user_id_company_member)
        ON DELETE RESTRICT,
    CONSTRAINT ck_jobs_title_present CHECK (length(btrim(title_job)) > 0),
    CONSTRAINT ck_jobs_description_present CHECK (length(btrim(description_job)) > 0),
    CONSTRAINT ck_jobs_country_code
        CHECK (country_code_job IS NULL OR country_code_job ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_jobs_salary CHECK (
        (salary_min_job IS NULL OR salary_min_job >= 0)
        AND (salary_max_job IS NULL OR salary_max_job >= 0)
        AND (salary_min_job IS NULL OR salary_max_job IS NULL
             OR salary_min_job <= salary_max_job)
        AND ((salary_min_job IS NULL AND salary_max_job IS NULL)
             OR (currency_job IS NOT NULL AND currency_job ~ '^[A-Z]{3}$'))
    ),
    CONSTRAINT ck_jobs_publication CHECK (
        (status_job = 'borrador' AND published_at_job IS NULL)
        OR (status_job IN ('publicada', 'cerrada') AND published_at_job IS NOT NULL
            AND published_at_job >= created_at_job)
    ),
    CONSTRAINT ck_jobs_fin_proceso CHECK (
        (motivo_fin_proceso IS NULL AND detalle_cancelacion IS NULL AND fecha_fin_proceso IS NULL
            AND id_responsable_fin_proceso IS NULL)
        OR (motivo_fin_proceso IS NOT NULL AND status_job = 'cerrada' AND fecha_fin_proceso IS NOT NULL
            AND id_responsable_fin_proceso IS NOT NULL
            AND ((motivo_fin_proceso = 'vacante_cubierta' AND detalle_cancelacion IS NULL)
                OR (motivo_fin_proceso = 'proceso_cancelado' AND detalle_cancelacion IS NOT NULL
                    AND length(btrim(detalle_cancelacion)) > 0)))
    ),
    CONSTRAINT ck_jobs_expiration CHECK (
        expires_at_job IS NULL OR published_at_job IS NULL
        OR expires_at_job > published_at_job
    )
);
CREATE INDEX idx_jobs_search ON jobs (career_id_job, published_at_job DESC)
    WHERE status_job = 'publicada';
CREATE INDEX idx_jobs_company ON jobs (company_id_job, created_at_job DESC);
CREATE INDEX idx_jobs_work_area ON jobs (work_area_id_job)
    WHERE status_job = 'publicada';

CREATE TABLE job_skills (
    job_id_job_skill BIGINT NOT NULL REFERENCES jobs(id_job) ON DELETE CASCADE,
    skill_id_job_skill BIGINT NOT NULL REFERENCES skills(id_skill) ON DELETE RESTRICT,
    required_job_skill BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (job_id_job_skill, skill_id_job_skill)
);
CREATE INDEX idx_job_skills_skill ON job_skills (skill_id_job_skill, job_id_job_skill);

-- Ampliaciones previstas: no forman parte de los flujos obligatorios del MVP.
CREATE TABLE resumes (
    id_resume BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id_resume BIGINT NOT NULL
        REFERENCES candidates(id_candidate) ON DELETE RESTRICT,
    storage_key_resume TEXT NOT NULL UNIQUE,
    original_name_resume VARCHAR(255) NOT NULL,
    mime_type_resume VARCHAR(100) NOT NULL CHECK (mime_type_resume IN (
        'application/pdf', 'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    )),
    size_bytes_resume BIGINT NOT NULL CHECK (size_bytes_resume > 0),
    uploaded_at_resume TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_resumes_owner UNIQUE (candidate_id_resume, id_resume)
);

CREATE TABLE projects (
    id_project BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id_project BIGINT NOT NULL
        REFERENCES candidates(id_candidate) ON DELETE RESTRICT,
    title_project VARCHAR(180) NOT NULL,
    description_project TEXT NOT NULL,
    url_project TEXT,
    created_at_project TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_projects_title_present CHECK (length(btrim(title_project)) > 0),
    CONSTRAINT ck_projects_description_present
        CHECK (length(btrim(description_project)) > 0),
    CONSTRAINT ck_projects_url
        CHECK (url_project IS NULL OR url_project ~* '^https?://[^[:space:]]+$')
);
CREATE INDEX idx_projects_candidate ON projects (candidate_id_project);

CREATE TABLE applications (
    id_application BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id_application BIGINT NOT NULL REFERENCES jobs(id_job) ON DELETE RESTRICT,
    candidate_id_application BIGINT NOT NULL
        REFERENCES candidates(id_candidate) ON DELETE RESTRICT,
    resume_id_application BIGINT,
    cover_letter_application TEXT,
    status_application VARCHAR(12) NOT NULL DEFAULT 'enviada'
        CONSTRAINT ck_applications_estado CHECK (status_application IN (
            'enviada', 'en_revision', 'seleccionada', 'descartada', 'retirada', 'contratada', 'finalizada'
        )),
    submitted_at_application TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at_application TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_applications_job_candidate
        UNIQUE (job_id_application, candidate_id_application),
    CONSTRAINT fk_applications_owned_resume
        FOREIGN KEY (candidate_id_application, resume_id_application)
        REFERENCES resumes(candidate_id_resume, id_resume) ON DELETE RESTRICT
);
CREATE INDEX idx_applications_candidate
    ON applications (candidate_id_application, submitted_at_application DESC);
CREATE INDEX idx_applications_job_status
    ON applications (job_id_application, status_application);

-- Cada postulación identifica una conversación; se inicia con el primer mensaje.
CREATE TABLE mensajes_chat (
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
CREATE INDEX idx_mensajes_chat_historial ON mensajes_chat (id_postulacion, id_mensaje DESC);
CREATE INDEX idx_mensajes_chat_frecuencia ON mensajes_chat (id_postulacion, id_emisor, fecha_envio DESC);

-- Cada envío conserva las condiciones que vio el postulante.
CREATE TABLE ofertas_contratacion (
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
CREATE UNIQUE INDEX uq_ofertas_reserva ON ofertas_contratacion (id_postulacion)
    WHERE estado IN ('pendiente', 'aceptada');
CREATE UNIQUE INDEX uq_ofertas_contratada ON ofertas_contratacion (id_postulacion)
    WHERE estado = 'contratada';
CREATE INDEX idx_ofertas_historial ON ofertas_contratacion (id_postulacion, id_oferta DESC);

CREATE TABLE avisos_contratacion (
    id_aviso BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_postulacion BIGINT NOT NULL REFERENCES applications(id_application) ON DELETE RESTRICT,
    destinatario VARCHAR(10) NOT NULL CHECK (destinatario IN ('postulante', 'empresa')),
    tipo VARCHAR(24) NOT NULL CHECK (tipo IN (
        'oferta_enviada', 'oferta_aceptada', 'oferta_rechazada', 'oferta_retirada',
        'contratacion_confirmada', 'vacante_cubierta', 'proceso_cancelado', 'postulacion_retirada'
    )),
    fecha_aviso TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_avisos_postulacion ON avisos_contratacion (id_postulacion, destinatario, id_aviso DESC);

COMMIT;

-- Reglas del backend: crear cuenta y perfil/membresía en una transacción;
-- comprobar membresía activa antes de publicar o editar una vacante;
-- aceptar postulaciones solo para vacantes publicadas y no vencidas;
-- exigir al menos una habilidad antes de publicar;
-- validar archivos por contenido y tamaño, y guardar solo su clave segura;
-- actualizar las fechas de modificación y aplicar baja lógica a cuentas con historial.
-- El registro resuelve id_career y guarda el ciclo o el año de egreso,
-- según la condición académica del postulante.
-- La primera vacante empresarial se habilitará cuando tenga carrera,
-- modalidad y descripción estructurada antes de persistirse como job.
