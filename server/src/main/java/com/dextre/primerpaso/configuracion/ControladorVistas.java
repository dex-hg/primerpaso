package com.dextre.primerpaso.configuracion;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Vistas Thymeleaf a partir de la migración de Mallqui Liberato Yefrit.
 */
@Controller
public class ControladorVistas {

    private static final Map<String, String> RUTAS_ANTERIORES = Map.of(
            "/index.html", "/",
            "/html/iniciar-sesion.html", "/iniciar-sesion",
            "/html/registro.html", "/registro",
            "/html/registro-postulante.html", "/registro-postulante",
            "/html/registro-empresa.html", "/registro-empresa",
            "/html/cuenta.html", "/cuenta");

    @GetMapping("/")
    public String inicio(Model modelo) {
        modelo.addAttribute("categorias", List.of(
                new CategoriaInicio("Tecnología", "480 oportunidades", "bi-code-slash", "tecnología"),
                new CategoriaInicio("Administración", "325 oportunidades", "bi-bar-chart", "administración"),
                new CategoriaInicio("Marketing", "216 oportunidades", "bi-megaphone", "marketing"),
                new CategoriaInicio("Diseño", "174 oportunidades", "bi-vector-pen", "diseño")));
        return "index";
    }

    @GetMapping("/iniciar-sesion")
    public String iniciarSesion() {
        return "iniciar-sesion";
    }

    @GetMapping("/registro")
    public String registro() {
        return "registro";
    }

    @GetMapping("/registro-postulante")
    public String registroPostulante() {
        return "registro-postulante";
    }

    @GetMapping("/registro-empresa")
    public String registroEmpresa() {
        return "registro-empresa";
    }

    @GetMapping("/cuenta")
    public String cuenta() {
        return "cuenta";
    }

    @GetMapping("/contacto")
    public String contacto() {
        return "contacto";
    }

    @GetMapping({"/index.html", "/html/iniciar-sesion.html", "/html/registro.html",
            "/html/registro-postulante.html", "/html/registro-empresa.html", "/html/cuenta.html"})
    public String redirigirRutaAnterior(HttpServletRequest solicitud) {
        String ruta = (String) solicitud.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        return "redirect:" + RUTAS_ANTERIORES.get(ruta);
    }

    public record CategoriaInicio(String nombre, String oportunidades, String icono, String termino) {
    }
}
