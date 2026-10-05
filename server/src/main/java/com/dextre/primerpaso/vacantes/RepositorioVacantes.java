package com.dextre.primerpaso.vacantes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.primerpaso.vacantes.DatosVacantes.Catalogos;
import com.dextre.primerpaso.vacantes.DatosVacantes.Habilidad;
import com.dextre.primerpaso.vacantes.DatosVacantes.OpcionCatalogo;
import com.dextre.primerpaso.vacantes.DatosVacantes.SolicitudVacante;
import com.dextre.primerpaso.vacantes.DatosVacantes.Vacante;

@Repository
public class RepositorioVacantes {

    private static final String CONSULTA_VACANTES = """
            SELECT j.id_job, j.title_job, j.description_job, j.career_id_job, c.name_career,
                j.work_mode_job, j.city_job, j.country_code_job, j.status_job,
                j.created_at_job, j.published_at_job, j.expires_at_job, j.updated_at_job
            FROM jobs j JOIN careers c ON c.id_career = j.career_id_job
            WHERE j.company_id_job = ?
            """;
    private final JdbcTemplate plantillaJdbc;

    public RepositorioVacantes(JdbcTemplate plantillaJdbc) {
        this.plantillaJdbc = plantillaJdbc;
    }

    public boolean membresiaActiva(long idUsuario, long idEmpresa, boolean bloquear) {
        String consulta = """
                SELECT m.role_company_member FROM company_members m
                JOIN companies e ON e.id_company = m.company_id_company_member
                JOIN users u ON u.id_user = m.user_id_company_member
                WHERE m.user_id_company_member = ? AND m.company_id_company_member = ?
                    AND m.active_company_member AND e.active_company AND u.status_user = 'activo'
                    AND m.role_company_member IN ('administrador', 'reclutador')
                """ + (bloquear ? " FOR SHARE OF m, e, u" : "");
        return !plantillaJdbc.query(consulta, (resultado, fila) -> resultado.getString(1),
                idUsuario, idEmpresa).isEmpty();
    }

    public boolean bloquearCarreraActiva(long idCarrera) {
        return !plantillaJdbc.query("""
                SELECT id_career FROM careers
                WHERE id_career = ? AND active_career FOR SHARE
                """, (resultado, fila) -> resultado.getLong(1), idCarrera).isEmpty();
    }

    public int bloquearHabilidadesActivas(List<Long> habilidades) {
        String marcadores = String.join(",", Collections.nCopies(habilidades.size(), "?"));
        return plantillaJdbc.query("SELECT id_skill FROM skills WHERE active_skill AND id_skill IN ("
                + marcadores + ") ORDER BY id_skill FOR SHARE", (resultado, fila) -> resultado.getLong(1),
                habilidades.toArray()).size();
    }

    public Catalogos catalogos() {
        List<OpcionCatalogo> carreras = plantillaJdbc.query("""
                SELECT id_career, name_career FROM careers WHERE active_career
                ORDER BY lower(name_career), id_career
                """, (resultado, fila) -> new OpcionCatalogo(resultado.getLong(1), resultado.getString(2)));
        List<OpcionCatalogo> habilidades = plantillaJdbc.query("""
                SELECT id_skill, name_skill FROM skills WHERE active_skill
                ORDER BY lower(name_skill), id_skill
                """, (resultado, fila) -> new OpcionCatalogo(resultado.getLong(1), resultado.getString(2)));
        return new Catalogos(carreras, habilidades);
    }

    public long crear(long idEmpresa, long idUsuario, SolicitudVacante datos) {
        List<Long> identificadores = plantillaJdbc.query("""
                INSERT INTO jobs (company_id_job, creator_user_id_job, career_id_job,
                    title_job, description_job, work_mode_job, city_job, country_code_job,
                    expires_at_job, status_job)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, 'borrador'
                WHERE ?::timestamptz > clock_timestamp() RETURNING id_job
                """, (resultado, fila) -> resultado.getLong(1), idEmpresa, idUsuario, datos.idCarrera(), datos.titulo(),
                datos.descripcion(), datos.modalidad(), datos.ciudad(), datos.codigoPais(),
                Timestamp.from(datos.fechaVencimiento()), Timestamp.from(datos.fechaVencimiento()));
        if (identificadores.isEmpty()) {
            throw new ReglaVacanteException(409, "La fecha de vencimiento ya pasó. Actualízala antes de guardar.");
        }
        return identificadores.getFirst();
    }

    public void editar(long idEmpresa, long idVacante, SolicitudVacante datos) {
        int filas = plantillaJdbc.update("""
                WITH instante AS (SELECT clock_timestamp() AS marca)
                UPDATE jobs j SET career_id_job = ?, title_job = ?, description_job = ?,
                    work_mode_job = ?, city_job = ?, country_code_job = ?, expires_at_job = ?,
                    updated_at_job = GREATEST(instante.marca, j.updated_at_job + interval '1 microsecond')
                FROM instante WHERE j.id_job = ? AND j.company_id_job = ?
                    AND ?::timestamptz > instante.marca
                    AND (j.status_job = 'borrador' OR (j.status_job = 'publicada'
                        AND (j.expires_at_job IS NULL OR j.expires_at_job > instante.marca)))
                """, datos.idCarrera(), datos.titulo(), datos.descripcion(), datos.modalidad(),
                datos.ciudad(), datos.codigoPais(), Timestamp.from(datos.fechaVencimiento()),
                idVacante, idEmpresa, Timestamp.from(datos.fechaVencimiento()));
        exigirActualizacion(filas);
    }

    public void sustituirHabilidades(long idEmpresa, long idVacante, List<Long> habilidades) {
        plantillaJdbc.update("""
                DELETE FROM job_skills js USING jobs j WHERE js.job_id_job_skill = j.id_job
                    AND j.id_job = ? AND j.company_id_job = ?
                """, idVacante, idEmpresa);
        List<Object[]> parametros = habilidades.stream()
                .map(idHabilidad -> new Object[] { idVacante, idHabilidad, idVacante, idEmpresa }).toList();
        plantillaJdbc.batchUpdate("""
                INSERT INTO job_skills (job_id_job_skill, skill_id_job_skill, required_job_skill)
                SELECT ?, ?, TRUE WHERE EXISTS
                    (SELECT 1 FROM jobs WHERE id_job = ? AND company_id_job = ?)
                """, parametros);
    }

    public void publicar(long idEmpresa, long idVacante) {
        int filas = plantillaJdbc.update("""
                WITH instante AS (SELECT clock_timestamp() AS marca)
                UPDATE jobs j SET status_job = 'publicada',
                    published_at_job = GREATEST(instante.marca, j.created_at_job),
                    updated_at_job = GREATEST(instante.marca, j.updated_at_job + interval '1 microsecond')
                FROM instante WHERE j.id_job = ? AND j.company_id_job = ?
                    AND j.status_job = 'borrador'
                    AND j.expires_at_job > GREATEST(instante.marca, j.created_at_job)
                """, idVacante, idEmpresa);
        if (filas != 1) {
            throw new ReglaVacanteException(409,
                    "La vacante venció antes de publicarse. Actualiza su fecha de vencimiento.");
        }
    }

    public void cerrar(long idEmpresa, long idVacante) {
        int filas = plantillaJdbc.update("""
                UPDATE jobs SET status_job = 'cerrada',
                    updated_at_job = GREATEST(clock_timestamp(), updated_at_job + interval '1 microsecond')
                WHERE id_job = ? AND company_id_job = ? AND status_job = 'publicada'
                """, idVacante, idEmpresa);
        exigirActualizacion(filas);
    }

    public Optional<Vacante> consultar(long idEmpresa, long idVacante, boolean bloquear, Instant ahora) {
        List<Vacante> vacantes = plantillaJdbc.query(CONSULTA_VACANTES + " AND j.id_job = ?"
                + (bloquear ? " FOR UPDATE OF j" : ""),
                (resultado, fila) -> mapearVacante(resultado, ahora), idEmpresa, idVacante);
        return vacantes.stream().findFirst().map(vacante -> incluirHabilidades(idEmpresa, vacante));
    }

    public long contar(long idEmpresa, String estado, Instant ahora) {
        List<Object> parametros = new ArrayList<>(List.of(idEmpresa));
        String filtro = filtroEstado(estado, ahora, parametros);
        Long total = plantillaJdbc.queryForObject("SELECT count(*) FROM jobs j WHERE j.company_id_job = ?"
                + filtro, Long.class, parametros.toArray());
        return total == null ? 0 : total;
    }

    public List<Vacante> listar(long idEmpresa, int pagina, int tamanio, String estado, Instant ahora) {
        List<Object> parametros = new ArrayList<>(List.of(idEmpresa));
        String filtro = filtroEstado(estado, ahora, parametros);
        parametros.add(tamanio);
        parametros.add((long) pagina * tamanio);
        List<Vacante> vacantes = plantillaJdbc.query(CONSULTA_VACANTES + filtro
                + " ORDER BY j.created_at_job DESC, j.id_job DESC LIMIT ? OFFSET ?",
                (resultado, fila) -> mapearVacante(resultado, ahora), parametros.toArray());
        return vacantes.stream().map(vacante -> incluirHabilidades(idEmpresa, vacante)).toList();
    }

    private String filtroEstado(String estado, Instant ahora, List<Object> parametros) {
        return switch (estado) {
            case "todos" -> "";
            case "borrador", "cerrada" -> {
                parametros.add(estado);
                yield " AND j.status_job = ?";
            }
            case "publicada" -> {
                parametros.add(Timestamp.from(ahora));
                yield " AND j.status_job = 'publicada' AND (j.expires_at_job IS NULL OR j.expires_at_job > ?)";
            }
            case "vencida" -> {
                parametros.add(Timestamp.from(ahora));
                yield " AND j.status_job = 'publicada' AND j.expires_at_job <= ?";
            }
            default -> throw new IllegalArgumentException("Filtro de vacantes desconocido.");
        };
    }

    private Vacante mapearVacante(ResultSet resultado, Instant ahora) throws SQLException {
        Instant vencimiento = leerInstante(resultado, "expires_at_job");
        String estado = resultado.getString("status_job");
        boolean vencida = "publicada".equals(estado) && vencimiento != null && !vencimiento.isAfter(ahora);
        return new Vacante(resultado.getLong("id_job"), resultado.getString("title_job"),
                resultado.getString("description_job"), resultado.getLong("career_id_job"),
                resultado.getString("name_career"), List.of(), resultado.getString("work_mode_job"),
                resultado.getString("city_job"), resultado.getString("country_code_job"), estado,
                leerInstante(resultado, "created_at_job"), leerInstante(resultado, "published_at_job"),
                vencimiento, leerInstante(resultado, "updated_at_job"), vencida);
    }

    private Vacante incluirHabilidades(long idEmpresa, Vacante vacante) {
        List<Habilidad> habilidades = plantillaJdbc.query("""
                SELECT s.id_skill, s.name_skill FROM job_skills js
                JOIN skills s ON s.id_skill = js.skill_id_job_skill
                JOIN jobs j ON j.id_job = js.job_id_job_skill
                WHERE j.id_job = ? AND j.company_id_job = ? ORDER BY lower(s.name_skill), s.id_skill
                """, (resultado, fila) -> new Habilidad(resultado.getLong(1), resultado.getString(2)),
                vacante.id(), idEmpresa);
        return new Vacante(vacante.id(), vacante.titulo(), vacante.descripcion(), vacante.idCarrera(),
                vacante.nombreCarrera(), habilidades, vacante.modalidad(), vacante.ciudad(),
                vacante.codigoPais(), vacante.estado(), vacante.fechaCreacion(), vacante.fechaPublicacion(),
                vacante.fechaVencimiento(), vacante.version(), vacante.vencida());
    }

    private Instant leerInstante(ResultSet resultado, String columna) throws SQLException {
        Timestamp fecha = resultado.getTimestamp(columna);
        return fecha == null ? null : fecha.toInstant();
    }

    private void exigirActualizacion(int filas) {
        if (filas != 1) {
            throw new ReglaVacanteException(409, "La vacante cambió. Recarga la página e inténtalo nuevamente.");
        }
    }
}
