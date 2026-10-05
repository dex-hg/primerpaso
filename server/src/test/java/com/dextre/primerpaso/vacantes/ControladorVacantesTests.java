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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.dextre.primerpaso.panel.FiltroAccesoPanel;
import com.dextre.primerpaso.sesion.AccesoNoAutorizadoException;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.sesion.ServicioSesion;
import com.dextre.primerpaso.vacantes.DatosVacantes.Habilidad;
import com.dextre.primerpaso.vacantes.DatosVacantes.PaginaVacantes;
import com.dextre.primerpaso.vacantes.DatosVacantes.SolicitudEstadoVacante;
import com.dextre.primerpaso.vacantes.DatosVacantes.SolicitudVacante;
import com.dextre.primerpaso.vacantes.DatosVacantes.Vacante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:postgresql://localhost:1/prueba",
        "spring.datasource.username=prueba", "spring.datasource.password=prueba",
        "primerpaso.bd.comprobar-al-iniciar=false"
})
class ControladorVacantesTests {

    private static final String TOKEN = "a".repeat(43);
    private static final Instant VERSION = Instant.parse("2026-10-05T14:00:00Z");
    private static final Instant VENCIMIENTO = Instant.parse("2027-03-31T04:59:00Z");
    private static final UsuarioSesion EMPRESA = empresa("administrador");
    private static final UsuarioSesion POSTULANTE = new UsuarioSesion(21L, "Ana", "Pérez",
            "ana@example.test", "postulante", null, null, null);
    private static final String DATOS = """
            {"titulo":" Desarrollador Java ","descripcion":" Desarrollo de sistemas web. ",
            "idCarrera":4,"habilidades":[8,9],"modalidad":" REMOTA ","ciudad":" Lima ",
            "codigoPais":" pe ","fechaVencimiento":"2027-03-31T04:59:00Z",
            "version":"2026-10-05T14:00:00Z"}
            """;
    private static final String ESTADO = "{\"version\":\"2026-10-05T14:00:00Z\"}";
    private static final String DATOS_CREACION = DATOS.replace(ESTADO.substring(1, ESTADO.length() - 1), "\"version\":null");

    @Autowired private WebApplicationContext contexto;
    @Autowired private FiltroAccesoPanel filtro;
    @MockitoBean private ServicioSesion sesiones;
    @MockitoBean private ServicioVacantes vacantes;
    private MockMvc cliente;

    @BeforeEach
    void prepararClienteConFiltroReal() {
        cliente = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtro).build();
    }

    @ParameterizedTest
    @MethodSource("solicitudesProtegidas")
    void rechazaAccesoAnonimoSinCrearSesion(HttpMethod metodo, String ruta, String cuerpo) throws Exception {
        var resultado = cliente.perform(solicitud(metodo, ruta, cuerpo))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.mensaje").isString()).andReturn();

        assertThat(resultado.getRequest().getSession(false)).isNull();
        verifyNoInteractions(sesiones, vacantes);
    }

    @ParameterizedTest
    @MethodSource("solicitudesProtegidas")
    void impideAccesoDePostulantesInclusoConTokenValido(HttpMethod metodo, String ruta, String cuerpo)
            throws Exception {
        when(sesiones.consultarUsuario(21L, "postulante", null)).thenReturn(POSTULANTE);
        cliente.perform(solicitud(metodo, ruta, cuerpo).session(sesionDe(POSTULANTE)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(vacantes);
    }

    static Stream<Arguments> solicitudesProtegidas() {
        return Stream.of(Arguments.of(HttpMethod.GET, "/api/vacantes", null),
                Arguments.of(HttpMethod.GET, "/api/vacantes/7", null),
                Arguments.of(HttpMethod.POST, "/api/vacantes", DATOS),
                Arguments.of(HttpMethod.PUT, "/api/vacantes/7", DATOS),
                Arguments.of(HttpMethod.POST, "/api/vacantes/7/publicar", ESTADO),
                Arguments.of(HttpMethod.POST, "/api/vacantes/7/cerrar", ESTADO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"administrador", "reclutador"})
    void creaVacanteConUsuarioRevalidadoYDatosNormalizados(String rol) throws Exception {
        UsuarioSesion usuarioActual = empresa(rol);
        when(sesiones.consultarUsuario(22L, "empresa", 69L)).thenReturn(usuarioActual);
        SolicitudVacante datosNuevos = new SolicitudVacante("Desarrollador Java", "Desarrollo de sistemas web.",
                4L, List.of(8L, 9L), "remota", "Lima", "PE", VENCIMIENTO, null);
        when(vacantes.crear(usuarioActual, datosNuevos)).thenReturn(vacante("borrador"));

        cliente.perform(post("/api/vacantes").session(sesionDe(EMPRESA))
                        .header("X-CSRF-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(DATOS_CREACION))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.LOCATION, "/api/vacantes/7"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.id").value(7)).andExpect(jsonPath("$.estado").value("borrador"))
                .andExpect(jsonPath("$.idEmpresa").doesNotExist());
        verify(vacantes).crear(usuarioActual, datosNuevos);
    }

    @Test
    void listaYConsultaSoloDentroDeLaEmpresaValidada() throws Exception {
        autorizarEmpresa();
        PaginaVacantes pagina = new PaginaVacantes(List.of(vacante("borrador")), 0, 1, 1);
        when(vacantes.listar(EMPRESA, 0, "todos")).thenReturn(pagina);
        when(vacantes.consultar(EMPRESA, 7L)).thenReturn(vacante("borrador"));

        cliente.perform(get("/api/vacantes").session(sesionDe(EMPRESA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.vacantes[0].id").value(7))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        cliente.perform(get("/api/vacantes/7").session(sesionDe(EMPRESA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.habilidades[0].nombre").value("Java"));
        verify(vacantes).listar(EMPRESA, 0, "todos");
        verify(vacantes).consultar(EMPRESA, 7L);
    }

    @Test
    void entregaEdicionYTransicionesAlServicioConLaVersionRecibida() throws Exception {
        autorizarEmpresa();
        SolicitudEstadoVacante estado = new SolicitudEstadoVacante(VERSION);
        when(vacantes.editar(EMPRESA, 7L, datos())).thenReturn(vacante("borrador"));
        when(vacantes.publicar(EMPRESA, 7L, estado)).thenReturn(vacante("publicada"));
        when(vacantes.cerrar(EMPRESA, 7L, estado)).thenReturn(vacante("cerrada"));

        cliente.perform(put("/api/vacantes/7").session(sesionDe(EMPRESA))
                        .header("X-CSRF-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("borrador"));
        for (String accion : List.of("publicar", "cerrar")) {
            cliente.perform(post("/api/vacantes/7/" + accion).session(sesionDe(EMPRESA))
                            .header("X-CSRF-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(ESTADO))
                    .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        }
        verify(vacantes).editar(EMPRESA, 7L, datos());
        verify(vacantes).publicar(EMPRESA, 7L, estado);
        verify(vacantes).cerrar(EMPRESA, 7L, estado);
    }

    @ParameterizedTest
    @MethodSource("mutacionesSinToken")
    void exigeProteccionCsrfAntesDeCadaMutacion(HttpMethod metodo, String ruta, String cuerpo,
            String token) throws Exception {
        autorizarEmpresa();
        MockHttpServletRequestBuilder peticion = request(metodo, ruta).session(sesionDe(EMPRESA))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (token != null) {
            peticion.header("X-CSRF-Token", token);
        }
        cliente.perform(peticion).andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(vacantes);
    }

    static Stream<Arguments> mutacionesSinToken() {
        return solicitudesProtegidas().filter(argumento -> argumento.get()[0] != HttpMethod.GET)
                .flatMap(argumento -> Stream.of(null, "b".repeat(43))
                        .map(token -> Arguments.of(argumento.get()[0], argumento.get()[1], argumento.get()[2], token)));
    }

    @ParameterizedTest
    @MethodSource("datosInvalidos")
    void rechazaDatosInvalidosSinEjecutarPersistencia(String cuerpo) throws Exception {
        autorizarEmpresa();
        cliente.perform(post("/api/vacantes").session(sesionDe(EMPRESA))
                        .header("X-CSRF-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isString())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(vacantes);
    }

    static Stream<String> datosInvalidos() {
        return Stream.of(DATOS_CREACION.replace(" Desarrollador Java ", " "), DATOS_CREACION.replace("\"idCarrera\":4", "\"idCarrera\":0"),
                DATOS_CREACION.replace("[8,9]", "[]"), DATOS_CREACION.replace("[8,9]", "[8,null]"),
                DATOS_CREACION.replace(" REMOTA ", "alternada"), DATOS_CREACION.replace(" Lima ", " "),
                DATOS_CREACION.replace(" pe ", "PER"), DATOS_CREACION.replace("\"fechaVencimiento\":\"2027-03-31T04:59:00Z\"", "\"fechaVencimiento\":null"),
                DATOS_CREACION.replace("\"titulo\":", "\"idEmpresa\":999,\"titulo\":"), "{malformado}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"publicar", "cerrar"})
    void rechazaCambioDeEstadoSinVersion(String accion) throws Exception {
        autorizarEmpresa();
        cliente.perform(post("/api/vacantes/7/" + accion).session(sesionDe(EMPRESA))
                        .header("X-CSRF-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(vacantes);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 409})
    void conservaErroresDePertenenciaYConflictoSinEntregarLaVacante(int estado) throws Exception {
        autorizarEmpresa();
        when(vacantes.editar(EMPRESA, 7L, datos())).thenThrow(new ReglaVacanteException(estado, "La vacante no está disponible."));
        cliente.perform(put("/api/vacantes/7").session(sesionDe(EMPRESA)).header("X-CSRF-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().is(estado)).andExpect(jsonPath("$.mensaje").value("La vacante no está disponible."))
                .andExpect(jsonPath("$.titulo").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void revocaSesionCuandoLaMembresiaDejaDeSerValida() throws Exception {
        MockHttpSession sesion = sesionDe(EMPRESA);
        when(sesiones.consultarUsuario(22L, "empresa", 69L))
                .thenThrow(new AccesoNoAutorizadoException("Detalle privado de membresía."));
        cliente.perform(get("/api/vacantes").session(sesion)).andExpect(status().isUnauthorized());
        assertThat(sesion.isInvalid()).isTrue();
        verifyNoInteractions(vacantes);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ocultaDetallesInternosDuranteFalloDeBaseYConservaLaSesion(boolean fallaSesion) throws Exception {
        MockHttpSession sesion = sesionDe(EMPRESA);
        var error = new DataAccessResourceFailureException("jdbc:postgresql://servidor password=secreto tabla=vacancies");
        if (fallaSesion) {
            when(sesiones.consultarUsuario(22L, "empresa", 69L)).thenThrow(error);
        } else {
            autorizarEmpresa();
            when(vacantes.consultar(EMPRESA, 7L)).thenThrow(error);
        }
        String respuesta = cliente.perform(get("/api/vacantes/7").session(sesion))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(respuesta).doesNotContain("jdbc:", "password", "secreto", "vacancies", EMPRESA.correo());
        assertThat(sesion.isInvalid()).isFalse();
    }

    private void autorizarEmpresa() {
        when(sesiones.consultarUsuario(22L, "empresa", 69L)).thenReturn(EMPRESA);
    }

    private static MockHttpServletRequestBuilder solicitud(HttpMethod metodo, String ruta, String cuerpo) {
        var peticion = request(metodo, ruta).header("X-CSRF-Token", TOKEN);
        return cuerpo == null ? peticion : peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static UsuarioSesion empresa(String rol) {
        return new UsuarioSesion(22L, "Luis", "Gómez", "luis@example.test", "empresa", 69L, "Empresa de prueba", rol);
    }

    private static MockHttpSession sesionDe(UsuarioSesion usuario) {
        var sesion = new MockHttpSession();
        sesion.setAttribute("primerpaso.idUsuario", usuario.idUsuario());
        sesion.setAttribute("primerpaso.tipoCuenta", usuario.tipoCuenta());
        sesion.setAttribute("primerpaso.tokenCsrf", TOKEN);
        if (usuario.idEmpresa() != null) {
            sesion.setAttribute("primerpaso.idEmpresa", usuario.idEmpresa());
        }
        return sesion;
    }

    private static SolicitudVacante datos() {
        return new SolicitudVacante("Desarrollador Java", "Desarrollo de sistemas web.", 4L,
                List.of(8L, 9L), "remota", "Lima", "PE", VENCIMIENTO, VERSION);
    }

    private static Vacante vacante(String estado) {
        return new Vacante(7L, "Desarrollador Java", "Desarrollo de sistemas web.", 4L, "Ingeniería de Sistemas",
                List.of(new Habilidad(8L, "Java"), new Habilidad(9L, "PostgreSQL")), "remota", "Lima", "PE",
                estado, VERSION, "borrador".equals(estado) ? null : VERSION, VENCIMIENTO, VERSION, false);
    }
}
