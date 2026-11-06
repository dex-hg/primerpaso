package com.dextre.primerpaso.seguridad;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import com.dextre.primerpaso.seguridad.LimiteSolicitudes.Operacion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
class FiltroProteccionApi extends OncePerRequestFilter {

    private static final int MAXIMO_BYTES = 64 * 1024;
    private static final Set<String> ESCRITURAS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final LimiteSolicitudes limites;
    private final OrigenSolicitud origen;
    private final Semaphore cuposAutenticacion = new Semaphore(3);

    FiltroProteccionApi(LimiteSolicitudes limites, OrigenSolicitud origen) {
        this.limites = limites;
        this.origen = origen;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {
        String ruta = UrlPathHelper.defaultInstance.getPathWithinApplication(solicitud).replaceAll("/{2,}", "/");
        if (!ruta.startsWith("/api/")) {
            cadena.doFilter(solicitud, respuesta);
            return;
        }
        Operacion operacion = obtenerOperacion(ruta, solicitud.getMethod());
        if (operacion != null) {
            respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            // Reutilizar el token de una sesión existente no crea otra sesión en memoria.
            if (operacion != Operacion.TOKEN_CSRF || solicitud.getSession(false) == null) {
                int espera = limites.consumir(operacion, origen.identificar(solicitud));
                if (espera > 0) {
                    rechazar(respuesta, 429, espera, "Demasiadas solicitudes. Espera antes de volver a intentarlo.");
                    return;
                }
            }
        }
        HttpServletRequest preparada = solicitud;
        if (ESCRITURAS.contains(solicitud.getMethod()) && solicitud.getContentLengthLong() != 0) {
            if (solicitud.getContentLengthLong() > MAXIMO_BYTES) {
                rechazarPorTamano(respuesta);
                return;
            }
            byte[] cuerpo = solicitud.getInputStream().readNBytes(MAXIMO_BYTES + 1);
            if (cuerpo.length > MAXIMO_BYTES) {
                rechazarPorTamano(respuesta);
                return;
            }
            preparada = new CuerpoApi(solicitud, cuerpo);
        }
        if (operacion == Operacion.INICIO_SESION || operacion == Operacion.REGISTRO) {
            if (!cuposAutenticacion.tryAcquire()) {
                rechazar(respuesta, 429, 2, "Hay demasiadas solicitudes en curso. Inténtalo en unos segundos.");
                return;
            }
            try {
                cadena.doFilter(preparada, respuesta);
            } finally {
                cuposAutenticacion.release();
            }
        } else {
            cadena.doFilter(preparada, respuesta);
        }
    }

    private Operacion obtenerOperacion(String ruta, String metodo) {
        if (ruta.equals("/api/sesion/seguridad") && ("GET".equals(metodo) || "HEAD".equals(metodo))) {
            return Operacion.TOKEN_CSRF;
        }
        if ("POST".equals(metodo)) {
            if (ruta.equals("/api/sesion")) {
                return Operacion.INICIO_SESION;
            }
            if (ruta.equals("/api/registro/postulantes") || ruta.equals("/api/registro/empresas")) {
                return Operacion.REGISTRO;
            }
        }
        return null;
    }

    private void rechazarPorTamano(HttpServletResponse respuesta) throws IOException {
        rechazar(respuesta, 413, 0, "La solicitud es demasiado grande.");
    }

    private void rechazar(HttpServletResponse respuesta, int estado, int espera, String mensaje)
            throws IOException {
        respuesta.setStatus(estado);
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (espera > 0) {
            respuesta.setHeader(HttpHeaders.RETRY_AFTER, Integer.toString(espera));
        }
        respuesta.setContentType("application/json;charset=UTF-8");
        respuesta.getWriter().write("{\"mensaje\":\"" + mensaje + "\"}");
    }
}
