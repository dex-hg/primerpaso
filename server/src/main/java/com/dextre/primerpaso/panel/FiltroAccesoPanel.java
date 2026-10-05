package com.dextre.primerpaso.panel;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import com.dextre.primerpaso.sesion.AccesoNoAutorizadoException;
import com.dextre.primerpaso.sesion.AccesoSesion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Protege las páginas privadas antes de ejecutar sus controladores. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class FiltroAccesoPanel extends OncePerRequestFilter {

    private static final Set<String> RUTAS_COMUNES = Set.of("/panel", "/cuenta", "/html/cuenta.html");
    private static final Pattern RUTAS_VACANTES = Pattern.compile(
            "/panel/empresa/vacantes(?:/nueva|/[1-9][0-9]*/editar)?");
    private static final Pattern API_VACANTES = Pattern.compile(
            "/api/vacantes(?:/[1-9][0-9]*(?:/(?:publicar|cerrar))?)?");
    private final AccesoSesion acceso;
    private final SpringTemplateEngine plantillas;

    public FiltroAccesoPanel(AccesoSesion acceso, SpringTemplateEngine plantillas) {
        this.acceso = acceso;
        this.plantillas = plantillas;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest solicitud) {
        String ruta = obtenerRuta(solicitud);
        return !RUTAS_COMUNES.contains(ruta) && !ruta.startsWith("/panel/") && !esApiVacantes(ruta);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        UsuarioSesion usuario;
        try {
            usuario = acceso.consultarUsuario(solicitud, respuesta);
        } catch (AccesoNoAutorizadoException excepcion) {
            if (esApiVacantes(obtenerRuta(solicitud))) {
                mostrarErrorApi(respuesta, 401);
            } else {
                respuesta.sendRedirect(solicitud.getContextPath() + "/iniciar-sesion");
            }
            return;
        } catch (DataAccessException | IllegalStateException excepcion) {
            mostrarError(solicitud, respuesta, 503);
            return;
        }
        String ruta = obtenerRuta(solicitud);
        boolean permisoEmpresa = "empresa".equals(usuario.tipoCuenta())
                && (RUTAS_VACANTES.matcher(ruta).matches() || API_VACANTES.matcher(ruta).matches());
        if (!RUTAS_COMUNES.contains(ruta) && !ruta.equals("/panel/" + usuario.tipoCuenta()) && !permisoEmpresa) {
            mostrarError(solicitud, respuesta, 403);
            return;
        }
        solicitud.setAttribute("usuarioSesion", usuario);
        cadena.doFilter(solicitud, respuesta);
    }

    private boolean esApiVacantes(String ruta) {
        return ruta.equals("/api/vacantes") || ruta.startsWith("/api/vacantes/");
    }

    private String obtenerRuta(HttpServletRequest solicitud) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(solicitud).replaceAll("/{2,}", "/");
    }

    private void mostrarError(HttpServletRequest solicitud, HttpServletResponse respuesta, int estado)
            throws IOException {
        if (esApiVacantes(obtenerRuta(solicitud))) {
            mostrarErrorApi(respuesta, estado);
            return;
        }
        respuesta.setStatus(estado);
        respuesta.setContentType("text/html;charset=UTF-8");
        var intercambio = JakartaServletWebApplication.buildApplication(solicitud.getServletContext())
                .buildExchange(solicitud, respuesta);
        var contexto = new WebContext(intercambio, solicitud.getLocale());
        plantillas.process("error/" + estado, contexto, respuesta.getWriter());
    }

    private void mostrarErrorApi(HttpServletResponse respuesta, int estado) throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType("application/json;charset=UTF-8");
        String mensaje = switch (estado) {
            case 401 -> "Inicia sesión para continuar.";
            case 403 -> "Tu tipo de cuenta no tiene acceso a esta operación.";
            default -> "No se pudo acceder al servicio. Inténtalo nuevamente más tarde.";
        };
        respuesta.getWriter().write("{\"mensaje\":\"" + mensaje + "\",\"errores\":{}}");
    }
}
