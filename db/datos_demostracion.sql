-- PrimerPaso: datos sintéticos persistentes para demostración local.
-- Ejecutar después de primerpaso.sql. No se ejecuta automáticamente al iniciar.
-- Los accesos y el recorrido de prueba están en README-demostracion.md.
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('primerpaso-demostracion-v1'));

DO $demostracion$
DECLARE
    datos_empresas JSONB := $empresas$[
      {"codigo":"andes","nombre":"Andes Digital (Demo)","correo":"empresa.andes@demo.primerpaso.test",
       "nombres":"Valeria","apellidos":"Soto","rol":"administrador","identificacion":"20000000001",
       "sector":"Tecnología","ciudad":"Lima","carrera":"Ingeniería de Software","area":"Desarrollo de software",
       "hash":"{pbkdf2-sha256}600000$8Q7UcAYkwA0wPOvwWvnw7Q==$qftjbJMA5y3OGfVKo1drtBYriDZ9r5i90vz4tsJ2JpQ=","vacantes":[
         {"codigo":"backend","titulo":"Practicante de desarrollo backend Java","modalidad":"hibrida","estado":"publicada","habilidades":["Java","SQL","Git"],
          "descripcion":"Apoya en la construcción de API con Java, consultas SQL y pruebas de servicios. Buscamos estudiantes con proyectos académicos y disposición para trabajar en equipo.\n\nEscenario de demostración: vacante abierta con varios postulantes."},
         {"codigo":"frontend","titulo":"Practicante de desarrollo frontend","modalidad":"remota","estado":"publicada","habilidades":["JavaScript","HTML y CSS","Git"],
          "descripcion":"Participa en formularios accesibles, interfaces adaptables y consumo de API. Puedes presentar proyectos personales o académicos.\n\nEscenario de demostración: convocatoria vigente con una postulación seleccionada."},
         {"codigo":"calidad","titulo":"Practicante de pruebas de software","modalidad":"presencial","estado":"publicada","habilidades":["SQL","Git"],
          "descripcion":"Diseña casos de prueba, registra incidencias y verifica flujos web. Se valora la atención al detalle y la capacidad de explicar un problema con claridad."},
         {"codigo":"borrador","titulo":"Asistente de documentación técnica","modalidad":"remota","estado":"borrador","habilidades":["Git","HTML y CSS"],
          "descripcion":"Apoya en manuales de usuario y documentación de API.\n\nEscenario de demostración: borrador editable que todavía no aparece en el panel de postulantes."},
         {"codigo":"cerrada","titulo":"Practicante de soporte de aplicaciones","modalidad":"hibrida","estado":"cerrada","habilidades":["SQL","Java"],
          "descripcion":"Colabora en la revisión de incidencias y seguimiento de soluciones.\n\nEscenario de demostración: convocatoria cerrada que conserva su historial de postulaciones."},
         {"codigo":"vencida","titulo":"Practicante de automatización web","modalidad":"remota","estado":"vencida","habilidades":["JavaScript","Git"],
          "descripcion":"Apoya en tareas repetitivas y pruebas de interfaces.\n\nEscenario de demostración: publicada cuyo plazo de postulación ya venció."}]},
      {"codigo":"impulso","nombre":"Impulso Creativo (Demo)","correo":"empresa.impulso@demo.primerpaso.test",
       "nombres":"Diego","apellidos":"Ramos","rol":"administrador","identificacion":"20000000002",
       "sector":"Diseño y comunicación","ciudad":"Arequipa","carrera":"Diseño Gráfico","area":"Diseño y creatividad",
       "hash":"{pbkdf2-sha256}600000$/BpSShHWgfkGcTF12ITsEA==$e9MNtbvlKhbE5bJHdsgOULceyj5QMiiU+De6e7lgCGA=","vacantes":[
         {"codigo":"experiencia","titulo":"Practicante de diseño de interfaces","modalidad":"remota","estado":"publicada","habilidades":["Figma","HTML y CSS"],
          "descripcion":"Colabora en prototipos de interfaces, pruebas de navegación y sistemas visuales. Buscamos una presentación que explique cómo resuelves problemas de los usuarios.\n\nEscenario de demostración: vacante abierta con tres postulantes."},
         {"codigo":"grafico","titulo":"Practicante de diseño gráfico digital","modalidad":"hibrida","estado":"publicada","habilidades":["Figma"],
          "descripcion":"Diseña piezas visuales para productos digitales y adapta composiciones a distintos tamaños. Se valoran proyectos académicos y una explicación clara de tus decisiones de diseño."},
         {"codigo":"contenido","titulo":"Asistente de contenido digital","modalidad":"presencial","estado":"publicada","habilidades":["Figma","Excel"],
          "descripcion":"Organiza contenidos, prepara recursos visuales y registra avances de campañas de demostración. Puedes postular con ejemplos de trabajos realizados en clase."},
         {"codigo":"borrador","titulo":"Practicante de investigación de usuarios","modalidad":"hibrida","estado":"borrador","habilidades":["Figma","Excel"],
          "descripcion":"Apoya en guías de entrevista y organización de hallazgos.\n\nEscenario de demostración: borrador que puede completarse y publicarse desde el panel empresarial."},
         {"codigo":"cerrada","titulo":"Asistente de identidad visual","modalidad":"remota","estado":"cerrada","habilidades":["Figma"],
          "descripcion":"Colabora en propuestas visuales y documentación de componentes.\n\nEscenario de demostración: convocatoria cerrada disponible para consulta empresarial."},
         {"codigo":"vencida","titulo":"Practicante de diseño editorial","modalidad":"presencial","estado":"vencida","habilidades":["Figma","Excel"],
          "descripcion":"Apoya en la organización visual de documentos y revisión de contenidos.\n\nEscenario de demostración: convocatoria vencida con una solicitud conservada en el historial."}]},
      {"codigo":"horizonte","nombre":"Horizonte Analítica (Demo)","correo":"empresa.horizonte@demo.primerpaso.test",
       "nombres":"Camila","apellidos":"Torres","rol":"reclutador","identificacion":"20000000003",
       "sector":"Análisis de datos","ciudad":"Trujillo","carrera":"Ciencia de Datos","area":"Análisis de datos",
       "hash":"{pbkdf2-sha256}600000$9a3JPXkqq5BA8/lRNjHs2Q==$ZeP6xbRp+QsOLw+NswB9GAwtdx+54xy8JO7UXiuCAWw=","vacantes":[
         {"codigo":"analisis","titulo":"Practicante de análisis de datos","modalidad":"hibrida","estado":"publicada","habilidades":["Python","SQL","Excel"],
          "descripcion":"Limpia conjuntos de datos, prepara consultas y comunica hallazgos con claridad. Se aceptan proyectos académicos como experiencia inicial.\n\nEscenario de demostración: vacante abierta con postulantes de distintas carreras."},
         {"codigo":"inteligencia","titulo":"Practicante de inteligencia de negocios","modalidad":"remota","estado":"publicada","habilidades":["Power BI","SQL","Excel"],
          "descripcion":"Prepara tableros, revisa indicadores y documenta su interpretación. Buscamos personas que expliquen sus resultados y puedan recibir retroalimentación."},
         {"codigo":"ingenieria","titulo":"Asistente de ingeniería de datos","modalidad":"presencial","estado":"publicada","habilidades":["Python","PostgreSQL","Git"],
          "descripcion":"Apoya en validación de datos, cargas sencillas y documentación de procesos. Puedes presentar ejercicios y proyectos de formación."},
         {"codigo":"borrador","titulo":"Practicante de calidad de datos","modalidad":"hibrida","estado":"borrador","habilidades":["SQL","Excel"],
          "descripcion":"Define validaciones de registros y comunica inconsistencias.\n\nEscenario de demostración: borrador administrado por una cuenta con rol de reclutador."},
         {"codigo":"cerrada","titulo":"Asistente de reportes operativos","modalidad":"remota","estado":"cerrada","habilidades":["Excel","SQL"],
          "descripcion":"Prepara reportes y organiza resultados para su revisión.\n\nEscenario de demostración: convocatoria cerrada con una postulación descartada."},
         {"codigo":"vencida","titulo":"Practicante de visualización de datos","modalidad":"remota","estado":"vencida","habilidades":["Python","Power BI"],
          "descripcion":"Convierte resultados en gráficos y explicaciones comprensibles.\n\nEscenario de demostración: vacante vencida que no admite nuevas postulaciones."}]}]$empresas$::JSONB;
    datos_postulantes JSONB := $postulantes$[
      {"codigo":"ana","correo":"ana.postulante@demo.primerpaso.test","nombres":"Ana","apellidos":"Torres",
       "ciudad":"Lima","carrera":"Ingeniería de Software","condicion":"estudiante","ciclo":6,"egreso":null,
       "area":"Desarrollo de software","habilidades":["Java","SQL","Git","JavaScript"],"hash":"{pbkdf2-sha256}600000$pAX0uFiNiQ9y+j+Jsw58/A==$3uCItVYOL54+OUrHQt5V8EbBGyQdZubRwjXDqJ9AVsw="},
      {"codigo":"bruno","correo":"bruno.postulante@demo.primerpaso.test","nombres":"Bruno","apellidos":"Medina",
       "ciudad":"Arequipa","carrera":"Diseño Gráfico","condicion":"egresado","ciclo":null,"egreso":2025,
       "area":"Diseño y creatividad","habilidades":["Figma","HTML y CSS","Excel"],"hash":"{pbkdf2-sha256}600000$HtadeN+J8po+wfkxjeaicw==$cJdiXD9aTi5Z83IVhKEUZF0z287qhkb+al4Zfu4+tAw="},
      {"codigo":"carla","correo":"carla.postulante@demo.primerpaso.test","nombres":"Carla","apellidos":"Rojas",
       "ciudad":"Trujillo","carrera":"Ciencia de Datos","condicion":"titulado","ciclo":null,"egreso":2024,
       "area":"Análisis de datos","habilidades":["Python","SQL","Excel","Power BI"],"hash":"{pbkdf2-sha256}600000$shLWDLKZd8tbx3aBG36QOg==$y1nslPuvfHfkT/sQGpQwZn1TlxUFvKVXxCV10sh0O7Y="}
    ]$postulantes$::JSONB;
    datos_postulaciones JSONB := $postulaciones$[
      {"persona":"ana","vacante":"andes:backend","estado":"en_revision","dias":12},
      {"persona":"bruno","vacante":"andes:backend","estado":"enviada","dias":11},
      {"persona":"carla","vacante":"andes:backend","estado":"descartada","dias":10},
      {"persona":"ana","vacante":"impulso:experiencia","estado":"enviada","dias":12},
      {"persona":"bruno","vacante":"impulso:experiencia","estado":"en_revision","dias":11},
      {"persona":"carla","vacante":"impulso:experiencia","estado":"seleccionada","dias":10},
      {"persona":"ana","vacante":"horizonte:analisis","estado":"descartada","dias":12},
      {"persona":"bruno","vacante":"horizonte:analisis","estado":"enviada","dias":11},
      {"persona":"carla","vacante":"horizonte:analisis","estado":"en_revision","dias":10},
      {"persona":"ana","vacante":"andes:frontend","estado":"seleccionada","dias":8},
      {"persona":"bruno","vacante":"impulso:grafico","estado":"descartada","dias":8},
      {"persona":"carla","vacante":"horizonte:inteligencia","estado":"seleccionada","dias":8},
      {"persona":"ana","vacante":"andes:cerrada","estado":"descartada","dias":14},
      {"persona":"bruno","vacante":"impulso:vencida","estado":"en_revision","dias":10},
      {"persona":"carla","vacante":"horizonte:cerrada","estado":"descartada","dias":14}
    ]$postulaciones$::JSONB;
    datos_cuenta JSONB;
    datos_empresa JSONB;
    datos_vacante JSONB;
    datos_postulacion JSONB;
    mapa_usuarios JSONB := '{}'::JSONB;
    mapa_vacantes JSONB := '{}'::JSONB;
    id_usuario BIGINT;
    id_empresa BIGINT;
    id_carrera BIGINT;
    id_area BIGINT;
    id_vacante BIGINT;
    nombre_habilidad TEXT;
    correos_demo TEXT[];
    total_cuentas INTEGER;
    cuentas_marcadas INTEGER;
    carta TEXT;
BEGIN
    SELECT array_agg(cuenta->>'correo') INTO correos_demo
    FROM jsonb_array_elements(datos_empresas || datos_postulantes) AS cuentas(cuenta);
    SELECT count(*), count(*) FILTER (WHERE terms_version_user = 'demostracion-v1')
    INTO total_cuentas, cuentas_marcadas FROM users WHERE email_user = ANY(correos_demo);
    IF total_cuentas > 0 THEN
        IF total_cuentas = 6 AND cuentas_marcadas = 6 THEN
            RAISE NOTICE 'Las seis cuentas de demostración ya existen. Se conservan todos sus cambios.';
            RETURN;
        END IF;
        RAISE EXCEPTION 'Un correo coincide con una cuenta existente o el conjunto demo está incompleto. No se modifica nada.';
    END IF;
    IF EXISTS (SELECT 1 FROM companies WHERE country_code_company = 'PE'
            AND tax_id_company IN ('20000000001', '20000000002', '20000000003')) THEN
        RAISE EXCEPTION 'Una identificación de demostración ya está ocupada. No se reutilizan empresas existentes.';
    END IF;

    -- Solo se insertan catálogos ausentes; un catálogo inactivo revierte el conjunto.
    FOR datos_cuenta IN SELECT value FROM jsonb_array_elements(datos_empresas || datos_postulantes) LOOP
        INSERT INTO careers(name_career) VALUES (datos_cuenta->>'carrera') ON CONFLICT DO NOTHING;
        SELECT id_career INTO id_carrera FROM careers
        WHERE lower(btrim(name_career)) = lower(datos_cuenta->>'carrera') AND active_career;
        IF NOT FOUND THEN RAISE EXCEPTION 'La carrera de demostración está inactiva.'; END IF;
        INSERT INTO work_areas(name_work_area) VALUES (datos_cuenta->>'area') ON CONFLICT DO NOTHING;
        SELECT id_work_area INTO id_area FROM work_areas
        WHERE lower(btrim(name_work_area)) = lower(datos_cuenta->>'area') AND active_work_area;
        IF NOT FOUND THEN RAISE EXCEPTION 'El área de demostración está inactiva.'; END IF;
        INSERT INTO users(email_user, password_hash_user, terms_accepted_at_user, terms_version_user)
        VALUES (datos_cuenta->>'correo', datos_cuenta->>'hash', now(), 'demostracion-v1') RETURNING id_user INTO id_usuario;
        mapa_usuarios := mapa_usuarios || jsonb_build_object(datos_cuenta->>'codigo', id_usuario);
        IF datos_cuenta ? 'condicion' THEN
            INSERT INTO candidates(id_candidate, first_name_candidate, last_name_candidate, city_candidate,
                country_code_candidate, institution_candidate, career_id_candidate, academic_status_candidate,
                current_term_candidate, graduation_year_candidate)
            VALUES (id_usuario, datos_cuenta->>'nombres', datos_cuenta->>'apellidos', datos_cuenta->>'ciudad',
                'PE', 'Instituto de Demostración PrimerPaso', id_carrera, datos_cuenta->>'condicion',
                (datos_cuenta->>'ciclo')::SMALLINT, (datos_cuenta->>'egreso')::SMALLINT);
            INSERT INTO candidate_work_areas(candidate_id_candidate_work_area, work_area_id_candidate_work_area)
            VALUES (id_usuario, id_area);
            FOR nombre_habilidad IN SELECT jsonb_array_elements_text(datos_cuenta->'habilidades') LOOP
                INSERT INTO skills(name_skill) VALUES (nombre_habilidad) ON CONFLICT DO NOTHING;
                INSERT INTO candidate_skills(candidate_id_candidate_skill, skill_id_candidate_skill)
                SELECT id_usuario, id_skill FROM skills
                WHERE lower(btrim(name_skill)) = lower(nombre_habilidad) AND active_skill;
                IF NOT FOUND THEN RAISE EXCEPTION 'Una habilidad de demostración está inactiva.'; END IF;
            END LOOP;
        ELSE
            INSERT INTO companies(trade_name_company, country_code_company, tax_id_company, industry_company,
                city_company, description_company)
            VALUES (datos_cuenta->>'nombre', 'PE', datos_cuenta->>'identificacion', datos_cuenta->>'sector',
                datos_cuenta->>'ciudad', 'Empresa sintética para demostrar las funcionalidades de PrimerPaso.')
            RETURNING id_company INTO id_empresa;
            INSERT INTO company_members(company_id_company_member, user_id_company_member, role_company_member,
                first_name_company_member, last_name_company_member)
            VALUES (id_empresa, id_usuario, datos_cuenta->>'rol', datos_cuenta->>'nombres', datos_cuenta->>'apellidos');
            INSERT INTO company_work_areas(company_id_company_work_area, work_area_id_company_work_area)
            VALUES (id_empresa, id_area);
        END IF;
    END LOOP;

    FOR datos_empresa IN SELECT value FROM jsonb_array_elements(datos_empresas) LOOP
        SELECT id_company INTO id_empresa FROM companies
        WHERE country_code_company = 'PE' AND tax_id_company = datos_empresa->>'identificacion';
        SELECT id_career INTO id_carrera FROM careers WHERE lower(btrim(name_career)) = lower(datos_empresa->>'carrera');
        SELECT id_work_area INTO id_area FROM work_areas WHERE lower(btrim(name_work_area)) = lower(datos_empresa->>'area');
        id_usuario := (mapa_usuarios->>(datos_empresa->>'codigo'))::BIGINT;
        FOR datos_vacante IN SELECT value FROM jsonb_array_elements(datos_empresa->'vacantes') LOOP
            INSERT INTO jobs(company_id_job, creator_user_id_job, career_id_job, work_area_id_job,
                title_job, description_job, work_mode_job, city_job, country_code_job, status_job,
                created_at_job, published_at_job, expires_at_job, updated_at_job)
            VALUES (id_empresa, id_usuario, id_carrera, id_area, '[Demo] ' || (datos_vacante->>'titulo'),
                datos_vacante->>'descripcion', datos_vacante->>'modalidad', datos_empresa->>'ciudad', 'PE',
                CASE WHEN datos_vacante->>'estado' = 'vencida' THEN 'publicada' ELSE datos_vacante->>'estado' END,
                now() - interval '40 days',
                CASE WHEN datos_vacante->>'estado' = 'borrador' THEN NULL ELSE now() - interval '30 days' END,
                CASE WHEN datos_vacante->>'estado' = 'vencida' THEN now() - interval '3 days' ELSE now() + interval '60 days' END,
                now() - interval '2 days') RETURNING id_job INTO id_vacante;
            mapa_vacantes := mapa_vacantes || jsonb_build_object(
                (datos_empresa->>'codigo') || ':' || (datos_vacante->>'codigo'), id_vacante);
            FOR nombre_habilidad IN SELECT jsonb_array_elements_text(datos_vacante->'habilidades') LOOP
                INSERT INTO skills(name_skill) VALUES (nombre_habilidad) ON CONFLICT DO NOTHING;
                INSERT INTO job_skills(job_id_job_skill, skill_id_job_skill)
                SELECT id_vacante, id_skill FROM skills WHERE lower(btrim(name_skill)) = lower(nombre_habilidad) AND active_skill;
                IF NOT FOUND THEN RAISE EXCEPTION 'Una habilidad de vacante está inactiva.'; END IF;
            END LOOP;
        END LOOP;
    END LOOP;

    FOR datos_postulacion IN SELECT value FROM jsonb_array_elements(datos_postulaciones) LOOP
        id_usuario := (mapa_usuarios->>(datos_postulacion->>'persona'))::BIGINT;
        id_vacante := (mapa_vacantes->>(datos_postulacion->>'vacante'))::BIGINT;
        SELECT format(E'Hola, equipo de selección:\n\nSoy %s %s y me formé en %s. Me interesa la vacante de %s porque quiero aplicar mis conocimientos, aprender con el equipo y aportar a proyectos concretos.\n\n%s\n\nTengo disposición para coordinar una entrevista y conversar sobre mi disponibilidad. Gracias por revisar mi postulación.\n\nSaludos,\n%s %s\n\nPostulación de demostración con datos sintéticos.',
            c.first_name_candidate, c.last_name_candidate, r.name_career, j.title_job,
            CASE datos_postulacion->>'persona'
                WHEN 'ana' THEN 'En mis proyectos académicos he desarrollado servicios con Java y consultas SQL. Me gusta trabajar con control de versiones y explicar las decisiones de implementación.'
                WHEN 'bruno' THEN 'He preparado prototipos en Figma y composiciones digitales. Me interesa conectar las necesidades de los usuarios con interfaces claras y accesibles.'
                ELSE 'He trabajado con Python, SQL y tableros de datos en ejercicios de formación. Puedo apoyar en la revisión de registros y la comunicación de hallazgos.' END,
            c.first_name_candidate, c.last_name_candidate)
        INTO carta FROM candidates c JOIN careers r ON r.id_career = c.career_id_candidate
            CROSS JOIN jobs j WHERE c.id_candidate = id_usuario AND j.id_job = id_vacante;
        INSERT INTO applications(job_id_application, candidate_id_application, cover_letter_application,
            status_application, submitted_at_application, updated_at_application)
        VALUES (id_vacante, id_usuario, carta, datos_postulacion->>'estado',
            now() - make_interval(days => (datos_postulacion->>'dias')::INTEGER), now() - interval '1 day');
    END LOOP;
    RAISE NOTICE 'Creadas seis cuentas, tres empresas, 18 vacantes y 15 postulaciones de demostración.';
END;
$demostracion$;
COMMIT;
