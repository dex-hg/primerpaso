package com.dextre.primerpaso.seguridad;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class FiltroCabecerasSeguridad extends OncePerRequestFilter {

    private static final String CDN = "https://cdn.jsdelivr.net/npm/";
    private static final String BOOTSTRAP = CDN + "bootstrap@5.3.8/dist/";
    private static final String ICONOS = CDN + "bootstrap-icons@1.13.1/font/";
    private static final String POLITICA = "default-src 'self'; "
            + "script-src 'self' " + BOOTSTRAP + "js/bootstrap.bundle.min.js; "
            + "style-src 'self' " + BOOTSTRAP + "css/bootstrap.min.css " + ICONOS + "bootstrap-icons.min.css; "
            + "font-src 'self' " + ICONOS + "fonts/; img-src 'self' data:; connect-src 'self'; "
            + "object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";
    private final boolean hsts;

    FiltroCabecerasSeguridad(@Value("${primerpaso.seguridad.hsts:false}") boolean hsts) {
        this.hsts = hsts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {
        respuesta.setHeader("Content-Security-Policy", POLITICA);
        respuesta.setHeader("X-Content-Type-Options", "nosniff");
        respuesta.setHeader("X-Frame-Options", "DENY");
        respuesta.setHeader("Referrer-Policy", "no-referrer");
        respuesta.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (hsts) {
            respuesta.setHeader("Strict-Transport-Security", "max-age=31536000");
        }
        cadena.doFilter(solicitud, respuesta);
    }
}
