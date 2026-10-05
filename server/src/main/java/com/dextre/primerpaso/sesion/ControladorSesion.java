package com.dextre.primerpaso.sesion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.sesion.DatosSesion.SolicitudInicioSesion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

@RestController
@RequestMapping("/api/sesion")
public class ControladorSesion {

    private static final String ATRIBUTO_CSRF = "primerpaso.tokenCsrf";
    private final SecureRandom aleatorio = new SecureRandom();
    private final ServicioSesion servicio;
    private final AccesoSesion acceso;

    public ControladorSesion(ServicioSesion servicio, AccesoSesion acceso) {
        this.servicio = servicio;
        this.acceso = acceso;
    }

    @GetMapping("/seguridad")
    public ResponseEntity<SeguridadSesion> obtenerSeguridad(HttpServletRequest solicitud) {
        HttpSession sesion = solicitud.getSession(true);
        sesion.setMaxInactiveInterval(1800);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SeguridadSesion(obtenerToken(sesion)));
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<UsuarioSesion> iniciarSesion(@Valid @RequestBody SolicitudInicioSesion datos,
            @RequestHeader(value = "X-CSRF-Token", required = false) String token,
            HttpServletRequest solicitud) {
        HttpSession sesionAnterior = solicitud.getSession(false);
        comprobarToken(sesionAnterior, token);
        UsuarioSesion usuario = servicio.iniciarSesion(datos);
        sesionAnterior.invalidate();
        HttpSession sesion = solicitud.getSession(true);
        sesion.setMaxInactiveInterval(1800);
        acceso.guardarUsuario(sesion, usuario);
        obtenerToken(sesion);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(usuario);
    }

    @GetMapping
    public ResponseEntity<UsuarioSesion> consultarSesion(HttpServletRequest solicitud,
            HttpServletResponse respuesta) {
        UsuarioSesion usuario = acceso.consultarUsuario(solicitud, respuesta);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(usuario);
    }

    @DeleteMapping
    public ResponseEntity<Void> cerrarSesion(
            @RequestHeader(value = "X-CSRF-Token", required = false) String token,
            HttpServletRequest solicitud, HttpServletResponse respuesta) {
        HttpSession sesion = solicitud.getSession(false);
        comprobarToken(sesion, token);
        sesion.invalidate();
        acceso.borrarCookie(respuesta);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private String obtenerToken(HttpSession sesion) {
        if (sesion.getAttribute(ATRIBUTO_CSRF) instanceof String token) {
            return token;
        }
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sesion.setAttribute(ATRIBUTO_CSRF, token);
        return token;
    }

    private void comprobarToken(HttpSession sesion, String recibido) {
        Object guardado = sesion == null ? null : sesion.getAttribute(ATRIBUTO_CSRF);
        if (!(guardado instanceof String esperado) || recibido == null || recibido.length() != 43
                || !MessageDigest.isEqual(esperado.getBytes(StandardCharsets.US_ASCII),
                        recibido.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La solicitud de sesión no es válida. Recarga la página e inténtalo nuevamente.");
        }
    }

    public record SeguridadSesion(String tokenCsrf) {
    }
}
