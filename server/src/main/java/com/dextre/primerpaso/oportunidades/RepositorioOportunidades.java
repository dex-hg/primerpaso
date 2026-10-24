package com.dextre.primerpaso.oportunidades;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.primerpaso.oportunidades.DatosOportunidades.Filtros;
import com.dextre.primerpaso.oportunidades.DatosOportunidades.Oportunidad;
import com.dextre.primerpaso.vacantes.DatosVacantes.Habilidad;

@Repository
public class RepositorioOportunidades {

    private static final String ORIGEN = """
            FROM jobs j JOIN companies e ON e.id_company = j.company_id_job
            JOIN careers c ON c.id_career = j.career_id_job
            WHERE j.status_job = 'publicada' AND e.active_company
                AND j.published_at_job <= ? AND (j.expires_at_job IS NULL OR j.expires_at_job > ?)
            """;
    private static final String COLUMNAS = """
            SELECT j.id_job, j.title_job, j.description_job, e.trade_name_company, c.name_career,
                j.work_mode_job, j.city_job, j.country_code_job, j.published_at_job, j.expires_at_job
            """;
    private final JdbcTemplate plantillaJdbc;

    public RepositorioOportunidades(JdbcTemplate plantillaJdbc) {
        this.plantillaJdbc = plantillaJdbc;
    }

    public long contar(Filtros filtros, Instant ahora) {
        List<Object> parametros = parametrosFecha(ahora);
        Long total = plantillaJdbc.queryForObject("SELECT count(*) " + ORIGEN + filtrar(filtros, parametros),
                Long.class, parametros.toArray());
        return total == null ? 0 : total;
    }

    public List<Oportunidad> listar(Filtros filtros, int pagina, int tamanio, Instant ahora) {
        List<Object> parametros = parametrosFecha(ahora);
        String condiciones = filtrar(filtros, parametros);
        parametros.add(tamanio);
        parametros.add((long) pagina * tamanio);
        List<Oportunidad> oportunidades = plantillaJdbc.query(COLUMNAS + ORIGEN + condiciones
                + " ORDER BY j.published_at_job DESC, j.id_job DESC LIMIT ? OFFSET ?",
                (resultado, fila) -> mapear(resultado), parametros.toArray());
        return incluirHabilidades(oportunidades);
    }

    public Optional<Oportunidad> consultar(long idVacante, Instant ahora) {
        List<Object> parametros = parametrosFecha(ahora);
        parametros.add(idVacante);
        List<Oportunidad> oportunidades = plantillaJdbc.query(COLUMNAS + ORIGEN + " AND j.id_job = ?",
                (resultado, fila) -> mapear(resultado), parametros.toArray());
        return incluirHabilidades(oportunidades).stream().findFirst();
    }

    private List<Object> parametrosFecha(Instant ahora) {
        return new ArrayList<>(List.of(Timestamp.from(ahora), Timestamp.from(ahora)));
    }

    private String filtrar(Filtros filtros, List<Object> parametros) {
        StringBuilder consulta = new StringBuilder();
        if (!filtros.busqueda().isEmpty()) {
            consulta.append("""
                     AND (concat_ws(' ', j.title_job, j.description_job, e.trade_name_company, c.name_career)
                        ILIKE ? ESCAPE '!' OR EXISTS (SELECT 1 FROM job_skills js JOIN skills s
                            ON s.id_skill = js.skill_id_job_skill WHERE js.job_id_job_skill = j.id_job
                            AND s.name_skill ILIKE ? ESCAPE '!'))
                    """);
            parametros.add(patronBusqueda(filtros.busqueda()));
            parametros.add(patronBusqueda(filtros.busqueda()));
        }
        if (!filtros.modalidad().isEmpty()) {
            consulta.append(" AND j.work_mode_job = ?");
            parametros.add(filtros.modalidad());
        }
        if (filtros.idCarrera() != null) {
            consulta.append(" AND j.career_id_job = ?");
            parametros.add(filtros.idCarrera());
        }
        if (!filtros.ciudad().isEmpty()) {
            consulta.append(" AND j.city_job ILIKE ? ESCAPE '!'");
            parametros.add(patronBusqueda(filtros.ciudad()));
        }
        if (filtros.idHabilidad() != null) {
            consulta.append(" AND EXISTS (SELECT 1 FROM job_skills js WHERE js.job_id_job_skill = j.id_job"
                    + " AND js.skill_id_job_skill = ?)");
            parametros.add(filtros.idHabilidad());
        }
        return consulta.toString();
    }

    private String patronBusqueda(String texto) {
        return "%" + texto.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private List<Oportunidad> incluirHabilidades(List<Oportunidad> oportunidades) {
        if (oportunidades.isEmpty()) return oportunidades;
        String marcadores = String.join(",", Collections.nCopies(oportunidades.size(), "?"));
        Map<Long, List<Habilidad>> habilidades = new HashMap<>();
        plantillaJdbc.query("""
                SELECT js.job_id_job_skill, s.id_skill, s.name_skill FROM job_skills js
                JOIN skills s ON s.id_skill = js.skill_id_job_skill
                WHERE js.job_id_job_skill IN (
                """ + marcadores + ") ORDER BY lower(s.name_skill), s.id_skill", resultado -> {
                    habilidades.computeIfAbsent(resultado.getLong(1), identificador -> new ArrayList<>())
                            .add(new Habilidad(resultado.getLong(2), resultado.getString(3)));
                }, oportunidades.stream().map(Oportunidad::id).toArray());
        return oportunidades.stream().map(oportunidad -> new Oportunidad(oportunidad.id(), oportunidad.titulo(),
                oportunidad.descripcion(), oportunidad.nombreEmpresa(), oportunidad.nombreCarrera(),
                oportunidad.modalidad(), oportunidad.ciudad(), oportunidad.codigoPais(), oportunidad.fechaPublicacion(),
                oportunidad.fechaVencimiento(), habilidades.getOrDefault(oportunidad.id(), List.of()))).toList();
    }

    private Oportunidad mapear(ResultSet resultado) throws SQLException {
        Timestamp vencimiento = resultado.getTimestamp("expires_at_job");
        return new Oportunidad(resultado.getLong("id_job"), resultado.getString("title_job"),
                resultado.getString("description_job"), resultado.getString("trade_name_company"),
                resultado.getString("name_career"), resultado.getString("work_mode_job"),
                resultado.getString("city_job"), resultado.getString("country_code_job"),
                resultado.getTimestamp("published_at_job").toInstant(),
                vencimiento == null ? null : vencimiento.toInstant(), List.of());
    }
}
