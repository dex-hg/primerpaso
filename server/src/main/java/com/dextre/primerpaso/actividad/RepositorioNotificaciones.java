package com.dextre.primerpaso.actividad;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.primerpaso.actividad.DatosActividad.Notificacion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

@Repository
public class RepositorioNotificaciones {

    private static final String ORIGEN = """
            FROM avisos_contratacion n JOIN applications a ON a.id_application = n.id_postulacion
            JOIN jobs j ON j.id_job = a.job_id_application
            """;
    private static final String LECTURAS = """
            LEFT JOIN lecturas_notificaciones l ON l.id_aviso = n.id_aviso AND l.id_usuario = ?
            """;
    private static final String GUARDAR_LECTURAS = "INSERT INTO lecturas_notificaciones (id_aviso, id_usuario) ";
    private static final String CONSERVAR_LECTURA = " ON CONFLICT (id_aviso, id_usuario) DO NOTHING";
    private final JdbcTemplate jdbc;

    public RepositorioNotificaciones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Notificacion> listar(UsuarioSesion usuario, boolean soloPendientes, long desplazamiento, int limite) {
        String consulta = """
                SELECT n.id_aviso, n.id_postulacion, j.title_job, e.trade_name_company,
                    n.tipo, n.fecha_aviso, l.id_aviso IS NOT NULL AS leida
                """ + ORIGEN + """
                JOIN companies e ON e.id_company = j.company_id_job
                """ + LECTURAS + filtro(usuario) + (soloPendientes ? " AND l.id_aviso IS NULL" : "")
                + " ORDER BY n.fecha_aviso DESC, n.id_aviso DESC LIMIT ? OFFSET ?";
        return jdbc.query(consulta, (resultado, fila) -> new Notificacion(resultado.getLong("id_aviso"),
                resultado.getLong("id_postulacion"), resultado.getString("title_job"),
                resultado.getString("trade_name_company"), resultado.getString("tipo"),
                resultado.getTimestamp("fecha_aviso").toInstant(), resultado.getBoolean("leida")),
                usuario.idUsuario(), usuario.tipoCuenta(), titular(usuario), limite, desplazamiento);
    }

    long pendientes(UsuarioSesion usuario) {
        return jdbc.queryForObject("SELECT count(*) " + ORIGEN + LECTURAS + filtro(usuario)
                + " AND l.id_aviso IS NULL", Long.class, usuario.idUsuario(), usuario.tipoCuenta(), titular(usuario));
    }

    Optional<Long> marcarLeida(UsuarioSesion usuario, long idAviso) {
        String consulta = "WITH visible AS (SELECT n.id_aviso, n.id_postulacion " + ORIGEN + filtro(usuario)
                + " AND n.id_aviso = ?), guardada AS (" + GUARDAR_LECTURAS
                + "SELECT id_aviso, ? FROM visible" + CONSERVAR_LECTURA + ") SELECT id_postulacion FROM visible";
        return jdbc.query(consulta, (resultado, fila) -> resultado.getLong(1),
                usuario.tipoCuenta(), titular(usuario), idAviso, usuario.idUsuario()).stream().findFirst();
    }

    void marcarTodasLeidas(UsuarioSesion usuario) {
        jdbc.update(GUARDAR_LECTURAS + "SELECT n.id_aviso, ? " + ORIGEN + LECTURAS + filtro(usuario)
                + " AND l.id_aviso IS NULL ORDER BY n.id_aviso" + CONSERVAR_LECTURA,
                usuario.idUsuario(), usuario.idUsuario(), usuario.tipoCuenta(), titular(usuario));
    }

    private String filtro(UsuarioSesion usuario) {
        return " WHERE n.destinatario = ? AND "
                + ("empresa".equals(usuario.tipoCuenta()) ? "j.company_id_job = ?" : "a.candidate_id_application = ?");
    }

    private long titular(UsuarioSesion usuario) {
        return "empresa".equals(usuario.tipoCuenta()) ? usuario.idEmpresa() : usuario.idUsuario();
    }
}
