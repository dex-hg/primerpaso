package com.dextre.primerpaso.actividad;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.actividad.DatosActividad.Pendientes;
import com.dextre.primerpaso.sesion.AccesoSesion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class ControladorActividad {

    private static final String BANDEJA = "/panel/notificaciones";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Lima"));
    private final ServicioActividad servicio;
    private final AccesoSesion acceso;

    public ControladorActividad(ServicioActividad servicio, AccesoSesion acceso) {
        this.servicio = servicio;
        this.acceso = acceso;
    }

    @GetMapping(BANDEJA)
    public String notificaciones(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @RequestParam(defaultValue = "todas") String estado, @RequestParam(defaultValue = "0") int pagina,
            HttpServletRequest solicitud, Model modelo) {
        prepararModelo(usuario, modelo);
        modelo.addAttribute("bandeja", servicio.bandeja(usuario, estado, pagina));
        modelo.addAttribute("estadoFiltro", estado);
        modelo.addAttribute("tokenCsrf", acceso.tokenActual(solicitud.getSession(false)));
        return "actividad/notificaciones";
    }

    @GetMapping("/cuenta/historial")
    public String historial(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @RequestParam(defaultValue = "0") int pagina, Model modelo) {
        prepararModelo(usuario, modelo);
        modelo.addAttribute("historial", servicio.historial(usuario, pagina));
        return "actividad/historial";
    }

    @GetMapping("/api/notificaciones/pendientes")
    public ResponseEntity<Pendientes> pendientes(@RequestAttribute("usuarioSesion") UsuarioSesion usuario) {
        try {
            return ResponseEntity.ok(new Pendientes(servicio.pendientes(usuario), usuario.idUsuario(), usuario.idEmpresa()));
        } catch (DataAccessException excepcion) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (ResponseStatusException excepcion) {
            return ResponseEntity.status(excepcion.getStatusCode()).build();
        }
    }

    @PostMapping(BANDEJA + "/{id}/leer")
    public String marcarLeida(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id,
            @RequestParam(required = false) String tokenCsrf, @RequestParam(defaultValue = "false") boolean abrir,
            @RequestParam(defaultValue = "todas") String estado, HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), tokenCsrf);
        long idPostulacion = servicio.marcarLeida(usuario, id);
        if (abrir) return "redirect:" + rutaPostulaciones(usuario) + "/" + idPostulacion;
        return volverABandeja(estado);
    }

    @PostMapping(BANDEJA + "/leer-todas")
    public String marcarTodasLeidas(@RequestAttribute("usuarioSesion") UsuarioSesion usuario,
            @RequestParam(required = false) String tokenCsrf, @RequestParam(defaultValue = "todas") String estado,
            HttpServletRequest solicitud) {
        acceso.comprobarToken(solicitud.getSession(false), tokenCsrf);
        servicio.marcarTodasLeidas(usuario);
        return volverABandeja(estado);
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String servicioNoDisponible() {
        return "error/503";
    }

    private void prepararModelo(UsuarioSesion usuario, Model modelo) {
        modelo.addAttribute("usuario", usuario);
        modelo.addAttribute("formatoFecha", FECHA);
        modelo.addAttribute("rutaPostulaciones", rutaPostulaciones(usuario));
    }

    private String rutaPostulaciones(UsuarioSesion usuario) {
        return "/panel/" + usuario.tipoCuenta() + "/postulaciones";
    }

    private String volverABandeja(String estado) {
        return "redirect:" + BANDEJA + ("sin_leer".equals(estado) ? "?estado=sin_leer" : "");
    }
}
