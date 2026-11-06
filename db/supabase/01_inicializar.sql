\set ON_ERROR_STOP on

-- Ejecutar con psql como administrador de un proyecto nuevo.
BEGIN;
DO $$
BEGIN
    IF to_regclass('public.users') IS NOT NULL
        OR to_regclass('public.jobs') IS NOT NULL
        OR to_regclass('public.applications') IS NOT NULL
        OR to_regnamespace('primerpaso') IS NOT NULL
        OR EXISTS (SELECT FROM pg_roles WHERE rolname = 'primerpaso_app') THEN
        RAISE EXCEPTION 'Se encontraron objetos previos de PrimerPaso. Usa un proyecto nuevo o revisa la instalación existente.';
    END IF;
END
$$;
CREATE SCHEMA primerpaso;
REVOKE ALL ON SCHEMA primerpaso FROM PUBLIC, anon, authenticated, service_role, authenticator;
COMMIT;

-- El esquema base administra su propia transacción e incluye V001 a V004.
SET search_path TO primerpaso, pg_temp;
\ir ../primerpaso.sql

BEGIN;
-- El rol queda sin acceso de red hasta asignarle una contraseña y habilitar LOGIN.
CREATE ROLE primerpaso_app NOLOGIN NOINHERIT CONNECTION LIMIT 10;
ALTER ROLE primerpaso_app SET search_path TO primerpaso, pg_temp;

REVOKE ALL ON ALL TABLES IN SCHEMA primerpaso
    FROM PUBLIC, anon, authenticated, service_role, authenticator;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA primerpaso
    FROM PUBLIC, anon, authenticated, service_role, authenticator;

GRANT USAGE ON SCHEMA primerpaso TO primerpaso_app;
GRANT SELECT ON users, careers, skills, work_areas, companies, candidates,
    company_members, candidate_skills, jobs, job_skills, applications, mensajes_chat,
    ofertas_contratacion, avisos_contratacion, lecturas_notificaciones TO primerpaso_app;
GRANT INSERT ON users, careers, skills, work_areas, companies, candidates, company_members,
    candidate_skills, candidate_work_areas, company_work_areas, jobs, job_skills,
    applications, mensajes_chat, ofertas_contratacion, avisos_contratacion,
    lecturas_notificaciones TO primerpaso_app;

-- Las columnas se limitan a las que escribe JDBC. Los bloqueos FOR SHARE/UPDATE
-- también requieren UPDATE sobre al menos una columna de la tabla.
GRANT UPDATE (name_career) ON careers TO primerpaso_app;
GRANT UPDATE (name_skill) ON skills TO primerpaso_app;
GRANT UPDATE (name_work_area) ON work_areas TO primerpaso_app;
GRANT UPDATE (email_user, updated_at_user) ON users TO primerpaso_app;
GRANT UPDATE (first_name_candidate, last_name_candidate, phone_candidate, city_candidate,
    institution_candidate, career_id_candidate, academic_status_candidate, current_term_candidate,
    graduation_year_candidate, updated_at_candidate) ON candidates TO primerpaso_app;
GRANT UPDATE (trade_name_company, industry_company, city_company, website_company)
    ON companies TO primerpaso_app;
GRANT UPDATE (first_name_company_member, last_name_company_member, phone_company_member)
    ON company_members TO primerpaso_app;
GRANT UPDATE (career_id_job, title_job, description_job, work_mode_job, city_job, country_code_job,
    expires_at_job, plazas, status_job, published_at_job, updated_at_job, motivo_fin_proceso,
    detalle_cancelacion, fecha_fin_proceso, id_responsable_fin_proceso) ON jobs TO primerpaso_app;
GRANT UPDATE (status_application, updated_at_application) ON applications TO primerpaso_app;
GRANT UPDATE (estado, respondida_en, resuelta_en, id_responsable_resolucion)
    ON ofertas_contratacion TO primerpaso_app;
GRANT DELETE ON job_skills TO primerpaso_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON limites_inicio_sesion TO primerpaso_app;

DO $$
DECLARE
    tabla record;
BEGIN
    FOR tabla IN SELECT oid, relname FROM pg_class
        WHERE relnamespace = 'primerpaso'::regnamespace AND relkind = 'r'
    LOOP
        EXECUTE format('ALTER TABLE primerpaso.%I ENABLE ROW LEVEL SECURITY', tabla.relname);
        IF has_table_privilege('primerpaso_app', tabla.oid, 'SELECT, INSERT') THEN
            -- Spring aplica la autorización por cuenta. RLS distingue el rol
            -- JDBC de los demás roles; la política no concede permisos SQL.
            EXECUTE format(
                'CREATE POLICY acceso_servidor ON primerpaso.%I TO primerpaso_app USING (true) WITH CHECK (true)',
                tabla.relname);
        END IF;
    END LOOP;
END
$$;
COMMIT;
