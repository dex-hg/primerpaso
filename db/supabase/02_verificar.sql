\set ON_ERROR_STOP on
BEGIN READ ONLY;

DO $$
DECLARE
    esquema oid := 'primerpaso'::regnamespace;
    aplicacion pg_roles%ROWTYPE;
    objeto record;
    rol_api text;
    roles_api constant text[] := ARRAY['anon', 'authenticated', 'service_role', 'authenticator'];
BEGIN
    SELECT * INTO STRICT aplicacion FROM pg_roles WHERE rolname = 'primerpaso_app';
    IF NOT aplicacion.rolcanlogin OR aplicacion.rolsuper OR aplicacion.rolcreaterole
        OR aplicacion.rolcreatedb OR aplicacion.rolreplication OR aplicacion.rolbypassrls
        OR aplicacion.rolinherit OR aplicacion.rolconnlimit < 1
        OR EXISTS (SELECT FROM pg_auth_members WHERE member = aplicacion.oid) THEN
        RAISE EXCEPTION 'El rol primerpaso_app no tiene los atributos o las membresías esperados.';
    END IF;

    IF NOT has_schema_privilege(aplicacion.oid, esquema, 'USAGE')
        OR has_schema_privilege(aplicacion.oid, esquema, 'CREATE')
        OR EXISTS (SELECT FROM pg_namespace n
            CROSS JOIN LATERAL aclexplode(n.nspacl) permiso
            WHERE n.oid = esquema AND permiso.grantee = 0) THEN
        RAISE EXCEPTION 'Permisos inesperados en el esquema primerpaso.';
    END IF;
    IF to_regclass('public.users') IS NOT NULL OR to_regclass('public.jobs') IS NOT NULL
        OR to_regclass('public.applications') IS NOT NULL THEN
        RAISE EXCEPTION 'Hay tablas de PrimerPaso en public. Revisa la instalación antes de continuar.';
    END IF;

    FOREACH rol_api IN ARRAY roles_api LOOP
        IF has_schema_privilege(rol_api, esquema, 'USAGE, CREATE')
            OR pg_has_role(rol_api, aplicacion.oid, 'MEMBER') THEN
            RAISE EXCEPTION 'El rol % tiene acceso al esquema o al rol de Spring.', rol_api;
        END IF;
    END LOOP;

    IF (SELECT count(*) FROM pg_class WHERE relnamespace = esquema AND relkind IN ('r', 'p')) < 20
        OR to_regclass('primerpaso.limites_inicio_sesion') IS NULL THEN
        RAISE EXCEPTION 'Faltan tablas del esquema base.';
    END IF;
    FOR objeto IN SELECT oid, relname, relowner, relrowsecurity, relkind FROM pg_class
        WHERE relnamespace = esquema AND relkind IN ('r', 'p', 'S')
    LOOP
        IF objeto.relowner = aplicacion.oid THEN
            RAISE EXCEPTION 'El rol de Spring es propietario de %.', objeto.relname;
        END IF;
        IF objeto.relkind = 'S' THEN
            FOREACH rol_api IN ARRAY roles_api LOOP
                IF has_sequence_privilege(rol_api, objeto.oid, 'USAGE, SELECT, UPDATE') THEN
                    RAISE EXCEPTION 'El rol % tiene permisos sobre la secuencia %.', rol_api, objeto.relname;
                END IF;
            END LOOP;
            CONTINUE;
        END IF;
        IF NOT objeto.relrowsecurity OR EXISTS (SELECT FROM pg_policy
            WHERE polrelid = objeto.oid AND polroles <> ARRAY[aplicacion.oid])
            OR (has_table_privilege(aplicacion.oid, objeto.oid, 'SELECT, INSERT')
                AND NOT EXISTS (SELECT FROM pg_policy WHERE polrelid = objeto.oid
                    AND polname = 'acceso_servidor')) THEN
            RAISE EXCEPTION 'RLS o políticas inesperadas en %.', objeto.relname;
        END IF;
        FOREACH rol_api IN ARRAY roles_api LOOP
            IF has_any_column_privilege(rol_api, objeto.oid, 'SELECT, INSERT, UPDATE, REFERENCES')
                OR has_table_privilege(rol_api, objeto.oid, 'DELETE, TRUNCATE, TRIGGER') THEN
                RAISE EXCEPTION 'El rol % tiene permisos sobre %.', rol_api, objeto.relname;
            END IF;
        END LOOP;
        IF has_table_privilege(aplicacion.oid, objeto.oid, 'TRUNCATE, TRIGGER, REFERENCES')
            OR (objeto.relname NOT IN ('job_skills', 'limites_inicio_sesion')
                AND has_table_privilege(aplicacion.oid, objeto.oid, 'DELETE'))
            OR (objeto.relname IN ('resumes', 'projects')
                AND has_any_column_privilege(aplicacion.oid, objeto.oid, 'SELECT, INSERT, UPDATE, REFERENCES')) THEN
            RAISE EXCEPTION 'El rol de Spring tiene permisos fuera de alcance sobre %.', objeto.relname;
        END IF;
        IF objeto.relname = 'limites_inicio_sesion' AND NOT (
            has_table_privilege(aplicacion.oid, objeto.oid, 'SELECT')
            AND has_table_privilege(aplicacion.oid, objeto.oid, 'INSERT')
            AND has_table_privilege(aplicacion.oid, objeto.oid, 'UPDATE')
            AND has_table_privilege(aplicacion.oid, objeto.oid, 'DELETE')) THEN
            RAISE EXCEPTION 'Faltan permisos de Spring para los contadores de acceso.';
        END IF;
    END LOOP;
    IF has_column_privilege(aplicacion.oid, 'primerpaso.users', 'password_hash_user', 'UPDATE')
        OR has_column_privilege(aplicacion.oid, 'primerpaso.users', 'status_user', 'UPDATE')
        OR has_column_privilege(aplicacion.oid, 'primerpaso.company_members', 'role_company_member', 'UPDATE') THEN
        RAISE EXCEPTION 'El rol de Spring puede cambiar credenciales, estado de cuentas o roles de membresía.';
    END IF;
END
$$;

SELECT count(*) AS tablas_con_rls
FROM pg_class
WHERE relnamespace = 'primerpaso'::regnamespace AND relkind IN ('r', 'p') AND relrowsecurity;
COMMIT;
