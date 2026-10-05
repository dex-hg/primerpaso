package com.dextre.primerpaso.vacantes;

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

import com.dextre.primerpaso.sesion.AccesoSesion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.DatosVacantes.PaginaVacantes;
import com.dextre.primerpaso.vacantes.DatosVacantes.SolicitudEstadoVacante;
import com.dextre.primerpaso.vacantes.DatosVacantes.SolicitudVacante;
import com.dextre.primerpaso.vacantes.DatosVacantes.Vacante;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/vacantes")
public class ControladorVacantes {

    private final ServicioVacantes servicio;
    private final AccesoSesion acceso;

    public ControladorVacantes(ServicioVacantes servicio, AccesoSesion acceso) {
        this.servicio = servicio;
        this.acceso = acceso;
    }

    @GetMapping
    public ResponseEntity<PaginaVacantes> listar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "todos") String estado) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.listar(usuario, pagina, estado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Vacante> consultar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @PathVariable long id) {
        return responder(servicio.consultar(usuario, id));
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Vacante> crear(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @Valid @RequestBody SolicitudVacante datos,
            @RequestHeader(value = "X-CSRF-Token", required = false) String token,
            HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        Vacante vacante = servicio.crear(usuario, datos);
        return ResponseEntity.created(URI.create(solicitud.getContextPath() + "/api/vacantes/" + vacante.id()))
                .cacheControl(CacheControl.noStore()).body(vacante);
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    public ResponseEntity<Vacante> editar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @PathVariable long id, @Valid @RequestBody SolicitudVacante datos,
            @RequestHeader(value = "X-CSRF-Token", required = false) String token,
            HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        return responder(servicio.editar(usuario, id, datos));
    }

    @PostMapping(value = "/{id}/publicar", consumes = "application/json")
    public ResponseEntity<Vacante> publicar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @PathVariable long id, @Valid @RequestBody SolicitudEstadoVacante datos,
            @RequestHeader(value = "X-CSRF-Token", required = false) String token,
            HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        return responder(servicio.publicar(usuario, id, datos));
    }

    @PostMapping(value = "/{id}/cerrar", consumes = "application/json")
    public ResponseEntity<Vacante> cerrar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @PathVariable long id, @Valid @RequestBody SolicitudEstadoVacante datos,
            @RequestHeader(value = "X-CSRF-Token", required = false) String token,
            HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        return responder(servicio.cerrar(usuario, id, datos));
    }

    private ResponseEntity<Vacante> responder(Vacante vacante) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vacante);
    }
}
