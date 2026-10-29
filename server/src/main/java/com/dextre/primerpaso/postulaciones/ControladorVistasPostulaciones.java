package com.dextre.primerpaso.postulaciones;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import com.dextre.primerpaso.oportunidades.ServicioOportunidades;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

@Controller
public class ControladorVistasPostulaciones {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Lima"));
    private static final List<String> ESTADOS = List.of("enviada", "en_revision", "seleccionada", "descartada", "retirada");
    private static final List<String> NOMBRES_ESTADOS = List.of("Enviada", "En revisión", "Seleccionada", "Descartada", "Retirada");
    private final ServicioPostulaciones servicio;
    private final ServicioOportunidades oportunidades;

    public ControladorVistasPostulaciones(ServicioPostulaciones servicio, ServicioOportunidades oportunidades) {
        this.servicio = servicio;
        this.oportunidades = oportunidades;
    }

    @GetMapping("/panel/postulante/vacantes/{id}/postular")
    public String formulario(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id, Model modelo) {
        var anterior = servicio.buscarPropia(usuario, id);
        if (anterior.isPresent()) return "redirect:/panel/postulante/postulaciones/" + anterior.get();
        prepararModelo(usuario, modelo);
        modelo.addAttribute("vacante", oportunidades.consultar(id));
        return "postulaciones/formulario";
    }

    @GetMapping({"/panel/postulante/postulaciones", "/panel/empresa/postulaciones"})
    public String listar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, Model modelo,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "todos") String estado,
            @RequestParam(required = false) Long idVacante) {
        prepararModelo(usuario, modelo);
        modelo.addAttribute("paginaPostulaciones", servicio.listar(usuario, pagina, estado, idVacante));
        modelo.addAttribute("estadoFiltro", estado);
        modelo.addAttribute("idVacante", idVacante);
        modelo.addAttribute("estados", ESTADOS);
        modelo.addAttribute("nombresEstados", NOMBRES_ESTADOS);
        return "postulaciones/lista";
    }

    @GetMapping({"/panel/postulante/postulaciones/{id}", "/panel/empresa/postulaciones/{id}"})
    public String consultar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id, Model modelo) {
        prepararModelo(usuario, modelo);
        modelo.addAttribute("detalle", servicio.consultar(usuario, id));
        return "postulaciones/detalle";
    }

    private void prepararModelo(UsuarioSesion usuario, Model modelo) {
        modelo.addAttribute("usuario", usuario);
        modelo.addAttribute("esEmpresa", "empresa".equals(usuario.tipoCuenta()));
        modelo.addAttribute("rutaPostulaciones", "/panel/" + usuario.tipoCuenta() + "/postulaciones");
        modelo.addAttribute("formatoFecha", FECHA);
    }
}
