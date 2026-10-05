package com.dextre.primerpaso.vacantes;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.DatosVacantes.Habilidad;
import com.dextre.primerpaso.vacantes.DatosVacantes.Vacante;

@Controller
@RequestMapping("/panel/empresa/vacantes")
public class ControladorVistasVacantes {

    private static final DateTimeFormatter FECHA_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            .withZone(ZoneId.of("America/Lima"));
    private final ServicioVacantes servicio;

    public ControladorVistasVacantes(ServicioVacantes servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String listar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, Model modelo,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "todos") String estado) {
        modelo.addAttribute("usuario", usuario);
        modelo.addAttribute("paginaVacantes", servicio.listar(usuario, pagina, estado));
        modelo.addAttribute("estadoFiltro", estado.strip().toLowerCase(Locale.ROOT));
        return "vacantes/lista";
    }

    @GetMapping("/nueva")
    public String nueva(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, Model modelo) {
        prepararFormulario(usuario, null, modelo);
        return "vacantes/formulario";
    }

    @GetMapping("/{id}/editar")
    public String editar(@RequestAttribute("usuarioSesion") UsuarioSesion usuario, @PathVariable long id, Model modelo) {
        prepararFormulario(usuario, servicio.consultar(usuario, id), modelo);
        return "vacantes/formulario";
    }

    private void prepararFormulario(UsuarioSesion usuario, Vacante vacante, Model modelo) {
        var catalogos = servicio.catalogos(usuario);
        var habilidadesActivas = catalogos.habilidades().stream().map(opcion -> opcion.id()).toList();
        boolean carreraInactiva = vacante != null
                && catalogos.carreras().stream().noneMatch(opcion -> opcion.id() == vacante.idCarrera());
        List<Habilidad> habilidadesInactivas = vacante == null ? List.of() : vacante.habilidades().stream()
                .filter(habilidad -> !habilidadesActivas.contains(habilidad.id())).toList();
        modelo.addAttribute("usuario", usuario);
        modelo.addAttribute("catalogos", catalogos);
        modelo.addAttribute("vacante", vacante);
        modelo.addAttribute("esNueva", vacante == null);
        modelo.addAttribute("soloLectura", vacante != null && !vacante.editable());
        modelo.addAttribute("fechaVencimientoLocal", vacante == null || vacante.fechaVencimiento() == null
                ? "" : FECHA_LOCAL.format(vacante.fechaVencimiento()));
        modelo.addAttribute("habilidadesSeleccionadas", vacante == null ? List.of()
            : vacante.habilidades().stream().map(habilidad -> habilidad.id()).toList());
        modelo.addAttribute("carreraInactiva", carreraInactiva);
        modelo.addAttribute("habilidadesInactivas", habilidadesInactivas);
    }
}
