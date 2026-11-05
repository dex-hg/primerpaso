package com.dextre.primerpaso.actividad;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.primerpaso.actividad.DatosActividad.Accion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

@Repository
public class RepositorioHistorial {

    private static final String POSTULANTE = """
            WITH propias AS (
                SELECT a.id_application AS id_postulacion, j.id_job AS id_vacante,
                    j.title_job AS titulo, e.trade_name_company AS empresa,
                    a.submitted_at_application, a.updated_at_application, a.status_application
                FROM applications a JOIN jobs j ON j.id_job = a.job_id_application
                JOIN companies e ON e.id_company = j.company_id_job
                WHERE a.candidate_id_application = ?
            )
            SELECT id_postulacion, id_vacante, titulo, empresa, 'postulaste' AS tipo,
                submitted_at_application AS fecha, id_postulacion AS referencia
            FROM propias
            UNION ALL
            SELECT p.id_postulacion, p.id_vacante, p.titulo, p.empresa,
                CASE WHEN o.estado = 'rechazada' THEN 'rechazaste' ELSE 'aceptaste' END,
                o.respondida_en, o.id_oferta
            FROM propias p JOIN ofertas_contratacion o ON o.id_postulacion = p.id_postulacion
            WHERE o.respondida_en IS NOT NULL
            UNION ALL
            SELECT p.id_postulacion, p.id_vacante, p.titulo, p.empresa,
                'contratacion_confirmada', o.resuelta_en, o.id_oferta
            FROM propias p JOIN ofertas_contratacion o ON o.id_postulacion = p.id_postulacion
            WHERE o.estado = 'contratada'
            UNION ALL
            SELECT id_postulacion, id_vacante, titulo, empresa, 'retiraste', updated_at_application, id_postulacion
            FROM propias WHERE status_application = 'retirada'
            """;

    private static final String EMPRESA = """
            WITH propias AS (
                SELECT j.id_job AS id_vacante, j.title_job AS titulo, e.trade_name_company AS empresa,
                    j.creator_user_id_job, j.created_at_job, j.motivo_fin_proceso, j.fecha_fin_proceso,
                    j.id_responsable_fin_proceso
                FROM jobs j JOIN companies e ON e.id_company = j.company_id_job
                WHERE j.company_id_job = ?
            ),
            ofertas AS (
                SELECT a.id_application AS id_postulacion, p.id_vacante, p.titulo, p.empresa,
                    o.id_oferta, o.id_emisor, o.id_responsable_resolucion, o.enviada_en, o.resuelta_en, o.estado
                FROM propias p JOIN applications a ON a.job_id_application = p.id_vacante
                JOIN ofertas_contratacion o ON o.id_postulacion = a.id_application
            )
            SELECT NULL::bigint AS id_postulacion, id_vacante, titulo, empresa, 'creaste' AS tipo,
                created_at_job AS fecha, id_vacante AS referencia
            FROM propias WHERE creator_user_id_job = ?
            UNION ALL
            SELECT id_postulacion, id_vacante, titulo, empresa, 'enviaste', enviada_en, id_oferta
            FROM ofertas WHERE id_emisor = ?
            UNION ALL
            SELECT id_postulacion, id_vacante, titulo, empresa, 'confirmaste', resuelta_en, id_oferta
            FROM ofertas WHERE estado = 'contratada' AND id_responsable_resolucion = ?
            UNION ALL
            SELECT NULL::bigint, id_vacante, titulo, empresa, 'cancelaste', fecha_fin_proceso, id_vacante
            FROM propias WHERE motivo_fin_proceso = 'proceso_cancelado' AND id_responsable_fin_proceso = ?
            """;
    private final JdbcTemplate jdbc;

    public RepositorioHistorial(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Accion> listar(UsuarioSesion usuario, long desplazamiento, int limite) {
        boolean empresa = "empresa".equals(usuario.tipoCuenta());
        long idUsuario = usuario.idUsuario();
        Object[] parametros = empresa
                ? new Object[] {usuario.idEmpresa(), idUsuario, idUsuario, idUsuario, idUsuario, limite, desplazamiento}
                : new Object[] {idUsuario, limite, desplazamiento};
        return jdbc.query((empresa ? EMPRESA : POSTULANTE) + " ORDER BY fecha DESC, referencia DESC, tipo LIMIT ? OFFSET ?",
                (resultado, fila) -> new Accion(resultado.getObject("id_postulacion", Long.class),
                        resultado.getLong("id_vacante"), resultado.getString("titulo"), resultado.getString("empresa"),
                        resultado.getString("tipo"), resultado.getTimestamp("fecha").toInstant()), parametros);
    }
}
