package com.dextre.primerpaso.oportunidades;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.oportunidades.DatosOportunidades.Oportunidad;
import com.dextre.primerpaso.oportunidades.DatosOportunidades.PaginaOportunidades;
import com.dextre.primerpaso.panel.FiltroAccesoPanel;
import com.dextre.primerpaso.postulaciones.ServicioPostulaciones;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.sesion.ServicioSesion;
import com.dextre.primerpaso.vacantes.DatosVacantes.Catalogos;
import com.dextre.primerpaso.vacantes.DatosVacantes.Habilidad;

@SpringBootTest(properties = {"spring.config.import=", "spring.datasource.url=jdbc:postgresql://localhost:1/prueba",
        "spring.datasource.username=prueba", "spring.datasource.password=prueba", "primerpaso.bd.comprobar-al-iniciar=false"})
class VistasOportunidadesTests {

    private static final UsuarioSesion USUARIO = new UsuarioSesion(7, "Ana", "Pérez", "ana@example.test",
            "postulante", null, null, null);
    @Autowired private WebApplicationContext contexto;
    @Autowired private FiltroAccesoPanel filtro;
    @MockitoBean private ServicioSesion sesiones;
    @MockitoBean private ServicioOportunidades oportunidades;
    @MockitoBean private ServicioPostulaciones postulaciones;
    private MockMvc cliente;

    @BeforeEach
    void preparar() {
        cliente = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtro).build();
    }

    @Test
    void exigeSesionDePostulanteAntesDeBuscarOConsultarUnaVacante() throws Exception {
        cliente.perform(get("/panel/postulante/vacantes")).andExpect(status().isFound())
                .andExpect(redirectedUrl("/iniciar-sesion"));
        var empresa = new UsuarioSesion(7, "Ana", "Pérez", "ana@example.test", "empresa", 9L, "Empresa", "administrador");
        when(sesiones.consultarUsuario(7, "empresa", 9L)).thenReturn(empresa);
        var sesion = sesion();
        sesion.setAttribute("primerpaso.tipoCuenta", "empresa");
        sesion.setAttribute("primerpaso.idEmpresa", 9L);
        cliente.perform(get("/panel/postulante/vacantes/8").session(sesion)).andExpect(status().isForbidden());
        verifyNoInteractions(oportunidades);
    }

    @Test
    void renderizaResultadosSinEjecutarHtmlYPreservaLaBusquedaAlPaginar() throws Exception {
        autorizar();
        when(oportunidades.listar(any(), anyInt())).thenReturn(new PaginaOportunidades(List.of(vacante()), 0, 2, 12));
        String html = cliente.perform(get("/panel/postulante/vacantes").session(sesion())
                        .param("busqueda", "Java <script>").param("modalidad", "remota").param("ciudad", "Lima"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("12 vacantes disponibles", "Java &lt;script&gt;", "Empresa &lt;prueba&gt;",
                        "pagina=1", "modalidad=remota", "ciudad=Lima", "href=\"/panel/postulante/vacantes/8\"")
                .doesNotContain("<script>alert", "Sesión activa.", ">Inicio</a>");
    }

    @Test
    void renderizaElEstadoVacioYUnDetalleConDescripcionYHabilidades() throws Exception {
        autorizar();
        when(oportunidades.listar(any(), anyInt())).thenReturn(new PaginaOportunidades(List.of(), 0, 0, 0));
        String html = cliente.perform(get("/panel/postulante/vacantes").session(sesion()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("No encontramos vacantes", "Limpiar filtros");
        when(oportunidades.consultar(8)).thenReturn(vacante());
        html = cliente.perform(get("/panel/postulante/vacantes/8").session(sesion()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Acerca de la oportunidad", "Habilidades solicitadas", "SQL", "Detalle de vacante");
    }

    @Test
    void unaVacanteQueDejaDeEstarDisponibleDevuelve404YPermiteVolverALaLista() throws Exception {
        autorizar();
        when(oportunidades.consultar(8)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Esta vacante ya no está disponible."));
        cliente.perform(get("/panel/postulante/vacantes/8").session(sesion())).andExpect(status().isNotFound());
        cliente.perform(get("/panel/postulante/vacantes").session(sesion()).param("idCarrera", "invalida"))
                .andExpect(status().isBadRequest());
    }

    private void autorizar() {
        when(sesiones.consultarUsuario(7, "postulante", null)).thenReturn(USUARIO);
        when(oportunidades.catalogos()).thenReturn(new Catalogos(List.of(), List.of()));
    }

    private Oportunidad vacante() {
        return new Oportunidad(8, "Java <script>", "Descripción\nSegunda línea", "Empresa <prueba>", "Sistemas",
                "remota", "Lima", "PE", Instant.parse("2026-10-24T15:00:00Z"), null,
                List.of(new Habilidad(4, "SQL")));
    }

    private MockHttpSession sesion() {
        var sesion = new MockHttpSession();
        sesion.setAttribute("primerpaso.idUsuario", 7L);
        sesion.setAttribute("primerpaso.tipoCuenta", "postulante");
        return sesion;
    }
}
