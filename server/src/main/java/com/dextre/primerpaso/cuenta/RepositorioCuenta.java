package com.dextre.primerpaso.cuenta;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.primerpaso.cuenta.DatosCuenta.Cuenta;
import com.dextre.primerpaso.cuenta.DatosCuenta.DatosPersonales;
import com.dextre.primerpaso.cuenta.DatosCuenta.Empresa;
import com.dextre.primerpaso.cuenta.DatosCuenta.Formacion;
import com.dextre.primerpaso.cuenta.DatosCuenta.SolicitudCuenta;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.DatosVacantes.OpcionCatalogo;

@Repository
public class RepositorioCuenta {

    private final JdbcTemplate plantillaJdbc;

    public RepositorioCuenta(JdbcTemplate plantillaJdbc) {
        this.plantillaJdbc = plantillaJdbc;
    }

    public Optional<Cuenta> consultar(UsuarioSesion usuario, boolean bloquear) {
        return "postulante".equals(usuario.tipoCuenta())
                ? consultarPostulante(usuario, bloquear) : consultarEmpresa(usuario, bloquear);
    }

    private Optional<Cuenta> consultarPostulante(UsuarioSesion usuario, boolean bloquear) {
        return plantillaJdbc.query("""
                SELECT u.email_user, p.*, c.name_career,
                    md5(concat_ws('|', u.email_user, u.status_user, u.updated_at_user::text,
                        to_jsonb(p)::text)) AS version
                FROM users u JOIN candidates p ON p.id_candidate = u.id_user
                JOIN careers c ON c.id_career = p.career_id_candidate
                WHERE u.id_user = ? AND u.status_user = 'activo'
                """ + (bloquear ? " FOR UPDATE OF u, p" : ""), (resultado, fila) -> {
                    var datos = new DatosPersonales(resultado.getString("first_name_candidate"),
                            resultado.getString("last_name_candidate"), resultado.getString("email_user"),
                            resultado.getString("phone_candidate"));
                    var formacion = new Formacion(resultado.getString("city_candidate"),
                            resultado.getString("institution_candidate"), resultado.getLong("career_id_candidate"),
                            resultado.getString("name_career"), resultado.getString("academic_status_candidate"),
                            resultado.getObject("current_term_candidate", Integer.class),
                            resultado.getObject("graduation_year_candidate", Integer.class));
                    var identidad = new UsuarioSesion(usuario.idUsuario(), datos.nombres(), datos.apellidos(),
                            datos.correo(), "postulante", null, null, null);
                    return new Cuenta(identidad, datos, formacion, null, resultado.getString("version"), List.of());
                }, usuario.idUsuario()).stream().findFirst();
    }

    private Optional<Cuenta> consultarEmpresa(UsuarioSesion usuario, boolean bloquear) {
        return plantillaJdbc.query("""
                SELECT u.email_user, m.*, e.*,
                    md5(concat_ws('|', u.email_user, u.status_user, u.updated_at_user::text,
                        to_jsonb(m)::text, to_jsonb(e)::text)) AS version
                FROM users u JOIN company_members m ON m.user_id_company_member = u.id_user
                JOIN companies e ON e.id_company = m.company_id_company_member
                WHERE u.id_user = ? AND e.id_company = ? AND u.status_user = 'activo'
                    AND m.active_company_member AND e.active_company
                    AND m.role_company_member IN ('administrador', 'reclutador')
                """ + (bloquear ? " FOR UPDATE OF u, m, e" : ""), (resultado, fila) -> {
                    var datos = new DatosPersonales(resultado.getString("first_name_company_member"),
                            resultado.getString("last_name_company_member"), resultado.getString("email_user"),
                            resultado.getString("phone_company_member"));
                    var empresa = new Empresa(resultado.getString("trade_name_company"),
                            resultado.getString("industry_company"), resultado.getString("city_company"),
                            resultado.getString("website_company"), resultado.getString("country_code_company"),
                            resultado.getString("tax_id_company"));
                    var identidad = new UsuarioSesion(usuario.idUsuario(), datos.nombres(), datos.apellidos(),
                            datos.correo(), "empresa", usuario.idEmpresa(), empresa.nombreComercial(),
                            resultado.getString("role_company_member"));
                    return new Cuenta(identidad, datos, null, empresa, resultado.getString("version"), List.of());
                }, usuario.idUsuario(), usuario.idEmpresa()).stream().findFirst();
    }

    public List<OpcionCatalogo> carreras() {
        return plantillaJdbc.query("""
                SELECT id_career, name_career FROM careers WHERE active_career
                ORDER BY lower(name_career), id_career
                """, (resultado, fila) -> new OpcionCatalogo(resultado.getLong(1), resultado.getString(2)));
    }

    public boolean carreraActiva(long idCarrera) {
        return !plantillaJdbc.query("SELECT id_career FROM careers WHERE id_career = ? AND active_career FOR SHARE",
                (resultado, fila) -> resultado.getLong(1), idCarrera).isEmpty();
    }

    public void guardarPostulante(long idUsuario, SolicitudCuenta datos) {
        plantillaJdbc.update("""
                UPDATE candidates SET first_name_candidate = ?, last_name_candidate = ?, phone_candidate = ?,
                    city_candidate = ?, institution_candidate = ?, career_id_candidate = ?,
                    academic_status_candidate = ?, current_term_candidate = ?, graduation_year_candidate = ?,
                    updated_at_candidate = clock_timestamp() WHERE id_candidate = ?
                """, datos.nombres(), datos.apellidos(), datos.telefono(), datos.ciudad(), datos.institucion(),
                datos.idCarrera(), datos.condicionAcademica(), datos.cicloActual(), datos.anioEgreso(), idUsuario);
        guardarCorreo(idUsuario, datos.correo());
    }

    public void guardarMiembro(long idUsuario, long idEmpresa, SolicitudCuenta datos) {
        plantillaJdbc.update("""
                UPDATE company_members SET first_name_company_member = ?, last_name_company_member = ?,
                    phone_company_member = ? WHERE user_id_company_member = ? AND company_id_company_member = ?
                """, datos.nombres(), datos.apellidos(), datos.telefono(), idUsuario, idEmpresa);
        guardarCorreo(idUsuario, datos.correo());
    }

    public void guardarEmpresa(long idEmpresa, SolicitudCuenta datos) {
        plantillaJdbc.update("""
                UPDATE companies SET trade_name_company = ?, industry_company = ?, city_company = ?,
                    website_company = ? WHERE id_company = ?
                """, datos.nombreComercial(), datos.sector(), datos.ciudad(), datos.sitioWeb(), idEmpresa);
    }

    private void guardarCorreo(long idUsuario, String correo) {
        plantillaJdbc.update("""
                UPDATE users SET email_user = ?,
                    updated_at_user = GREATEST(clock_timestamp(), updated_at_user + interval '1 microsecond')
                WHERE id_user = ?
                """, correo, idUsuario);
    }
}
