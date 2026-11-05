package com.dextre.primerpaso.contratacion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.primerpaso.contratacion.DatosContratacion.Ficha;
import com.dextre.primerpaso.contratacion.DatosContratacion.Oferta;
import com.dextre.primerpaso.contratacion.DatosContratacion.SolicitudCancelacion;
import com.dextre.primerpaso.contratacion.DatosContratacion.SolicitudConfirmacion;
import com.dextre.primerpaso.contratacion.DatosContratacion.SolicitudOferta;
import com.dextre.primerpaso.contratacion.DatosContratacion.SolicitudRespuesta;
import com.dextre.primerpaso.contratacion.DatosContratacion.SolicitudRetiro;
import com.dextre.primerpaso.sesion.AccesoSesion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
public class ControladorContratacion {

    private static final String OFERTAS = "/api/postulaciones/{id}/ofertas";
    private static final String TOKEN = "X-CSRF-Token";
    private final ServicioContratacion servicio;
    private final AccesoSesion acceso;

    public ControladorContratacion(ServicioContratacion servicio, AccesoSesion acceso) {
        this.servicio = servicio;
        this.acceso = acceso;
    }

    @GetMapping(OFERTAS)
    public Ficha consultar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id) {
        return servicio.consultar(usuario, id);
    }

    @PostMapping(value = OFERTAS, consumes = "application/json")
    public Oferta enviar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id,
            @Valid @RequestBody SolicitudOferta datos, @RequestHeader(value = TOKEN, required = false) String token,
            HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        return servicio.enviarOferta(usuario, id, datos);
    }

    @PostMapping(value = OFERTAS + "/{idOferta}/respuesta", consumes = "application/json")
    public Oferta responder(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id,
            @PathVariable long idOferta, @Valid @RequestBody SolicitudRespuesta datos,
            @RequestHeader(value = TOKEN, required = false) String token, HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        return servicio.responder(usuario, id, idOferta, datos.aceptada());
    }

    @PostMapping(OFERTAS + "/{idOferta}/retirar")
    public Oferta retirarOferta(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id,
            @PathVariable long idOferta, @RequestHeader(value = TOKEN, required = false) String token,
            HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        return servicio.retirarOferta(usuario, id, idOferta);
    }

    @PostMapping(value = OFERTAS + "/{idOferta}/confirmar", consumes = "application/json")
    public Oferta confirmar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id,
            @PathVariable long idOferta, @Valid @RequestBody SolicitudConfirmacion datos,
            @RequestHeader(value = TOKEN, required = false) String token, HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        return servicio.confirmar(usuario, id, idOferta, datos);
    }

    @PostMapping(value = "/api/postulaciones/{id}/retirar", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirarPostulacion(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id,
            @Valid @RequestBody SolicitudRetiro datos, @RequestHeader(value = TOKEN, required = false) String token,
            HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        servicio.retirarPostulacion(usuario, id, datos.version());
    }

    @PostMapping(value = "/api/vacantes/{id}/cancelar-proceso", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarProceso(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id,
            @Valid @RequestBody SolicitudCancelacion datos, @RequestHeader(value = TOKEN, required = false) String token,
            HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        servicio.cancelarProceso(usuario, id, datos);
    }
}
