package com.dextre.primerpaso.configuracion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "primerpaso.bd.comprobar-al-iniciar", havingValue = "true", matchIfMissing = true)
public class ComprobacionBaseDatos implements ApplicationRunner {

    private static final Logger REGISTRO = LoggerFactory.getLogger(ComprobacionBaseDatos.class);
    private final DataSource fuenteDatos;
    private final JdbcTemplate jdbc;
    private final String clave;

    public ComprobacionBaseDatos(DataSource fuenteDatos, JdbcTemplate jdbc,
            @Value("${spring.datasource.password}") String clave) {
        this.fuenteDatos = fuenteDatos;
        this.jdbc = jdbc;
        this.clave = clave;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (clave == null || clave.isBlank()) {
            throw new IllegalStateException("Configura PRIMERPASO_BD_CLAVE en server/.env.");
        }
        try (Connection conexion = fuenteDatos.getConnection()) {
            if (!conexion.isValid(5)) {
                throw new IllegalStateException("PostgreSQL no responde a la comprobación de conexión.");
            }
        } catch (SQLException excepcion) {
            throw new IllegalStateException("No se pudo conectar a PostgreSQL. Revisa server/.env.");
        }
        for (String tabla : List.of("users", "candidates", "careers", "skills", "work_areas",
                "companies", "company_members", "candidate_skills", "candidate_work_areas", "company_work_areas",
                "jobs", "job_skills")) {
            Boolean existe = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, tabla);
            if (!Boolean.TRUE.equals(existe)) {
                throw new IllegalStateException("Faltan tablas del esquema. Revisa db/primerpaso.sql.");
            }
        }
        Integer columnas = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'company_members'
                AND column_name IN ('first_name_company_member', 'last_name_company_member', 'phone_company_member')
                """, Integer.class);
        if (columnas == null || columnas != 3) {
            throw new IllegalStateException("Aplica db/registro_reclutador.sql antes de iniciar el servidor.");
        }
        Integer columnasChat = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'mensajes_chat'
                AND column_name IN ('id_mensaje', 'id_postulacion', 'id_emisor', 'id_envio',
                    'nombre_emisor', 'tipo_emisor', 'contenido', 'fecha_envio')
                """, Integer.class);
        if (columnasChat == null || columnasChat != 8) {
            throw new IllegalStateException("Aplica db/migraciones/V001__mensajes_chat.sql antes de iniciar el servidor.");
        }
        Boolean esquemaContratacion = jdbc.queryForObject("""
                SELECT to_regclass('ofertas_contratacion') IS NOT NULL AND to_regclass('avisos_contratacion') IS NOT NULL
                    AND (SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema()
                        AND table_name = 'jobs' AND column_name IN ('plazas', 'motivo_fin_proceso', 'detalle_cancelacion',
                            'fecha_fin_proceso', 'id_responsable_fin_proceso')) = 5
                """, Boolean.class);
        if (!Boolean.TRUE.equals(esquemaContratacion)) {
            throw new IllegalStateException("Aplica db/migraciones/V002__contratacion.sql antes de iniciar el servidor.");
        }
        REGISTRO.info("Conexión con PostgreSQL y tablas del proyecto comprobadas.");
    }
}
