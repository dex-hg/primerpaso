package com.dextre.primerpaso.chat;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.primerpaso.chat.DatosChat.Detalle;
import com.dextre.primerpaso.chat.DatosChat.PaginaConversaciones;
import com.dextre.primerpaso.chat.DatosChat.ResultadoEnvio;
import com.dextre.primerpaso.chat.DatosChat.SolicitudMensaje;
import com.dextre.primerpaso.sesion.AccesoSesion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chat")
public class ControladorChat {

    private final ServicioChat servicio;
    private final AccesoSesion acceso;

    public ControladorChat(ServicioChat servicio, AccesoSesion acceso) {
        this.servicio = servicio;
        this.acceso = acceso;
    }

    @GetMapping
    public ResponseEntity<PaginaConversaciones> listar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @RequestParam(defaultValue = "0") int pagina) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.listar(usuario, pagina));
    }

    @GetMapping("/postulaciones/{id}")
    public ResponseEntity<Detalle> consultar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @PathVariable long id, @RequestParam(required = false) Long antesDe,
            @RequestParam(required = false) Long despuesDe) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.consultar(usuario, id, antesDe, despuesDe));
    }

    @PostMapping(value = "/postulaciones/{id}/mensajes", consumes = "application/json")
    public ResponseEntity<ResultadoEnvio> enviar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @PathVariable long id, @Valid @RequestBody SolicitudMensaje datos,
            @RequestHeader(value = "X-CSRF-Token", required = false) String token, HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), token);
        ResultadoEnvio resultado = servicio.enviar(usuario, id, datos);
        return ResponseEntity.status(resultado.creado() ? 201 : 200).cacheControl(CacheControl.noStore()).body(resultado);
    }
}
