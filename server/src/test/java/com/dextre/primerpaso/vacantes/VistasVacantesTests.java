package com.dextre.primerpaso.vacantes;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.dextre.primerpaso.panel.FiltroAccesoPanel;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.sesion.ServicioSesion;
import com.dextre.primerpaso.vacantes.DatosVacantes.Catalogos;
import com.dextre.primerpaso.vacantes.DatosVacantes.Habilidad;
import com.dextre.primerpaso.vacantes.DatosVacantes.OpcionCatalogo;
import com.dextre.primerpaso.vacantes.DatosVacantes.PaginaVacantes;
import com.dextre.primerpaso.vacantes.DatosVacantes.Vacante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:postgresql://localhost:1/prueba",
        "spring.datasource.username=prueba", "spring.datasource.password=prueba",
        "primerpaso.bd.comprobar-al-iniciar=false"
})
class VistasVacantesTests {

    private static final UsuarioSesion EMPRESA = new UsuarioSesion(22L, "Luis", "Gómez",
            "luis@example.test", "empresa", 69L, "Empresa de prueba", "administrador");
    private static final UsuarioSesion POSTULANTE = new UsuarioSesion(21L, "Ana", "Pérez",
            "ana@example.test", "postulante", null, null, null);
    private static final Instant VERSION = Instant.parse("2026-10-05T14:00:00Z");
    private static final Instant VENCIMIENTO = Instant.parse("2027-03-31T04:59:00Z");
    private static final Catalogos CATALOGOS = new Catalogos(
            List.of(new OpcionCatalogo(4L, "Ingeniería de Sistemas")),
            List.of(new OpcionCatalogo(8L, "Java"), new OpcionCatalogo(9L, "PostgreSQL")));

    @Autowired private WebApplicationContext contexto;
    @Autowired private FiltroAccesoPanel filtro;
    @MockitoBean private ServicioSesion sesiones;
    @MockitoBean private ServicioVacantes vacantes;
    private MockMvc cliente;

    @BeforeEach
    void prepararClienteConRenderizadoThymeleafReal() {
        cliente = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtro).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/panel/empresa/vacantes", "/panel/empresa/vacantes/nueva",
            "/panel/empresa/vacantes/7/editar"})
    void redirigeVisitantesAnonimosAntesDeConsultarInformacionEmpresarial(String ruta) throws Exception {
        cliente.perform(get(ruta)).andExpect(status().isFound())
                .andExpect(redirectedUrl("/iniciar-sesion"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(sesiones, vacantes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/panel/empresa/vacantes", "/panel/empresa/vacantes/nueva",
            "/panel/empresa/vacantes/7/editar"})
    void impideIngresoDePostulantesSinExponerLaEmpresa(String ruta) throws Exception {
        when(sesiones.consultarUsuario(21L, "postulante", null)).thenReturn(POSTULANTE);
        String html = cliente.perform(get(ruta).session(sesionDe(POSTULANTE)))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).doesNotContain(EMPRESA.nombreEmpresa(), EMPRESA.correo());
        verifyNoInteractions(vacantes);
    }

    @Test
    void muestraListaPaginadaConFiltroYDatosRealesDelServicio() throws Exception {
        autorizarEmpresa();
        PaginaVacantes pagina = new PaginaVacantes(List.of(vacante("publicada", false)), 1, 3, 26);
        when(vacantes.listar(EMPRESA, 1, "publicada")).thenReturn(pagina);

        String html = cliente.perform(get("/panel/empresa/vacantes").param("pagina", "1")
                        .param("estado", "publicada").session(sesionDe(EMPRESA)))
                .andExpect(status().isOk()).andExpect(view().name("vacantes/lista"))
                .andExpect(model().attribute("paginaVacantes", pagina))
                .andExpect(model().attribute("estadoFiltro", "publicada"))
                .andExpect(model().attribute("usuario", EMPRESA))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Desarrollador Java", "26 vacantes", "Página 2 de 3", "Lima, PE",
                        "30/03/2027 23:59", "data-accion=\"cerrar\"", "href=\"/panel/empresa/vacantes/7/editar\"")
                .doesNotContain("data-accion=\"publicar\"", "th:text", "th:if", "th:replace");
        verify(vacantes).listar(EMPRESA, 1, "publicada");
    }

    @Test
    void muestraElEstadoVacioSinInventarOportunidades() throws Exception {
        autorizarEmpresa();
        when(vacantes.listar(EMPRESA, 0, "todos")).thenReturn(new PaginaVacantes(List.of(), 0, 0, 0));
        String html = cliente.perform(get("/panel/empresa/vacantes").session(sesionDe(EMPRESA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Aún no tienes vacantes", "Crear primera vacante", "0 vacantes")
                .doesNotContain("Desarrollador Java", "data-accion=\"publicar\"", "data-accion=\"cerrar\"");
    }

    @Test
    void muestraFormularioNuevoConCatalogosYSinIdentidadDeVacante() throws Exception {
        autorizarEmpresa();
        when(vacantes.catalogos(EMPRESA)).thenReturn(CATALOGOS);

        String html = cliente.perform(get("/panel/empresa/vacantes/nueva").session(sesionDe(EMPRESA)))
                .andExpect(status().isOk()).andExpect(view().name("vacantes/formulario"))
                .andExpect(model().attribute("esNueva", true)).andExpect(model().attribute("soloLectura", false))
                .andExpect(model().attribute("catalogos", CATALOGOS))
                .andExpect(model().attribute("habilidadesSeleccionadas", List.of()))
                .andExpect(model().attribute("fechaVencimientoLocal", ""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Guardar borrador", "Ingeniería de Sistemas", "PostgreSQL", "value=\"PE\"")
                .doesNotContain("data-id-vacante=", "data-version=", "data-accion=\"publicar\"",
                        "data-accion=\"cerrar\"", "th:value");
        verify(vacantes).catalogos(EMPRESA);
    }

    @Test
    void precargaEdicionConHabilidadesYHoraDeLima() throws Exception {
        autorizarEmpresa();
        Vacante vacante = vacante("borrador", false);
        when(vacantes.catalogos(EMPRESA)).thenReturn(CATALOGOS);
        when(vacantes.consultar(EMPRESA, 7L)).thenReturn(vacante);

        String html = cliente.perform(get("/panel/empresa/vacantes/7/editar").session(sesionDe(EMPRESA)))
                .andExpect(status().isOk()).andExpect(view().name("vacantes/formulario"))
                .andExpect(model().attribute("vacante", vacante))
                .andExpect(model().attribute("esNueva", false)).andExpect(model().attribute("soloLectura", false))
                .andExpect(model().attribute("habilidadesSeleccionadas", List.of(8L, 9L)))
                .andExpect(model().attribute("fechaVencimientoLocal", "2027-03-30T23:59"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Guardar cambios", "value=\"Desarrollador Java\"", "Desarrollo de sistemas web.",
                        "data-id-vacante=\"7\"", "data-version=\"2026-10-05T14:00:00Z\"", "data-accion=\"publicar\"")
                .doesNotContain("th:selected", "th:checked");
    }

    @ParameterizedTest
    @MethodSource("vacantesDeSoloLectura")
    void conservaDetallesDeCerradasOVencidasSinPermitirEdicion(String estado, boolean vencida)
            throws Exception {
        autorizarEmpresa();
        when(vacantes.catalogos(EMPRESA)).thenReturn(CATALOGOS);
        when(vacantes.consultar(EMPRESA, 7L)).thenReturn(vacante(estado, vencida));

        String html = cliente.perform(get("/panel/empresa/vacantes/7/editar").session(sesionDe(EMPRESA)))
                .andExpect(status().isOk()).andExpect(model().attribute("soloLectura", true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Detalle de vacante", "<fieldset disabled=\"disabled\">", "Desarrollador Java")
                .doesNotContain("Guardar cambios", "data-accion=\"publicar\"");
        if ("cerrada".equals(estado)) {
            assertThat(html).doesNotContain("data-accion=\"cerrar\"");
        }
    }

    static Stream<Arguments> vacantesDeSoloLectura() {
        return Stream.of(Arguments.of("cerrada", false), Arguments.of("publicada", true));
    }

    @Test
    void permiteConsultarCatalogosInactivosSinOcultarDatosYaGuardados() throws Exception {
        autorizarEmpresa();
        when(vacantes.catalogos(EMPRESA)).thenReturn(new Catalogos(List.of(), List.of(new OpcionCatalogo(8L, "Java"))));
        when(vacantes.consultar(EMPRESA, 7L)).thenReturn(vacante("borrador", false));

        String html = cliente.perform(get("/panel/empresa/vacantes/7/editar").session(sesionDe(EMPRESA)))
                .andExpect(status().isOk()).andExpect(model().attribute("carreraInactiva", true))
                .andExpect(model().attribute("habilidadesInactivas", List.of(new Habilidad(9L, "PostgreSQL"))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Ingeniería de Sistemas (no disponible)", "PostgreSQL", "ya no están disponibles");
    }

    @Test
    void escapaTituloYDescripcionEnEdicionParaImpedirInyeccionHtml() throws Exception {
        autorizarEmpresa();
        Vacante base = vacante("borrador", false);
        String contenido = "<script>alert('xss')</script>";
        Vacante alterada = new Vacante(base.id(), contenido, contenido, base.idCarrera(), base.nombreCarrera(),
                base.habilidades(), base.modalidad(), base.ciudad(), base.codigoPais(), base.estado(),
                base.fechaCreacion(), base.fechaPublicacion(), base.fechaVencimiento(), base.version(), false);
        when(vacantes.catalogos(EMPRESA)).thenReturn(CATALOGOS);
        when(vacantes.consultar(EMPRESA, 7L)).thenReturn(alterada);
        String html = cliente.perform(get("/panel/empresa/vacantes/7/editar").session(sesionDe(EMPRESA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("&lt;script&gt;").doesNotContain(contenido);
    }

    @Test
    void ocultaFalloDeBaseSinRomperLaSesion() throws Exception {
        autorizarEmpresa();
        MockHttpSession sesion = sesionDe(EMPRESA);
        when(vacantes.listar(EMPRESA, 0, "todos"))
                .thenThrow(new DataAccessResourceFailureException("jdbc:postgresql://servidor password=secreto"));
        String html = cliente.perform(get("/panel/empresa/vacantes").session(sesion))
                .andExpect(status().isServiceUnavailable()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).doesNotContain("jdbc:", "password", "secreto", EMPRESA.correo());
        assertThat(sesion.isInvalid()).isFalse();
    }

    @Test
    void respetaPrefijoDeAplicacionEnEnlacesYRecursos() throws Exception {
        autorizarEmpresa();
        when(vacantes.catalogos(EMPRESA)).thenReturn(CATALOGOS);
        String html = cliente.perform(get("/primerpaso/panel/empresa/vacantes/nueva")
                        .contextPath("/primerpaso").session(sesionDe(EMPRESA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("href=\"/primerpaso/panel/empresa/vacantes\"", "src=\"/primerpaso/js/vacantes.js\"",
                        "data-ruta-base=\"/primerpaso/\"")
                .doesNotContain("src=\"/js/vacantes.js\"");
    }

    private void autorizarEmpresa() {
        when(sesiones.consultarUsuario(22L, "empresa", 69L)).thenReturn(EMPRESA);
    }

    private static MockHttpSession sesionDe(UsuarioSesion usuario) {
        var sesion = new MockHttpSession();
        sesion.setAttribute("primerpaso.idUsuario", usuario.idUsuario());
        sesion.setAttribute("primerpaso.tipoCuenta", usuario.tipoCuenta());
        if (usuario.idEmpresa() != null) {
            sesion.setAttribute("primerpaso.idEmpresa", usuario.idEmpresa());
        }
        return sesion;
    }

    private static Vacante vacante(String estado, boolean vencida) {
        return new Vacante(7L, "Desarrollador Java", "Desarrollo de sistemas web.", 4L, "Ingeniería de Sistemas",
                List.of(new Habilidad(8L, "Java"), new Habilidad(9L, "PostgreSQL")), "remota", "Lima", "PE",
                estado, VERSION, "borrador".equals(estado) ? null : VERSION,
                vencida ? VERSION.minusSeconds(1) : VENCIMIENTO, VERSION, vencida);
    }
}
