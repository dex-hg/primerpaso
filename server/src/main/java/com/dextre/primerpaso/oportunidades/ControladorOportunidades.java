package com.dextre.primerpaso.oportunidades;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dextre.primerpaso.oportunidades.DatosOportunidades.Filtros;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

@Controller
@RequestMapping("/panel/postulante/vacantes")
public class ControladorOportunidades {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Lima"));
    private final ServicioOportunidades servicio;

    public ControladorOportunidades(ServicioOportunidades servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String listar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, Model modelo,
            @RequestParam(defaultValue = "") String busqueda, @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) Long idCarrera, @RequestParam(defaultValue = "") String ciudad,
            @RequestParam(required = false) Long idHabilidad, @RequestParam(defaultValue = "0") int pagina) {
        var filtros = new Filtros(busqueda, modalidad, idCarrera, ciudad, idHabilidad);
        modelo.addAttribute("usuario", usuario);
        modelo.addAttribute("filtros", filtros);
        modelo.addAttribute("paginaVacantes", servicio.listar(filtros, pagina));
        modelo.addAttribute("catalogos", servicio.catalogos());
        modelo.addAttribute("formatoFecha", FECHA);
        return "oportunidades/lista";
    }

    @GetMapping("/{id}")
    public String consultar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id,
            Model modelo) {
        modelo.addAttribute("usuario", usuario);
        modelo.addAttribute("vacante", servicio.consultar(id));
        modelo.addAttribute("formatoFecha", FECHA);
        return "oportunidades/detalle";
    }
}
