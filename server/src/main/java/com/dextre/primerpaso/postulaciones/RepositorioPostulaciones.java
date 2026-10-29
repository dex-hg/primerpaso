package com.dextre.primerpaso.postulaciones;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.primerpaso.postulaciones.DatosPostulaciones.Detalle;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.Perfil;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.Resumen;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

@Repository
public class RepositorioPostulaciones {

    private static final String ORIGEN = """
            FROM applications a JOIN jobs j ON j.id_job = a.job_id_application
            JOIN companies e ON e.id_company = j.company_id_job
            JOIN candidates c ON c.id_candidate = a.candidate_id_application
            JOIN users u ON u.id_user = c.id_candidate
            JOIN careers r ON r.id_career = c.career_id_candidate
            """;
    private static final String COLUMNAS = """
            SELECT a.id_application, j.id_job, j.title_job, e.trade_name_company,
                c.first_name_candidate, c.last_name_candidate, a.status_application,
                a.submitted_at_application, a.updated_at_application
            """;
    private final JdbcTemplate plantillaJdbc;

    public RepositorioPostulaciones(JdbcTemplate plantillaJdbc) {
        this.plantillaJdbc = plantillaJdbc;
    }

    public boolean postulanteActivo(long idUsuario, boolean bloquear) {
        return !plantillaJdbc.query("""
                SELECT c.id_candidate FROM candidates c JOIN users u ON u.id_user = c.id_candidate
                WHERE c.id_candidate = ? AND u.status_user = 'activo'
                """ + (bloquear ? " FOR SHARE OF c, u" : ""),
                (resultado, fila) -> resultado.getLong(1), idUsuario).isEmpty();
    }

    public Optional<Long> buscarPropia(long idUsuario, long idVacante) {
        return plantillaJdbc.query("""
                SELECT id_application FROM applications
                WHERE candidate_id_application = ? AND job_id_application = ?
                """, (resultado, fila) -> resultado.getLong(1), idUsuario, idVacante).stream().findFirst();
    }

    public boolean bloquearVacanteDisponible(long idVacante) {
        return !plantillaJdbc.query("""
                SELECT j.id_job FROM jobs j JOIN companies e ON e.id_company = j.company_id_job
                WHERE j.id_job = ? AND j.status_job = 'publicada' AND e.active_company
                    AND j.published_at_job <= clock_timestamp()
                    AND (j.expires_at_job IS NULL OR j.expires_at_job > clock_timestamp())
                FOR SHARE OF j, e
                """, (resultado, fila) -> resultado.getLong(1), idVacante).isEmpty();
    }

    public Optional<Long> crear(long idUsuario, long idVacante, String carta) {
        return plantillaJdbc.query("""
                INSERT INTO applications (candidate_id_application, job_id_application,
                    cover_letter_application, submitted_at_application, updated_at_application)
                SELECT ?, j.id_job, ?, clock_timestamp(), clock_timestamp()
                FROM jobs j JOIN companies e ON e.id_company = j.company_id_job
                WHERE j.id_job = ? AND j.status_job = 'publicada' AND e.active_company
                    AND j.published_at_job <= clock_timestamp()
                    AND (j.expires_at_job IS NULL OR j.expires_at_job > clock_timestamp())
                ON CONFLICT (job_id_application, candidate_id_application) DO NOTHING
                RETURNING id_application
                """, (resultado, fila) -> resultado.getLong(1), idUsuario, carta, idVacante).stream().findFirst();
    }

    public Optional<String> tituloVacanteEmpresa(long idEmpresa, long idVacante) {
        return plantillaJdbc.query("SELECT title_job FROM jobs WHERE company_id_job = ? AND id_job = ?",
                (resultado, fila) -> resultado.getString(1), idEmpresa, idVacante).stream().findFirst();
    }

    public long contar(UsuarioSesion usuario, String estado, Long idVacante) {
        List<Object> parametros = new ArrayList<>();
        Long total = plantillaJdbc.queryForObject("SELECT count(*) " + ORIGEN
                + condiciones(usuario, estado, idVacante, parametros), Long.class, parametros.toArray());
        return total == null ? 0 : total;
    }

    public List<Resumen> listar(UsuarioSesion usuario, String estado, Long idVacante, int pagina, int tamanio) {
        List<Object> parametros = new ArrayList<>();
        String filtro = condiciones(usuario, estado, idVacante, parametros);
        parametros.add(tamanio);
        parametros.add((long) pagina * tamanio);
        return plantillaJdbc.query(COLUMNAS + ORIGEN + filtro
                + " ORDER BY a.submitted_at_application DESC, a.id_application DESC LIMIT ? OFFSET ?",
                (resultado, fila) -> mapearResumen(resultado), parametros.toArray());
    }

    public Optional<Detalle> consultar(UsuarioSesion usuario, long idPostulacion, boolean bloquear) {
        List<Object> parametros = new ArrayList<>();
        String filtro = condiciones(usuario, "todos", null, parametros);
        parametros.add(idPostulacion);
        String consulta = COLUMNAS + """
                , a.cover_letter_application, u.email_user, c.phone_candidate, c.city_candidate,
                c.institution_candidate, r.name_career, c.academic_status_candidate,
                c.current_term_candidate, c.graduation_year_candidate, c.id_candidate
                """ + ORIGEN + filtro + " AND a.id_application = ?" + (bloquear ? " FOR UPDATE OF a" : "");
        return plantillaJdbc.query(consulta, (resultado, fila) -> {
            List<String> habilidades = plantillaJdbc.query("""
                    SELECT s.name_skill FROM candidate_skills cs JOIN skills s ON s.id_skill = cs.skill_id_candidate_skill
                    WHERE cs.candidate_id_candidate_skill = ? ORDER BY lower(s.name_skill), s.id_skill
                    """, (habilidad, indice) -> habilidad.getString(1), resultado.getLong("id_candidate"));
            var perfil = new Perfil(resultado.getString("email_user"), resultado.getString("phone_candidate"),
                    resultado.getString("city_candidate"), resultado.getString("institution_candidate"),
                    resultado.getString("name_career"), resultado.getString("academic_status_candidate"),
                    resultado.getObject("current_term_candidate", Integer.class),
                    resultado.getObject("graduation_year_candidate", Integer.class), habilidades);
            return new Detalle(mapearResumen(resultado), resultado.getString("cover_letter_application"), perfil);
        }, parametros.toArray()).stream().findFirst();
    }

    public void actualizarEstado(long idEmpresa, long idPostulacion, String estado, Instant version) {
        int filas = plantillaJdbc.update("""
                UPDATE applications a SET status_application = ?,
                    updated_at_application = GREATEST(clock_timestamp(), a.updated_at_application + interval '1 microsecond')
                FROM jobs j WHERE j.id_job = a.job_id_application AND j.company_id_job = ?
                    AND a.id_application = ? AND a.updated_at_application = ?
                """, estado, idEmpresa, idPostulacion, java.sql.Timestamp.from(version));
        if (filas != 1) {
            throw new IllegalStateException("No se pudo actualizar la postulación bloqueada.");
        }
    }

    private String condiciones(UsuarioSesion usuario, String estado, Long idVacante, List<Object> parametros) {
        StringBuilder filtro = new StringBuilder("empresa".equals(usuario.tipoCuenta())
                ? " WHERE j.company_id_job = ?" : " WHERE a.candidate_id_application = ?");
        parametros.add("empresa".equals(usuario.tipoCuenta()) ? usuario.idEmpresa() : usuario.idUsuario());
        if (!"todos".equals(estado)) {
            filtro.append(" AND a.status_application = ?");
            parametros.add(estado);
        }
        if (idVacante != null) {
            filtro.append(" AND j.id_job = ?");
            parametros.add(idVacante);
        }
        return filtro.toString();
    }

    private Resumen mapearResumen(ResultSet resultado) throws SQLException {
        return new Resumen(resultado.getLong("id_application"), resultado.getLong("id_job"),
                resultado.getString("title_job"), resultado.getString("trade_name_company"),
                resultado.getString("first_name_candidate"), resultado.getString("last_name_candidate"),
                resultado.getString("status_application"), resultado.getTimestamp("submitted_at_application").toInstant(),
                resultado.getTimestamp("updated_at_application").toInstant());
    }
}
