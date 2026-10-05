package com.dextre.primerpaso.sesion;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.dextre.primerpaso.sesion.DatosSesion.SolicitudInicioSesion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControladorSesionTests {

    private static final String RUTA = "/api/sesion";
    private static final String SOLICITUD_VALIDA = """
            {"correo":" ANA@EXAMPLE.TEST ","contrasena":" Clave á 123 ","tipoCuenta":"postulante"}
            """;
    private static final UsuarioSesion POSTULANTE = new UsuarioSesion(21, "Ana", "Pérez",
            "ana@example.test", "postulante", null, null, null);
    private ServicioSesion servicio;
    private MockMvc cliente;

    @BeforeEach
    void prepararControlador() {
        servicio = mock(ServicioSesion.class);
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        cliente = MockMvcBuilders.standaloneSetup(new ControladorSesion(servicio, new AccesoSesion(servicio, false)))
                .setControllerAdvice(new ManejadorErroresSesion()).setValidator(validador).build();
    }

    @Test
    void entregaTokenDeSeguridadSinCacheYLoConservaEnLaMismaSesion() throws Exception {
        Seguridad seguridad = obtenerSeguridad();
        assertThat(seguridad.token()).matches("[A-Za-z0-9_-]{43}");
        cliente.perform(get(RUTA + "/seguridad").session(seguridad.sesion()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenCsrf").value(seguridad.token()));
        verifyNoInteractions(servicio);
    }

    @Test
    void rechazaInicioYCierreSinSesionPreviaAunqueSeEnvieUnToken() throws Exception {
        cliente.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD_VALIDA)
                .header("X-CSRF-Token", "A".repeat(43)))
                .andExpect(status().isForbidden());
        cliente.perform(delete(RUTA).header("X-CSRF-Token", "A".repeat(43)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"token-corto", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void rechazaTokensAusentesOIncorrectosAntesDeConsultarCredenciales(String token) throws Exception {
        Seguridad seguridad = obtenerSeguridad();
        var solicitud = post(RUTA).session(seguridad.sesion())
                .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD_VALIDA);
        if (token != null) {
            solicitud.header("X-CSRF-Token", token);
        }
        cliente.perform(solicitud).andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @MethodSource("solicitudesInvalidas")
    void rechazaCamposInvalidosAntesDeConsultarCredenciales(String contenido) throws Exception {
        Seguridad seguridad = obtenerSeguridad();
        cliente.perform(post(RUTA).session(seguridad.sesion()).header("X-CSRF-Token", seguridad.token())
                .contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(servicio);
    }

    static Stream<String> solicitudesInvalidas() {
        return Stream.of(SOLICITUD_VALIDA.replace(" ANA@EXAMPLE.TEST ", "correo-invalido"),
                SOLICITUD_VALIDA.replace(" Clave á 123 ", "corta"),
                SOLICITUD_VALIDA.replace(" Clave á 123 ", "x".repeat(129)),
                SOLICITUD_VALIDA.replace(" Clave á 123 ", "        "),
                SOLICITUD_VALIDA.replace("postulante", "administrador"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{", "{}"})
    void rechazaCuerposNulosIncompletosOMalformados(String contenido) throws Exception {
        Seguridad seguridad = obtenerSeguridad();
        cliente.perform(post(RUTA).session(seguridad.sesion()).header("X-CSRF-Token", seguridad.token())
                .contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void devuelveErrorGenericoDeCredencialesYPermiteReintentar() throws Exception {
        Seguridad seguridad = obtenerSeguridad();
        when(servicio.iniciarSesion(any())).thenThrow(new AccesoNoAutorizadoException(
                "Correo, contraseña o tipo de cuenta incorrectos."));
        cliente.perform(post(RUTA).session(seguridad.sesion()).header("X-CSRF-Token", seguridad.token())
                .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD_VALIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.mensaje").value("Correo, contraseña o tipo de cuenta incorrectos."));
        assertThat(seguridad.sesion().isInvalid()).isFalse();
    }

    @Test
    void completaInicioConsultaYCierreRotandoSesionYToken() throws Exception {
        Seguridad anterior = obtenerSeguridad();
        String idAnterior = anterior.sesion().getId();
        when(servicio.iniciarSesion(any())).thenReturn(POSTULANTE);
        when(servicio.consultarUsuario(21, "postulante", null)).thenReturn(POSTULANTE);
        MvcResult inicio = cliente.perform(post(RUTA).session(anterior.sesion())
                .header("X-CSRF-Token", anterior.token()).contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD_VALIDA)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(8)))
                .andExpect(jsonPath("$.idUsuario").value(21))
                .andExpect(jsonPath("$.contrasena").doesNotExist()).andReturn();
        MockHttpSession nueva = (MockHttpSession) inicio.getRequest().getSession(false);
        assertThat(anterior.sesion().isInvalid()).isTrue();
        assertThat(nueva.getId()).isNotEqualTo(idAnterior);
        String tokenNuevo = JsonPath.read(cliente.perform(get(RUTA + "/seguridad").session(nueva))
                .andReturn().getResponse().getContentAsString(), "$.tokenCsrf");
        assertThat(tokenNuevo).isNotEqualTo(anterior.token());
        cliente.perform(get(RUTA).session(nueva)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.correo").value("ana@example.test"));
        cliente.perform(delete(RUTA).session(nueva)).andExpect(status().isForbidden());
        cliente.perform(delete(RUTA).session(nueva).header("X-CSRF-Token", anterior.token()))
                .andExpect(status().isForbidden());
        assertThat(nueva.isInvalid()).isFalse();
        MvcResult cierre = cliente.perform(delete(RUTA).session(nueva).header("X-CSRF-Token", tokenNuevo))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andReturn();
        comprobarCookieBorrada(cierre);
        assertThat(nueva.isInvalid()).isTrue();
        cliente.perform(get(RUTA)).andExpect(status().isUnauthorized());
        verify(servicio).iniciarSesion(new SolicitudInicioSesion("ana@example.test", " Clave á 123 ", "postulante"));
        verify(servicio).consultarUsuario(21, "postulante", null);
    }

    @Test
    void exigeSesionAutenticadaParaConsultarUsuario() throws Exception {
        cliente.perform(get(RUTA)).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(servicio);
    }

    @Test
    void invalidaSesionPreviaAlInicioCuandoSeConsultaUsuario() throws Exception {
        Seguridad seguridad = obtenerSeguridad();
        MvcResult resultado = cliente.perform(get(RUTA).session(seguridad.sesion()))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(seguridad.sesion().isInvalid()).isTrue();
        comprobarCookieBorrada(resultado);
        verifyNoInteractions(servicio);
    }

    @Test
    void consultaEmpresaConLaMembresiaExactaGuardadaEnLaSesion() throws Exception {
        UsuarioSesion empresa = new UsuarioSesion(22, "Luis", "Pérez", "luis@example.test",
                "empresa", 69L, "Empresa de prueba", "administrador");
        MockHttpSession sesion = autenticar(empresa);
        when(servicio.consultarUsuario(22, "empresa", 69L)).thenReturn(empresa);
        cliente.perform(get(RUTA).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idEmpresa").value(69))
                .andExpect(jsonPath("$.nombreEmpresa").value("Empresa de prueba"));
        verify(servicio).consultarUsuario(22, "empresa", 69L);
    }

    @Test
    void invalidaSesionDeEmpresaSiFaltaSuMembresia() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute("primerpaso.idUsuario", 22L);
        sesion.setAttribute("primerpaso.tipoCuenta", "empresa");
        MvcResult resultado = cliente.perform(get(RUTA).session(sesion))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(sesion.isInvalid()).isTrue();
        comprobarCookieBorrada(resultado);
        verifyNoInteractions(servicio);
    }

    @Test
    void invalidaLaSesionSiLaCuentaFueSuspendidaOSeRetiroLaMembresia() throws Exception {
        MockHttpSession sesion = autenticar(POSTULANTE);
        when(servicio.consultarUsuario(21, "postulante", null))
                .thenThrow(new AccesoNoAutorizadoException("Inicia sesión para continuar."));
        MvcResult resultado = cliente.perform(get(RUTA).session(sesion))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(sesion.isInvalid()).isTrue();
        comprobarCookieBorrada(resultado);
    }

    @Test
    void ocultaDetallesDeBaseDeDatosCuandoFallaElInicio() throws Exception {
        Seguridad seguridad = obtenerSeguridad();
        when(servicio.iniciarSesion(any())).thenThrow(falloBaseDatos());
        String respuesta = cliente.perform(post(RUTA).session(seguridad.sesion())
                .header("X-CSRF-Token", seguridad.token()).contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD_VALIDA)).andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain("jdbc:", "password", "clave_secreta", "localhost");
        assertThat(seguridad.sesion().isInvalid()).isFalse();
    }

    @Test
    void permiteReintentarLaConsultaTrasUnaFallaTemporalDeBaseDeDatos() throws Exception {
        MockHttpSession sesion = autenticar(POSTULANTE);
        when(servicio.consultarUsuario(21, "postulante", null)).thenThrow(falloBaseDatos());
        String respuesta = cliente.perform(get(RUTA).session(sesion))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain("jdbc:", "password", "clave_secreta", "localhost");
        assertThat(sesion.isInvalid()).isFalse();
    }

    private Seguridad obtenerSeguridad() throws Exception {
        MvcResult resultado = cliente.perform(get(RUTA + "/seguridad").session(new MockHttpSession()))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        return new Seguridad((MockHttpSession) resultado.getRequest().getSession(false),
                JsonPath.read(resultado.getResponse().getContentAsString(), "$.tokenCsrf"));
    }

    private MockHttpSession autenticar(UsuarioSesion usuario) throws Exception {
        Seguridad seguridad = obtenerSeguridad();
        when(servicio.iniciarSesion(any())).thenReturn(usuario);
        MvcResult resultado = cliente.perform(post(RUTA).session(seguridad.sesion())
                .header("X-CSRF-Token", seguridad.token()).contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD_VALIDA.replace("postulante", usuario.tipoCuenta())))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private void comprobarCookieBorrada(MvcResult resultado) {
        assertThat(resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("PRIMERPASO_SESION=").contains("Path=/", "HttpOnly", "SameSite=Strict", "Max-Age=0");
    }

    private DataAccessResourceFailureException falloBaseDatos() {
        return new DataAccessResourceFailureException("jdbc:postgresql://localhost/PrimerPaso password=clave_secreta");
    }

    private record Seguridad(MockHttpSession sesion, String token) {
    }
}
