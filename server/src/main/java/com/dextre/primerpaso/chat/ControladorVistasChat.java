package com.dextre.primerpaso.chat;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

@Controller
public class ControladorVistasChat {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Lima"));
    private final ServicioChat servicio;

    public ControladorVistasChat(ServicioChat servicio) {
        this.servicio = servicio;
    }

    @GetMapping({"/panel/postulante/mensajes", "/panel/empresa/mensajes"})
    public String listar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, Model modelo,
            @RequestParam(defaultValue = "0") int pagina) {
        prepararModelo(usuario, modelo);
        modelo.addAttribute("bandeja", servicio.listar(usuario, pagina));
        return "chat/lista";
    }

    @GetMapping({"/panel/postulante/postulaciones/{id}/chat", "/panel/empresa/postulaciones/{id}/chat"})
    public String consultar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, Model modelo, @PathVariable long id) {
        prepararModelo(usuario, modelo);
        modelo.addAttribute("chat", servicio.consultar(usuario, id, null, null));
        return "chat/detalle";
    }

    private void prepararModelo(UsuarioSesion usuario, Model modelo) {
        modelo.addAttribute("usuario", usuario);
        modelo.addAttribute("esEmpresa", "empresa".equals(usuario.tipoCuenta()));
        modelo.addAttribute("rutaPanel", "/panel/" + usuario.tipoCuenta());
        modelo.addAttribute("formatoFecha", FECHA);
    }
}
