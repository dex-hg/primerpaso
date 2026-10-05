package com.dextre.primerpaso.panel;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;

import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

@Controller
public class ControladorPanel {

    @GetMapping("/panel")
    public String abrirPanel(@RequestAttribute("usuarioSesion") UsuarioSesion usuario) {
        return "redirect:/panel/" + usuario.tipoCuenta();
    }

    @GetMapping({"/panel/postulante", "/panel/empresa"})
    public String mostrarPanel(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, Model modelo) {
        modelo.addAttribute("usuario", usuario);
        return "panel/" + usuario.tipoCuenta();
    }
}
