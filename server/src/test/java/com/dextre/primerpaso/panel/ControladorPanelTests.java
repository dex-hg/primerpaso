package com.dextre.primerpaso.panel;

import java.net.URI;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.dextre.primerpaso.sesion.AccesoNoAutorizadoException;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.sesion.ServicioSesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:postgresql://localhost:1/prueba",
        "spring.datasource.username=prueba",
        "spring.datasource.password=prueba",
        "primerpaso.bd.comprobar-al-iniciar=false"
})
class ControladorPanelTests {

    private static final UsuarioSesion POSTULANTE = new UsuarioSesion(21L, "Ana", "Pérez",
            "ana@example.test", "postulante", null, null, null);
    private static final UsuarioSesion EMPRESA = empresa("administrador");

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private FiltroAccesoPanel filtro;

    @MockitoBean
    private ServicioSesion servicio;

    private MockMvc cliente;

    @BeforeEach
    void prepararClienteConFiltroYRenderizadoReal() {
        cliente = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtro).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/cuenta", "/cuenta;marca=1", "/html/cuenta.html",
            "/html/cuenta.html;marca=1", "/panel", "/panel/",
            "/panel/postulante", "/panel/empresa", "/panel/ruta-pendiente"})
    void redirigeAccesoAnonimoAntesDeRenderizarDatosPrivados(String ruta) throws Exception {
        MvcResult resultado = cliente.perform(get(ruta))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/iniciar-sesion"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain(POSTULANTE.correo(), EMPRESA.correo(), EMPRESA.nombreEmpresa());
        assertThat(resultado.getRequest().getSession(false)).isNull();
        verifyNoInteractions(servicio);
    }

    @Test
    void renderizaPanelPostulanteConDatosValidadosDesdeServidor() throws Exception {
        when(servicio.consultarUsuario(21L, "postulante", null)).thenReturn(POSTULANTE);

        String html = cliente.perform(get("/panel/postulante").session(sesionDe(POSTULANTE)))
                .andExpect(status().isOk())
                .andExpect(view().name("panel/postulante"))
                .andExpect(model().attribute("usuario", POSTULANTE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<html", "lang=\"es\"", "<main", "Ana", POSTULANTE.correo())
                .doesNotContain("th:text", "th:replace", EMPRESA.correo(), EMPRESA.nombreEmpresa());
        verify(servicio).consultarUsuario(21L, "postulante", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"administrador", "reclutador"})
    void permitePanelEmpresarialConLaMembresiaYRolActuales(String rol) throws Exception {
        UsuarioSesion usuario = empresa(rol);
        when(servicio.consultarUsuario(22L, "empresa", 69L)).thenReturn(usuario);

        String html = cliente.perform(get("/panel/empresa").session(sesionDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(view().name("panel/empresa"))
                .andExpect(model().attribute("usuario", usuario))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains(usuario.nombreEmpresa(), usuario.correo(),
                        "id=\"panel-rol-empresa\"", "data-rol-empresa=\"" + rol + "\"")
                .doesNotContain(POSTULANTE.correo(), "th:text", "th:if");
        verify(servicio).consultarUsuario(22L, "empresa", 69L);
    }

    @ParameterizedTest
    @MethodSource("rutasPorTipoDeCuenta")
    void dirigeElAccesoGeneralAlPanelCorrespondiente(UsuarioSesion usuario, String ruta,
            String destino) throws Exception {
        when(servicio.consultarUsuario(usuario.idUsuario(), usuario.tipoCuenta(), usuario.idEmpresa()))
                .thenReturn(usuario);

        cliente.perform(get(ruta).session(sesionDe(usuario)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(destino))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    static Stream<Arguments> rutasPorTipoDeCuenta() {
        return Stream.of(POSTULANTE, EMPRESA)
                .map(usuario -> Arguments.of(usuario, "/panel", "/panel/" + usuario.tipoCuenta()));
    }

    @Test
    void conservaLaVistaDeCuentaYAliasSoloTrasValidarLaSesion() throws Exception {
        when(servicio.consultarUsuario(21L, "postulante", null)).thenReturn(POSTULANTE);
        MockHttpSession sesion = sesionDe(POSTULANTE);

        cliente.perform(get("/cuenta").session(sesion))
                .andExpect(status().isOk()).andExpect(view().name("cuenta"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        cliente.perform(get("/html/cuenta.html").session(sesion))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/cuenta"));

        verify(servicio, times(2)).consultarUsuario(21L, "postulante", null);
    }

    @ParameterizedTest
    @MethodSource("accesosCruzados")
    void rechazaAccesoAlPanelDelOtroTipoSinExponerDatos(UsuarioSesion usuario, String ruta)
            throws Exception {
        MockHttpSession sesion = sesionDe(usuario);
        when(servicio.consultarUsuario(usuario.idUsuario(), usuario.tipoCuenta(), usuario.idEmpresa()))
                .thenReturn(usuario);

        String html = cliente.perform(get(ruta).session(sesion))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<html")
                .doesNotContain(usuario.correo(), POSTULANTE.correo(), EMPRESA.nombreEmpresa());
        assertThat(sesion.isInvalid()).isFalse();
    }

    static Stream<Arguments> accesosCruzados() {
        return Stream.of(Arguments.of(POSTULANTE, "/panel/empresa"),
                Arguments.of(EMPRESA, "/panel/postulante"));
    }

    @ParameterizedTest
    @MethodSource("rutasPrivadasAlternativas")
    void mantienePermisosAlNormalizarRutasYRechazaRutasSinPermiso(String ruta,
            HttpMethod metodo) throws Exception {
        when(servicio.consultarUsuario(21L, "postulante", null)).thenReturn(POSTULANTE);

        MvcResult resultado = cliente.perform(request(metodo, URI.create(ruta)).session(sesionDe(POSTULANTE)))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(POSTULANTE.correo(), EMPRESA.nombreEmpresa());
        verify(servicio).consultarUsuario(21L, "postulante", null);
    }

    static Stream<Arguments> rutasPrivadasAlternativas() {
        return Stream.of("/panel;marca=1/empresa", "/%70anel/empresa", "/panel//empresa",
                        "/panel/ruta-pendiente")
                .flatMap(ruta -> Stream.of(HttpMethod.GET, HttpMethod.HEAD)
                        .map(metodo -> Arguments.of(ruta, metodo)));
    }

    @ParameterizedTest
    @MethodSource("atributosInvalidos")
    void revocaSesionesIncompletasOMalformadasAntesDeConsultarLaBase(UsuarioSesion usuario,
            String atributo, Object valor) throws Exception {
        MockHttpSession sesion = sesionDe(usuario);
        sesion.setAttribute(atributo, valor);

        MvcResult resultado = cliente.perform(get("/panel/postulante").session(sesion))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/iniciar-sesion"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        assertThat(sesion.isInvalid()).isTrue();
        comprobarCookieBorrada(resultado, "/");
        verifyNoInteractions(servicio);
    }

    static Stream<Arguments> atributosInvalidos() {
        return Stream.of(Arguments.of(POSTULANTE, "primerpaso.idUsuario", null),
                Arguments.of(POSTULANTE, "primerpaso.idUsuario", 21),
                Arguments.of(POSTULANTE, "primerpaso.idUsuario", -1L),
                Arguments.of(POSTULANTE, "primerpaso.tipoCuenta", null),
                Arguments.of(POSTULANTE, "primerpaso.tipoCuenta", "administrador"),
                Arguments.of(POSTULANTE, "primerpaso.tipoCuenta", "empresa"),
                Arguments.of(POSTULANTE, "primerpaso.idEmpresa", 69L),
                Arguments.of(POSTULANTE, "primerpaso.idEmpresa", "69"),
                Arguments.of(EMPRESA, "primerpaso.idEmpresa", null),
                Arguments.of(EMPRESA, "primerpaso.idEmpresa", "69"),
                Arguments.of(EMPRESA, "primerpaso.idEmpresa", 0L));
    }

    @Test
    void revocaSesionSiLaCuentaOLaMembresiaYaNoEstanActivas() throws Exception {
        MockHttpSession sesion = sesionDe(EMPRESA);
        when(servicio.consultarUsuario(22L, "empresa", 69L))
                .thenThrow(new AccesoNoAutorizadoException("Datos privados de la membresía."));

        MvcResult resultado = cliente.perform(get("/panel/empresa").session(sesion))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/iniciar-sesion"))
                .andReturn();

        assertThat(sesion.isInvalid()).isTrue();
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("Datos privados");
        comprobarCookieBorrada(resultado, "/");
    }

    @Test
    void conservaSesionDuranteFalloTemporalYOcultaDetallesDeLaBase() throws Exception {
        MockHttpSession sesion = sesionDe(POSTULANTE);
        when(servicio.consultarUsuario(21L, "postulante", null)).thenThrow(
                new DataAccessResourceFailureException("jdbc:postgresql://localhost/prueba password=clave_secreta"))
                .thenReturn(POSTULANTE);

        String html = cliente.perform(get("/panel/postulante").session(sesion))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<html")
                .doesNotContain("jdbc:", "password", "clave_secreta", "localhost", POSTULANTE.correo());
        assertThat(sesion.isInvalid()).isFalse();
        cliente.perform(get("/panel/postulante").session(sesion)).andExpect(status().isOk());
    }

    @Test
    void vuelveAConsultarLaMembresiaYActualizaElRolEnCadaSolicitud() throws Exception {
        UsuarioSesion reclutador = empresa("reclutador");
        when(servicio.consultarUsuario(22L, "empresa", 69L)).thenReturn(EMPRESA, reclutador);
        MockHttpSession sesion = sesionDe(EMPRESA);

        cliente.perform(get("/panel/empresa").session(sesion))
                .andExpect(status().isOk()).andExpect(model().attribute("usuario", EMPRESA));
        cliente.perform(get("/panel/empresa").session(sesion))
                .andExpect(status().isOk()).andExpect(model().attribute("usuario", reclutador));

        verify(servicio, times(2)).consultarUsuario(22L, "empresa", 69L);
    }

    @Test
    void escapaDatosPersonalesParaImpedirInyeccionHtml() throws Exception {
        UsuarioSesion usuario = new UsuarioSesion(21L, "<script>alert('xss')</script>", "Pérez",
                POSTULANTE.correo(), "postulante", null, null, null);
        when(servicio.consultarUsuario(21L, "postulante", null)).thenReturn(usuario);

        String html = cliente.perform(get("/panel/postulante").session(sesionDe(usuario)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("&lt;script&gt;")
                .doesNotContain("<script>alert('xss')</script>");
    }

    @Test
    void respetaContextoEnRedireccionAnonimaYBorradoDeCookie() throws Exception {
        MockHttpSession sesion = sesionDe(POSTULANTE);
        sesion.removeAttribute("primerpaso.tipoCuenta");

        MvcResult resultado = cliente.perform(get("/primerpaso/panel/postulante")
                        .contextPath("/primerpaso").session(sesion))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/primerpaso/iniciar-sesion"))
                .andReturn();

        comprobarCookieBorrada(resultado, "/");
    }

    @Test
    void respetaContextoEnRedireccionYRecursosDelPanelAutenticado() throws Exception {
        when(servicio.consultarUsuario(21L, "postulante", null)).thenReturn(POSTULANTE);
        MockHttpSession sesion = sesionDe(POSTULANTE);

        cliente.perform(get("/primerpaso/panel").contextPath("/primerpaso").session(sesion))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/primerpaso/panel/postulante"));
        String html = cliente.perform(get("/primerpaso/panel/postulante")
                        .contextPath("/primerpaso").session(sesion))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("href=\"/primerpaso/css/styles.css\"")
                .doesNotContain("href=\"/css/styles.css\"", "src=\"/js/auth.js\"");
    }

    private static UsuarioSesion empresa(String rol) {
        return new UsuarioSesion(22L, "Luis", "Gómez", "luis@example.test",
                "empresa", 69L, "Empresa de prueba", rol);
    }

    private MockHttpSession sesionDe(UsuarioSesion usuario) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute("primerpaso.idUsuario", usuario.idUsuario());
        sesion.setAttribute("primerpaso.tipoCuenta", usuario.tipoCuenta());
        if (usuario.idEmpresa() != null) {
            sesion.setAttribute("primerpaso.idEmpresa", usuario.idEmpresa());
        }
        return sesion;
    }

    private void comprobarCookieBorrada(MvcResult resultado, String ruta) {
        assertThat(resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("PRIMERPASO_SESION=")
                .contains("Path=" + ruta, "HttpOnly", "SameSite=Strict", "Max-Age=0");
    }
}
