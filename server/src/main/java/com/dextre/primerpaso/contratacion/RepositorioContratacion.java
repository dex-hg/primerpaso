package com.dextre.primerpaso.contratacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.primerpaso.contratacion.DatosContratacion.Oferta;
import com.dextre.primerpaso.contratacion.DatosContratacion.Postulacion;
import com.dextre.primerpaso.contratacion.DatosContratacion.Proceso;
import com.dextre.primerpaso.contratacion.DatosContratacion.Progreso;
import com.dextre.primerpaso.contratacion.DatosContratacion.SolicitudOferta;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

@Repository
public class RepositorioContratacion {

    private static final String CAMPOS_PROCESO = """
            SELECT j.id_job, j.status_job, j.motivo_fin_proceso, j.detalle_cancelacion,
                j.plazas, j.updated_at_job, e.active_company
            FROM jobs j JOIN companies e ON e.id_company = j.company_id_job
            """;
    private static final String CAMPOS_OFERTA = """
            SELECT id_oferta, condiciones, fecha_inicio, vence_en, enviada_en, respondida_en,
                CASE WHEN estado = 'pendiente' AND vence_en <= statement_timestamp()
                    THEN 'vencida' ELSE estado END AS estado,
                CASE WHEN estado = 'pendiente' AND vence_en <= statement_timestamp()
                    THEN vence_en ELSE resuelta_en END AS resuelta_en
            FROM ofertas_contratacion WHERE id_postulacion = ?
            """;
    private static final String ACTIVAS = "a.status_application IN ('enviada', 'en_revision', 'seleccionada')";
    private final JdbcTemplate jdbc;

    public RepositorioContratacion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Proceso> procesoDePostulacion(UsuarioSesion usuario, long idPostulacion, boolean bloquear) {
        boolean empresa = "empresa".equals(usuario.tipoCuenta());
        String consulta = CAMPOS_PROCESO + """
                JOIN applications a ON a.job_id_application = j.id_job WHERE a.id_application = ?
                """ + (empresa ? " AND j.company_id_job = ?" : " AND a.candidate_id_application = ?")
                + (bloquear ? " FOR UPDATE OF j FOR SHARE OF e" : "");
        return jdbc.query(consulta, (resultado, fila) -> mapearProceso(resultado), idPostulacion,
                empresa ? usuario.idEmpresa() : usuario.idUsuario()).stream().findFirst();
    }

    Optional<Proceso> bloquearProceso(long idVacante, long idEmpresa) {
        return jdbc.query(CAMPOS_PROCESO + """
                WHERE j.id_job = ? AND j.company_id_job = ? FOR UPDATE OF j
                """, (resultado, fila) -> mapearProceso(resultado), idVacante, idEmpresa).stream().findFirst();
    }

    Postulacion consultarPostulacion(long idPostulacion, long idVacante, boolean bloquear) {
        return jdbc.queryForObject("""
                SELECT a.id_application, a.status_application, a.updated_at_application, u.status_user = 'activo'
                FROM applications a JOIN users u ON u.id_user = a.candidate_id_application
                WHERE a.id_application = ? AND a.job_id_application = ?
                """ + (bloquear ? " FOR UPDATE OF a FOR SHARE OF u" : ""),
                (resultado, fila) -> new Postulacion(resultado.getLong(1), resultado.getString(2),
                        resultado.getTimestamp(3).toInstant(), resultado.getBoolean(4)), idPostulacion, idVacante);
    }

    Progreso progreso(Proceso proceso) {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM applications a
                        WHERE a.job_id_application = ? AND a.status_application = 'contratada'),
                    (SELECT count(*) FROM ofertas_contratacion o JOIN applications a ON a.id_application = o.id_postulacion
                        WHERE a.job_id_application = ?
                        AND (o.estado = 'aceptada' OR (o.estado = 'pendiente' AND o.vence_en > statement_timestamp()))),
                    (SELECT count(*) FROM applications a WHERE a.job_id_application = ? AND
                """ + ACTIVAS + ")",
                (resultado, fila) -> new Progreso(proceso.plazas(), resultado.getLong(1), resultado.getLong(2),
                        resultado.getLong(3), proceso.version()), proceso.id(), proceso.id(), proceso.id());
    }

    List<Oferta> ofertas(long idPostulacion) {
        return jdbc.query(CAMPOS_OFERTA + " ORDER BY id_oferta DESC", this::mapearOferta, idPostulacion);
    }

    Optional<Oferta> buscarOferta(long idPostulacion, long idOferta) {
        return jdbc.query(CAMPOS_OFERTA + " AND id_oferta = ?", this::mapearOferta, idPostulacion, idOferta)
                .stream().findFirst();
    }

    Optional<Oferta> buscarEnvio(long idPostulacion, UUID idEnvio) {
        return jdbc.query(CAMPOS_OFERTA + " AND id_envio = ?", this::mapearOferta, idPostulacion, idEnvio)
                .stream().findFirst();
    }

    boolean tieneReserva(long idPostulacion) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM ofertas_contratacion WHERE id_postulacion = ?
                    AND (estado = 'aceptada' OR (estado = 'pendiente' AND vence_en > statement_timestamp())))
                """, Boolean.class, idPostulacion));
    }

    boolean tieneOfertasAceptadas(long idVacante) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM ofertas_contratacion o JOIN applications a ON a.id_application = o.id_postulacion
                    WHERE a.job_id_application = ? AND o.estado = 'aceptada')
                """, Boolean.class, idVacante));
    }

    Optional<Long> crearOferta(long idPostulacion, long idEmisor, SolicitudOferta datos) {
        Timestamp vencimiento = Timestamp.from(datos.venceEn());
        return jdbc.query("""
                WITH instante AS (SELECT clock_timestamp() AS marca)
                INSERT INTO ofertas_contratacion (id_postulacion, id_emisor, id_envio, condiciones,
                    fecha_inicio, vence_en, enviada_en)
                SELECT ?, ?, ?, ?, ?, ?, marca FROM instante WHERE ?::timestamptz > marca
                RETURNING id_oferta
                """, (resultado, fila) -> resultado.getLong(1), idPostulacion, idEmisor, datos.idEnvio(),
                datos.condiciones(), datos.fechaInicio(), vencimiento, vencimiento).stream().findFirst();
    }

    boolean cambiarOferta(long idOferta, String actual, String nuevo, long idResponsable) {
        boolean responde = "aceptada".equals(nuevo) || "rechazada".equals(nuevo);
        boolean resuelve = !"aceptada".equals(nuevo);
        return jdbc.update("""
                WITH instante AS (SELECT clock_timestamp() AS marca)
                UPDATE ofertas_contratacion o SET estado = ?,
                    respondida_en = CASE WHEN ? THEN marca ELSE o.respondida_en END,
                    resuelta_en = CASE WHEN ? THEN marca END,
                    id_responsable_resolucion = CASE WHEN ? THEN ?::bigint END
                FROM instante WHERE o.id_oferta = ? AND o.estado = ?
                    AND (o.estado <> 'pendiente' OR o.vence_en > marca)
                """, nuevo, responde, resuelve, resuelve, idResponsable, idOferta, actual) == 1;
    }

    void vencerOfertas(long idPostulacion) {
        jdbc.update("""
                UPDATE ofertas_contratacion SET estado = 'vencida', resuelta_en = vence_en
                WHERE id_postulacion = ? AND estado = 'pendiente' AND vence_en <= statement_timestamp()
                """, idPostulacion);
    }

    void retirarOfertasDePostulacion(long idPostulacion, long idResponsable) {
        retirarReservas("o.id_postulacion = ?", idPostulacion, idResponsable);
    }

    void retirarOfertasPendientesDeVacante(long idVacante, long idResponsable) {
        retirarReservas("""
                o.estado = 'pendiente' AND EXISTS (SELECT 1 FROM applications a
                    WHERE a.id_application = o.id_postulacion AND a.job_id_application = ?)
                """, idVacante, idResponsable);
    }

    private void retirarReservas(String filtro, long identificador, long idResponsable) {
        jdbc.update("""
                WITH instante AS (SELECT statement_timestamp() AS marca)
                UPDATE ofertas_contratacion o SET
                    estado = CASE WHEN o.estado = 'pendiente' AND o.vence_en <= marca THEN 'vencida' ELSE 'retirada' END,
                    resuelta_en = CASE WHEN o.estado = 'pendiente' AND o.vence_en <= marca THEN o.vence_en ELSE marca END,
                    id_responsable_resolucion = CASE WHEN o.estado = 'aceptada' OR o.vence_en > marca THEN ? END
                FROM instante WHERE o.estado IN ('pendiente', 'aceptada') AND
                """ + filtro, idResponsable, identificador);
    }

    void actualizarPostulacion(long idPostulacion, String estado) {
        exigirUnaFila(jdbc.update("""
                UPDATE applications SET status_application = COALESCE(?, status_application),
                    updated_at_application = GREATEST(clock_timestamp(), updated_at_application + interval '1 microsecond')
                WHERE id_application = ?
                """, estado, idPostulacion));
    }

    void actualizarVersionVacante(long idVacante) {
        exigirUnaFila(jdbc.update("""
                UPDATE jobs SET updated_at_job = GREATEST(clock_timestamp(), updated_at_job + interval '1 microsecond')
                WHERE id_job = ?
                """, idVacante));
    }

    void cerrarProceso(long idVacante, String motivo, String detalle, long idResponsable) {
        exigirUnaFila(jdbc.update("""
                UPDATE jobs SET status_job = 'cerrada', motivo_fin_proceso = ?, detalle_cancelacion = ?,
                    fecha_fin_proceso = clock_timestamp(), id_responsable_fin_proceso = ?,
                    updated_at_job = GREATEST(clock_timestamp(), updated_at_job + interval '1 microsecond')
                WHERE id_job = ? AND motivo_fin_proceso IS NULL
                """, motivo, detalle, idResponsable, idVacante));
    }

    void finalizarPostulaciones(long idVacante, String motivo) {
        jdbc.update("""
                WITH finalizadas AS (
                    UPDATE applications a SET status_application = 'finalizada',
                        updated_at_application = GREATEST(clock_timestamp(), a.updated_at_application + interval '1 microsecond')
                    WHERE a.job_id_application = ? AND
                """ + ACTIVAS + """
                    RETURNING id_application
                )
                INSERT INTO avisos_contratacion (id_postulacion, destinatario, tipo)
                SELECT id_application, 'postulante', ? FROM finalizadas
                """, idVacante, motivo);
    }

    void avisar(long idPostulacion, String destinatario, String tipo) {
        jdbc.update("INSERT INTO avisos_contratacion (id_postulacion, destinatario, tipo) VALUES (?, ?, ?)",
                idPostulacion, destinatario, tipo);
    }

    private Proceso mapearProceso(ResultSet resultado) throws SQLException {
        return new Proceso(resultado.getLong("id_job"), resultado.getString("status_job"),
                resultado.getString("motivo_fin_proceso"), resultado.getString("detalle_cancelacion"),
                resultado.getInt("plazas"), leerFecha(resultado, "updated_at_job"), resultado.getBoolean("active_company"));
    }

    private Oferta mapearOferta(ResultSet resultado, int fila) throws SQLException {
        return new Oferta(resultado.getLong("id_oferta"), resultado.getString("condiciones"),
                resultado.getObject("fecha_inicio", LocalDate.class), leerFecha(resultado, "vence_en"),
                resultado.getString("estado"), leerFecha(resultado, "enviada_en"),
                leerFecha(resultado, "respondida_en"), leerFecha(resultado, "resuelta_en"));
    }

    private Instant leerFecha(ResultSet resultado, String columna) throws SQLException {
        Timestamp fecha = resultado.getTimestamp(columna);
        return fecha == null ? null : fecha.toInstant();
    }

    private void exigirUnaFila(int filas) {
        if (filas != 1) throw new IllegalStateException("No se pudo actualizar el proceso bloqueado.");
    }
}
