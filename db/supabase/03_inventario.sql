\set ON_ERROR_STOP on
\if :{?esquema}
\else
    \set esquema primerpaso
\endif
\if :{?exigir_vacia}
\else
    \set exigir_vacia off
\endif

-- Usar como propietario. Cuenta filas sin mostrar datos personales ni hashes.
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL search_path TO :"esquema", pg_temp;
SET LOCAL row_security = off;

\if :exigir_vacia
DO $$
DECLARE
    tabla record;
    tiene_filas boolean;
BEGIN
    FOR tabla IN SELECT schemaname, tablename FROM pg_tables WHERE schemaname = current_schema() LOOP
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I.%I)', tabla.schemaname, tabla.tablename)
            INTO tiene_filas;
        IF tiene_filas THEN
            RAISE EXCEPTION 'La tabla % no está vacía. No restaures el traslado sobre una base en uso.', tabla.tablename;
        END IF;
    END LOOP;
END
$$;
\endif

SELECT count(*) AS cuentas,
    count(*) FILTER (WHERE es_prueba) AS cuentas_de_prueba,
    count(*) FILTER (WHERE es_prueba) > 0 AS hay_cuentas_de_prueba
FROM (
    SELECT terms_version_user = 'demostracion-v1' OR terms_version_user LIKE 'qa-%'
        OR split_part(email_user, '@', 2) ~ '(\.|^)(test|example|invalid|localhost)$'
        AS es_prueba
    FROM :"esquema".users
) cuentas_clasificadas
\gset
\echo Cuentas: :cuentas. Marcadas como prueba o demostración: :cuentas_de_prueba.

\if :hay_cuentas_de_prueba
DO $$
BEGIN
    RAISE EXCEPTION 'Hay cuentas de prueba o demostración. Prepara una copia aprobada y depurada antes de transferir datos.';
END
$$;
\endif

SELECT string_agg(format('SELECT %L AS tabla, count(*) AS filas FROM %I.%I',
    tablename, schemaname, tablename), ' UNION ALL ' ORDER BY tablename) || ' ORDER BY tabla'
FROM pg_tables
WHERE schemaname = current_schema()
\gexec

DO $$
DECLARE
    identidad record;
    maximo bigint;
    ultimo bigint;
    usada boolean;
BEGIN
    FOR identidad IN SELECT c.relname AS tabla, a.attname AS columna,
        pg_get_serial_sequence(format('%I.%I', current_schema(), c.relname), a.attname) AS secuencia
        FROM pg_class c
        JOIN pg_attribute a ON a.attrelid = c.oid
        WHERE c.relnamespace = current_schema()::regnamespace AND c.relkind = 'r'
            AND a.attidentity <> ''
    LOOP
        EXECUTE format('SELECT max(%I) FROM %I.%I', identidad.columna, current_schema(), identidad.tabla)
            INTO maximo;
        EXECUTE format('SELECT last_value, is_called FROM %s', identidad.secuencia) INTO ultimo, usada;
        IF maximo IS NOT NULL AND (ultimo < maximo OR (ultimo = maximo AND NOT usada)) THEN
            RAISE EXCEPTION 'La secuencia de %.% no está adelantada respecto de sus IDs. Revisa la copia.',
                identidad.tabla, identidad.columna;
        END IF;
    END LOOP;
END
$$;
COMMIT;
