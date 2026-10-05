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
        REGISTRO.info("Conexión con PostgreSQL y tablas del proyecto comprobadas.");
    }
}
