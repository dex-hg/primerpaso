package com.dextre.primerpaso.configuracion;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.dextre.primerpaso.panel.FiltroAccesoPanel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:postgresql://localhost:1/prueba",
        "spring.datasource.username=prueba",
        "spring.datasource.password=prueba",
        "primerpaso.bd.comprobar-al-iniciar=false"
})
class ControladorVistasTests {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private FiltroAccesoPanel filtro;

    private MockMvc cliente;

    @BeforeEach
    void prepararClienteConRenderizadoReal() {
        cliente = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtro).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/iniciar-sesion", "/registro", "/registro-postulante",
            "/registro-empresa", "/contacto"})
    void renderizaCadaVistaConCabeceraPieYEstilos(String ruta) throws Exception {
        String html = obtenerHtml(ruta);
        String tipoFragmento = ruta.equals("/") || ruta.equals("/contacto") ? "site" : "auth";

        assertThat(html)
                .contains("<html", "lang=\"es\"", "<main", "PrimerPaso")
                .contains("<header class=\"" + tipoFragmento + "-header")
                .contains("<footer class=\"" + tipoFragmento + "-footer")
                .contains("href=\"/css/styles.css\"")
                .doesNotContain("th:replace", "th:href", "th:src", "th:each", "th:text");
    }

    @Test
    void inicioRenderizaTodasLasCategoriasYTerminosDeBusqueda() throws Exception {
        String html = obtenerHtml("/");

        assertThat(html)
                .contains("<strong>Tecnología</strong>", "<strong>Administración</strong>")
                .contains("<strong>Marketing</strong>", "<strong>Diseño</strong>")
                .contains("data-search-term=\"tecnología\"",
                        "data-search-term=\"administración\"",
                        "data-search-term=\"marketing\"", "data-search-term=\"diseño\"")
                .contains("bi-code-slash", "bi-bar-chart", "bi-megaphone", "bi-vector-pen");
        assertThat(html.split("class=\"category-card\"", -1)).hasSize(5);
    }

    @Test
    void seleccionDeRegistroEnlazaAmbosTiposDeCuenta() throws Exception {
        String html = obtenerHtml("/registro");

        assertThat(html).contains("href=\"/registro-postulante\"",
                "href=\"/registro-empresa\"", "href=\"/iniciar-sesion\"");
    }

    @ParameterizedTest
    @MethodSource("formulariosDeRegistro")
    void preservaContratoYValidacionesDelFormularioDeRegistro(String ruta,
            String identificador, String tipo, String contrasena) throws Exception {
        String html = obtenerHtml(ruta);

        assertThat(html)
                .contains("id=\"" + identificador + "\"", "data-multi-step")
                .contains("data-tipo-registro=\"" + tipo + "\"")
                .contains("data-step-panel=\"1\"", "data-step-panel=\"2\"",
                        "data-step-panel=\"3\"")
                .contains("minlength=\"8\"", "maxlength=\"128\"", "required")
                .contains("data-confirm-password=\"#" + contrasena + "\"")
                .contains("data-registro-acceso", "href=\"/iniciar-sesion\"")
                .contains("src=\"/js/registro.js\"", "src=\"/js/auth.js\"");
    }

    static Stream<Arguments> formulariosDeRegistro() {
        return Stream.of(
                Arguments.of("/registro-postulante", "applicant-register-form",
                        "postulante", "applicant-password"),
                Arguments.of("/registro-empresa", "company-register-form",
                        "empresa", "recruiter-password"));
    }

    @Test
    void contactoIncluyeSuFormularioYLosScriptsNecesarios() throws Exception {
        String html = obtenerHtml("/contacto");

        assertThat(html)
                .contains("<body class=\"contact-page\"")
                .contains("id=\"contact-form\"", "id=\"contact-status\"")
                .contains("src=\"/js/contacto.js\"", "src=\"/js/main.js\"");
    }

    @ParameterizedTest
    @MethodSource("recursosEstaticos")
    void sirveRecursosEstaticosConContenidoYTipoCorrectos(String ruta,
            String tipoContenido, String marcador) throws Exception {
        String recurso = cliente.perform(get(ruta))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(tipoContenido))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(recurso).isNotBlank().contains(marcador).doesNotContain("<!doctype html>");
    }

    static Stream<Arguments> recursosEstaticos() {
        return Stream.of(
                Arguments.of("/css/styles.css", "text/css", ".site-header"),
                Arguments.of("/js/main.js", "text/javascript", "DOMContentLoaded"));
    }

    @ParameterizedTest
    @MethodSource("rutasAntiguas")
    void redirigeEnlacesAntiguosALasRutasDeThymeleaf(String anterior,
            String actual) throws Exception {
        cliente.perform(get(anterior))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(actual));
    }

    static Stream<Arguments> rutasAntiguas() {
        return Stream.of(
                Arguments.of("/index.html", "/"),
                Arguments.of("/html/iniciar-sesion.html", "/iniciar-sesion"),
                Arguments.of("/html/registro.html", "/registro"),
                Arguments.of("/html/registro-postulante.html", "/registro-postulante"),
                Arguments.of("/html/registro-empresa.html", "/registro-empresa"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/cuenta", "/html/cuenta.html"})
    void exigeInicioDeSesionParaLasRutasDeCuenta(String ruta) throws Exception {
        cliente.perform(get(ruta))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/iniciar-sesion"));
    }

    @Test
    void respetaElContextoEnEstilosYEnlacesDeLaCabecera() throws Exception {
        String html = cliente.perform(get("/primerpaso/").contextPath("/primerpaso"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html)
                .contains("href=\"/primerpaso/css/styles.css\"")
                .contains("href=\"/primerpaso/#inicio\"")
                .contains("href=\"/primerpaso/iniciar-sesion\"")
                .contains("href=\"/primerpaso/registro\"")
                .doesNotContain("href=\"/css/styles.css\"", "href=\"/iniciar-sesion\"");
    }

    private String obtenerHtml(String ruta) throws Exception {
        return cliente.perform(get(ruta))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
