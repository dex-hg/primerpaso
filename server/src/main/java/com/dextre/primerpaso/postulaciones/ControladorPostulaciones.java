package com.dextre.primerpaso.postulaciones;

import java.net.URI;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.primerpaso.postulaciones.DatosPostulaciones.Detalle;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.PaginaPostulaciones;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.SolicitudEstado;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.SolicitudPostulacion;
import com.dextre.primerpaso.sesion.AccesoSesion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/postulaciones")
public class ControladorPostulaciones {

    private final ServicioPostulaciones servicio;
    private final AccesoSesion acceso;

    public ControladorPostulaciones(ServicioPostulaciones servicio, AccesoSesion acceso) {
        this.servicio = servicio;
        this.acceso = acceso;
    }

    @GetMapping
    public ResponseEntity<PaginaPostulaciones> listar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "todos") String estado,
            @RequestParam(required = false) Long idVacante) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.listar(usuario, pagina, estado, idVacante));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Detalle> consultar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.consultar(usuario, id));
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Detalle> postular(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @Valid @RequestBody SolicitudPostulacion datos,
            @RequestHeader(value = "X-CSRF-Token", required = false) String token, HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        Detalle detalle = servicio.postular(usuario, datos);
        return ResponseEntity.created(URI.create(solicitud.getContextPath() + "/api/postulaciones/" + detalle.postulacion().id()))
                .cacheControl(CacheControl.noStore()).body(detalle);
    }

    @PutMapping(value = "/{id}/estado", consumes = "application/json")
    public ResponseEntity<Detalle> cambiarEstado(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @PathVariable long id, @Valid @RequestBody SolicitudEstado datos,
            @RequestHeader(value = "X-CSRF-Token", required = false) String token, HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.cambiarEstado(usuario, id, datos));
    }
}
