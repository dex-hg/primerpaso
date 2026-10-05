package com.dextre.primerpaso.sesion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Revalida la identidad guardada en sesión antes de entregar datos privados. */
@Component
public class AccesoSesion {

    private static final String ATRIBUTO_USUARIO = "primerpaso.idUsuario";
    private static final String ATRIBUTO_TIPO = "primerpaso.tipoCuenta";
    private static final String ATRIBUTO_EMPRESA = "primerpaso.idEmpresa";
    private final ServicioSesion servicio;
    private final boolean cookieSegura;

    public AccesoSesion(ServicioSesion servicio,
            @Value("${server.servlet.session.cookie.secure:false}") boolean cookieSegura) {
        this.servicio = servicio;
        this.cookieSegura = cookieSegura;
    }

    public UsuarioSesion consultarUsuario(HttpServletRequest solicitud, HttpServletResponse respuesta) {
        HttpSession sesion = solicitud.getSession(false);
        if (sesion == null) {
            throw accesoInvalido();
        }
        try {
            if (!(sesion.getAttribute(ATRIBUTO_USUARIO) instanceof Long idUsuario) || idUsuario <= 0
                    || !(sesion.getAttribute(ATRIBUTO_TIPO) instanceof String tipoCuenta)) {
                throw accesoInvalido();
            }
            Object empresa = sesion.getAttribute(ATRIBUTO_EMPRESA);
            Long idEmpresa = empresa instanceof Long identificador ? identificador : null;
            if (!("postulante".equals(tipoCuenta) || "empresa".equals(tipoCuenta))
                    || ("postulante".equals(tipoCuenta) && empresa != null)
                    || ("empresa".equals(tipoCuenta) && (idEmpresa == null || idEmpresa <= 0))) {
                throw accesoInvalido();
            }
            UsuarioSesion usuario = servicio.consultarUsuario(idUsuario, tipoCuenta, idEmpresa);
            if (usuario == null || usuario.idUsuario() != idUsuario || !tipoCuenta.equals(usuario.tipoCuenta())
                    || ("empresa".equals(tipoCuenta) && (idEmpresa == null
                        || !idEmpresa.equals(usuario.idEmpresa())
                        || !("administrador".equals(usuario.rolEmpresa())
                            || "reclutador".equals(usuario.rolEmpresa()))))) {
                throw accesoInvalido();
            }
            return usuario;
        } catch (AccesoNoAutorizadoException excepcion) {
            sesion.invalidate();
            borrarCookie(respuesta);
            throw excepcion;
        }
    }

    public void guardarUsuario(HttpSession sesion, UsuarioSesion usuario) {
        sesion.setAttribute(ATRIBUTO_USUARIO, usuario.idUsuario());
        sesion.setAttribute(ATRIBUTO_TIPO, usuario.tipoCuenta());
        if (usuario.idEmpresa() != null) {
            sesion.setAttribute(ATRIBUTO_EMPRESA, usuario.idEmpresa());
        }
    }

    public void borrarCookie(HttpServletResponse respuesta) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("PRIMERPASO_SESION", "")
                .path("/").httpOnly(true).sameSite("Strict").secure(cookieSegura)
                .maxAge(Duration.ZERO).build().toString());
    }

    public void comprobarToken(HttpSession sesion, String recibido) {
        Object guardado = sesion == null ? null : sesion.getAttribute("primerpaso.tokenCsrf");
        if (!(guardado instanceof String esperado) || recibido == null || recibido.length() != 43
                || !MessageDigest.isEqual(esperado.getBytes(StandardCharsets.US_ASCII),
                        recibido.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La solicitud no es válida. Recarga la página e inténtalo nuevamente.");
        }
    }

    private AccesoNoAutorizadoException accesoInvalido() {
        return new AccesoNoAutorizadoException("Inicia sesión para continuar.");
    }
}
