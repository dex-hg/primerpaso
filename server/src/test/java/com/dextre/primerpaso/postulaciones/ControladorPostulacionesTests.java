package com.dextre.primerpaso.postulaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.oportunidades.DatosOportunidades.Oportunidad;
import com.dextre.primerpaso.oportunidades.ServicioOportunidades;
import com.dextre.primerpaso.panel.FiltroAccesoPanel;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.Detalle;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.PaginaPostulaciones;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.Perfil;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.Resumen;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.SolicitudEstado;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.SolicitudPostulacion;
import com.dextre.primerpaso.sesion.AccesoNoAutorizadoException;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.sesion.ServicioSesion;

@SpringBootTest(properties = {"spring.config.import=", "spring.datasource.url=jdbc:postgresql://localhost:1/prueba",
        "spring.datasource.username=prueba", "spring.datasource.password=prueba", "primerpaso.bd.comprobar-al-iniciar=false"})
class ControladorPostulacionesTests {

    private static final String TOKEN = "a".repeat(43);
    private static final Instant VERSION = Instant.parse("2026-10-28T15:00:00.123456Z");
    private static final UsuarioSesion POSTULANTE = new UsuarioSesion(7, "Ana", "Pérez", "ana@example.test",
            "postulante", null, null, null);
    private static final UsuarioSesion EMPRESA = new UsuarioSesion(8, "Luis", "Díaz", "luis@example.test",
            "empresa", 9L, "Empresa", "reclutador");
    private static final String DATOS = "{\"idVacante\":12,\"cartaPresentacion\":\" Hola\\nMe interesa. \"}";
    @Autowired private WebApplicationContext contexto;
    @Autowired private FiltroAccesoPanel filtro;
    @MockitoBean private ServicioSesion sesiones;
    @MockitoBean private ServicioPostulaciones postulaciones;
    @MockitoBean private ServicioOportunidades oportunidades;
    private MockMvc cliente;

    @BeforeEach
    void preparar() {
        cliente = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtro).build();
        when(sesiones.consultarUsuario(7, "postulante", null)).thenReturn(POSTULANTE);
        when(sesiones.consultarUsuario(8, "empresa", 9L)).thenReturn(EMPRESA);
    }

    @Test
    void exigeSesionEnTodasLasRutasSinCrearSesionesAnonimas() throws Exception {
        for (String ruta : List.of("/api/postulaciones", "/api/postulaciones/15", "/api/postulaciones/15/estado")) {
            var resultado = cliente.perform(get(ruta)).andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", "no-store")).andReturn();
            assertThat(resultado.getRequest().getSession(false)).isNull();
        }
        for (String ruta : List.of("/panel/postulante/postulaciones", "/panel/empresa/postulaciones/15",
                "/panel/postulante/vacantes/12/postular")) {
            cliente.perform(get(ruta)).andExpect(status().isFound()).andExpect(redirectedUrl("/iniciar-sesion"));
        }
        verifyNoInteractions(postulaciones, oportunidades);
    }

    @Test
    void bloqueaPanelesCruzadosYVariantesDeRutaAntesDelControlador() throws Exception {
        for (String ruta : List.of("/panel/empresa/postulaciones", "/panel/empresa/postulaciones/15",
                "/%70anel/empresa/postulaciones", "/panel//empresa/postulaciones", "/panel/empresa;prueba=1/postulaciones")) {
            cliente.perform(get(java.net.URI.create(ruta)).session(sesion(POSTULANTE))).andExpect(status().isForbidden());
        }
        cliente.perform(get("/panel/postulante/vacantes/12/postular").session(sesion(EMPRESA)))
                .andExpect(status().isForbidden());
        cliente.perform(get("/panel/postulante/postulaciones").session(sesion(EMPRESA)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(postulaciones, oportunidades);
    }

    @Test
    void exigeCsrfAntesDeGuardarCartaOCambiarEstado() throws Exception {
        cliente.perform(post("/api/postulaciones").session(sesion(POSTULANTE))
                .contentType(MediaType.APPLICATION_JSON).content(DATOS)).andExpect(status().isForbidden());
        cliente.perform(put("/api/postulaciones/15/estado").session(sesion(EMPRESA))
                .contentType(MediaType.APPLICATION_JSON).content(estadoJson()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(postulaciones);
    }

    @Test
    void creaConIdentidadRevalidadaSinAceptarUnCandidatoDesdeElCliente() throws Exception {
        when(postulaciones.postular(POSTULANTE, new SolicitudPostulacion(12L, "Hola\nMe interesa.")))
                .thenReturn(detalle());
        cliente.perform(post("/api/postulaciones").session(sesion(POSTULANTE)).header("X-CSRF-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(DATOS)).andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/postulaciones/15"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.postulacion.id").value(15));
        cliente.perform(post("/api/postulaciones").session(sesion(POSTULANTE)).header("X-CSRF-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(DATOS.replace("}", ",\"idUsuario\":8}")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"idVacante\":-1,\"cartaPresentacion\":\"Hola\"}",
            "{\"idVacante\":12,\"cartaPresentacion\":\"  \"}", "{malformado}"})
    void rechazaElContratoInvalido(String datos) throws Exception {
        cliente.perform(post("/api/postulaciones").session(sesion(POSTULANTE)).header("X-CSRF-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isBadRequest());
        verifyNoInteractions(postulaciones);
    }

    @Test
    void actualizaElEstadoConUnaVersionQueConservaMicrosegundos() throws Exception {
        when(postulaciones.cambiarEstado(EMPRESA, 15, new SolicitudEstado("en_revision", VERSION))).thenReturn(detalle());
        cliente.perform(put("/api/postulaciones/15/estado").session(sesion(EMPRESA)).header("X-CSRF-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(estadoJson())).andExpect(status().isOk())
                .andExpect(jsonPath("$.postulacion.version").value(VERSION.toString()));
        verify(postulaciones).cambiarEstado(EMPRESA, 15, new SolicitudEstado("en_revision", VERSION));
    }

    @Test
    void preservaLosCodigosDeErrorYOcultaLaInformacionInterna() throws Exception {
        for (HttpStatus estado : List.of(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT)) {
            doThrow(new ResponseStatusException(estado, "Mensaje de negocio")).when(postulaciones).consultar(EMPRESA, 15);
            cliente.perform(get("/api/postulaciones/15").session(sesion(EMPRESA)))
                    .andExpect(status().is(estado.value())).andExpect(jsonPath("$.mensaje").value("Mensaje de negocio"));
        }
        doThrow(new DataAccessResourceFailureException("SELECT secreto")).when(postulaciones).consultar(EMPRESA, 15);
        String json = cliente.perform(get("/api/postulaciones/15").session(sesion(EMPRESA)))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("SELECT", "secreto");
        when(sesiones.consultarUsuario(8, "empresa", 9L)).thenThrow(new AccesoNoAutorizadoException("Revocada"));
        cliente.perform(get("/api/postulaciones").session(sesion(EMPRESA))).andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void renderizaHistorialYCartaComoTextoYNoModificaElEstadoAlLeer() throws Exception {
        when(postulaciones.listar(EMPRESA, 0, "enviada", 12L)).thenReturn(
                new PaginaPostulaciones(List.of(detalle().postulacion()), 0, 2, 13, "Practicante <web>"));
        String lista = cliente.perform(get("/panel/empresa/postulaciones").session(sesion(EMPRESA))
                .param("estado", "enviada").param("idVacante", "12")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(lista).contains("Practicante &lt;web&gt;", "idVacante=12", "estado=enviada", "pagina=1",
                "28/10/2026 10:00", "13 postulaciones");
        when(postulaciones.consultar(EMPRESA, 15)).thenReturn(detalle());
        String html = cliente.perform(get("/panel/empresa/postulaciones/15").session(sesion(EMPRESA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("&lt;script&gt;alert", "Datos actuales del perfil", "Guardar estado", VERSION.toString())
                .doesNotContain("<script>alert");
        verify(postulaciones, never()).cambiarEstado(any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void renderizaVistaDePostulanteSinControlesEmpresarialesYEstadoVacio() throws Exception {
        when(postulaciones.consultar(POSTULANTE, 15)).thenReturn(detalle());
        String html = cliente.perform(get("/panel/postulante/postulaciones/15").session(sesion(POSTULANTE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Carta de presentación", "Mis postulaciones").doesNotContain("Guardar estado");
        when(postulaciones.listar(POSTULANTE, 0, "todos", null)).thenReturn(new PaginaPostulaciones(List.of(), 0, 0, 0, null));
        html = cliente.perform(get("/panel/postulante/postulaciones").session(sesion(POSTULANTE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("No hay postulaciones para mostrar", "href=\"/panel/postulante/vacantes\"");
    }

    @Test
    void ofreceElFormularioYRedirigeALaSolicitudSiYaPostulo() throws Exception {
        when(oportunidades.consultar(12)).thenReturn(new Oportunidad(12, "Practicante", "Detalle", "Empresa", "Sistemas",
                "remota", "Lima", "PE", VERSION, null, List.of()));
        String html = cliente.perform(get("/panel/postulante/vacantes/12/postular").session(sesion(POSTULANTE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("maxlength=\"12000\"", "Enviar postulación", "data-id-vacante=\"12\"");
        when(postulaciones.buscarPropia(POSTULANTE, 12)).thenReturn(Optional.of(15L));
        cliente.perform(get("/panel/postulante/vacantes/12/postular").session(sesion(POSTULANTE)))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/panel/postulante/postulaciones/15"));
    }

    @Test
    void respetaElContextoYLosErroresDeFiltrosEnLasVistas() throws Exception {
        when(postulaciones.listar(POSTULANTE, 0, "todos", null)).thenReturn(new PaginaPostulaciones(List.of(), 0, 0, 0, null));
        String html = cliente.perform(get("/primerpaso/panel/postulante/postulaciones").contextPath("/primerpaso")
                .session(sesion(POSTULANTE))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("/primerpaso/js/postulaciones.js", "href=\"/primerpaso/panel/postulante/vacantes\"");
        cliente.perform(get("/panel/postulante/postulaciones").session(sesion(POSTULANTE)).param("pagina", "abc"))
                .andExpect(status().isBadRequest());
    }

    private Detalle detalle() {
        return new Detalle(new Resumen(15, 12, "Practicante <web>", "Empresa", "Ana", "Pérez", "enviada", VERSION, VERSION),
                "<script>alert('prueba')</script>\nSegunda línea", new Perfil("ana@example.test", null, null,
                "Institución", "Sistemas", "estudiante", 6, null, List.of("Java")));
    }

    private String estadoJson() {
        return "{\"estado\":\"en_revision\",\"version\":\"" + VERSION + "\"}";
    }

    private MockHttpSession sesion(UsuarioSesion usuario) {
        var sesion = new MockHttpSession();
        sesion.setAttribute("primerpaso.idUsuario", usuario.idUsuario());
        sesion.setAttribute("primerpaso.tipoCuenta", usuario.tipoCuenta());
        sesion.setAttribute("primerpaso.tokenCsrf", TOKEN);
        if (usuario.idEmpresa() != null) sesion.setAttribute("primerpaso.idEmpresa", usuario.idEmpresa());
        return sesion;
    }
}
