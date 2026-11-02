package com.dextre.primerpaso.chat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.primerpaso.chat.DatosChat.Contexto;
import com.dextre.primerpaso.chat.DatosChat.Conversacion;
import com.dextre.primerpaso.chat.DatosChat.Mensaje;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

@Repository
public class RepositorioChat {

    private static final String ORIGEN = """
            FROM applications a JOIN jobs j ON j.id_job = a.job_id_application
            JOIN companies e ON e.id_company = j.company_id_job
            JOIN candidates c ON c.id_candidate = a.candidate_id_application
            JOIN users u ON u.id_user = c.id_candidate
            """;
    private static final String COLUMNAS_MENSAJE = """
            id_mensaje, id_emisor, id_envio, nombre_emisor, tipo_emisor, contenido, fecha_envio
            """;
    private final JdbcTemplate plantillaJdbc;

    public RepositorioChat(JdbcTemplate plantillaJdbc) {
        this.plantillaJdbc = plantillaJdbc;
    }

    public Optional<Contexto> consultarContexto(UsuarioSesion usuario, long idPostulacion, boolean bloquear) {
        String consulta = """
                SELECT a.id_application, j.id_job, j.title_job, e.trade_name_company,
                    c.first_name_candidate || ' ' || c.last_name_candidate AS nombre_postulante,
                    a.status_application, (e.active_company AND u.status_user = 'activo') AS participantes_activos
                """ + ORIGEN + filtro(usuario) + " AND a.id_application = ?"
                + (bloquear ? " FOR UPDATE OF a FOR SHARE OF e, u" : "");
        return plantillaJdbc.query(consulta, (resultado, fila) -> new Contexto(
                resultado.getLong("id_application"), resultado.getLong("id_job"), resultado.getString("title_job"),
                resultado.getString("trade_name_company"), resultado.getString("nombre_postulante"),
                resultado.getString("status_application"), resultado.getBoolean("participantes_activos")),
                identificadorAcceso(usuario), idPostulacion).stream().findFirst();
    }

    public boolean estaIniciada(long idPostulacion) {
        return Boolean.TRUE.equals(plantillaJdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM mensajes_chat WHERE id_postulacion = ?)", Boolean.class, idPostulacion));
    }

    public List<Mensaje> listarMensajes(long idPostulacion, Long antesDe, Long despuesDe, int limite) {
        String condicion = antesDe != null ? " AND id_mensaje < ?" : despuesDe != null ? " AND id_mensaje > ?" : "";
        String orden = despuesDe != null ? " ASC" : " DESC";
        String consulta = "SELECT " + COLUMNAS_MENSAJE + " FROM mensajes_chat WHERE id_postulacion = ?"
                + condicion + " ORDER BY id_mensaje" + orden + " LIMIT ?";
        Object[] parametros = antesDe != null || despuesDe != null
                ? new Object[] {idPostulacion, antesDe != null ? antesDe : despuesDe, limite}
                : new Object[] {idPostulacion, limite};
        return plantillaJdbc.query(consulta, (resultado, fila) -> mapearMensaje(resultado), parametros);
    }

    public Optional<Mensaje> buscarEnvio(long idPostulacion, long idEmisor, UUID idEnvio) {
        return plantillaJdbc.query("SELECT " + COLUMNAS_MENSAJE + """
                FROM mensajes_chat WHERE id_postulacion = ? AND id_emisor = ? AND id_envio = ?
                """, (resultado, fila) -> mapearMensaje(resultado), idPostulacion, idEmisor, idEnvio).stream().findFirst();
    }

    public boolean excedeFrecuencia(long idPostulacion, long idEmisor) {
        Long cantidad = plantillaJdbc.queryForObject("""
                SELECT count(*) FROM mensajes_chat WHERE id_postulacion = ? AND id_emisor = ?
                    AND fecha_envio > clock_timestamp() - interval '1 minute'
                """, Long.class, idPostulacion, idEmisor);
        return cantidad != null && cantidad >= 20;
    }

    public Mensaje guardar(long idPostulacion, UsuarioSesion usuario, UUID idEnvio, String contenido) {
        return plantillaJdbc.queryForObject("""
                INSERT INTO mensajes_chat (id_postulacion, id_emisor, id_envio, nombre_emisor, tipo_emisor, contenido)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING
                """ + COLUMNAS_MENSAJE, (resultado, fila) -> mapearMensaje(resultado), idPostulacion,
                usuario.idUsuario(), idEnvio, (usuario.nombres() + " " + usuario.apellidos()).strip(),
                usuario.tipoCuenta(), contenido);
    }

    public long contarConversaciones(UsuarioSesion usuario) {
        Long total = plantillaJdbc.queryForObject("SELECT count(*) " + ORIGEN + filtro(usuario)
                + " AND EXISTS(SELECT 1 FROM mensajes_chat m WHERE m.id_postulacion = a.id_application)",
                Long.class, identificadorAcceso(usuario));
        return total == null ? 0 : total;
    }

    public List<Conversacion> listarConversaciones(UsuarioSesion usuario, int pagina, int tamanio) {
        return plantillaJdbc.query("""
                SELECT a.id_application, j.title_job, e.trade_name_company,
                    c.first_name_candidate || ' ' || c.last_name_candidate AS nombre_postulante,
                    a.status_application, ultimo.contenido, ultimo.fecha_envio
                """ + ORIGEN + """
                JOIN LATERAL (SELECT m.contenido, m.fecha_envio, m.id_mensaje FROM mensajes_chat m
                    WHERE m.id_postulacion = a.id_application ORDER BY m.id_mensaje DESC LIMIT 1) ultimo ON TRUE
                """ + filtro(usuario) + " ORDER BY ultimo.fecha_envio DESC, ultimo.id_mensaje DESC LIMIT ? OFFSET ?",
                (resultado, fila) -> new Conversacion(resultado.getLong("id_application"),
                        resultado.getString("title_job"), resultado.getString("trade_name_company"),
                        resultado.getString("nombre_postulante"), resultado.getString("status_application"),
                        resultado.getString("contenido"), resultado.getTimestamp("fecha_envio").toInstant()),
                identificadorAcceso(usuario), tamanio, (long) pagina * tamanio);
    }

    private String filtro(UsuarioSesion usuario) {
        return "empresa".equals(usuario.tipoCuenta()) ? " WHERE j.company_id_job = ?" : " WHERE a.candidate_id_application = ?";
    }

    private long identificadorAcceso(UsuarioSesion usuario) {
        return "empresa".equals(usuario.tipoCuenta()) ? usuario.idEmpresa() : usuario.idUsuario();
    }

    private Mensaje mapearMensaje(ResultSet resultado) throws SQLException {
        return new Mensaje(resultado.getLong("id_mensaje"), resultado.getLong("id_emisor"),
                resultado.getObject("id_envio", UUID.class), resultado.getString("nombre_emisor"),
                resultado.getString("tipo_emisor"), resultado.getString("contenido"),
                resultado.getTimestamp("fecha_envio").toInstant());
    }
}
