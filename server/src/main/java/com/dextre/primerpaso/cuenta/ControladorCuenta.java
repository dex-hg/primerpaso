package com.dextre.primerpaso.cuenta;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.primerpaso.cuenta.DatosCuenta.Cuenta;
import com.dextre.primerpaso.cuenta.DatosCuenta.SolicitudCuenta;
import com.dextre.primerpaso.sesion.AccesoSesion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cuenta")
public class ControladorCuenta {

    private final ServicioCuenta servicio;
    private final AccesoSesion acceso;

    public ControladorCuenta(ServicioCuenta servicio, AccesoSesion acceso) {
        this.servicio = servicio;
        this.acceso = acceso;
    }

    @GetMapping
    public ResponseEntity<Cuenta> consultar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.consultar(usuario));
    }

    @PutMapping(consumes = "application/json")
    public ResponseEntity<Cuenta> actualizar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @Valid @RequestBody SolicitudCuenta datos,
            @RequestHeader(value = "X-CSRF-Token", required = false) String token,
            HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.actualizar(usuario, datos));
    }
}
