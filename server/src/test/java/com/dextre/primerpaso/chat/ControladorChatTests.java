package com.dextre.primerpaso.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.dextre.primerpaso.chat.DatosChat.Contexto;
import com.dextre.primerpaso.chat.DatosChat.Detalle;
import com.dextre.primerpaso.chat.DatosChat.Mensaje;
import com.dextre.primerpaso.chat.DatosChat.PaginaConversaciones;
import com.dextre.primerpaso.chat.DatosChat.ResultadoEnvio;
import com.dextre.primerpaso.chat.DatosChat.SolicitudMensaje;
import com.dextre.primerpaso.panel.FiltroAccesoPanel;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.sesion.ServicioSesion;

@SpringBootTest(properties = {"spring.config.import=", "spring.datasource.url=jdbc:postgresql://localhost:1/prueba",
        "spring.datasource.username=prueba", "spring.datasource.password=prueba", "primerpaso.bd.comprobar-al-iniciar=false"})
class ControladorChatTests {

    private static final String TOKEN = "a".repeat(43);
    private static final UUID ENVIO = UUID.fromString("4ac08510-d431-43e7-aa35-c5e12cc79ed1");
    private static final UsuarioSesion POSTULANTE = new UsuarioSesion(7, "Ana", "Pérez", "ana@example.test", "postulante", null, null, null);
    private static final UsuarioSesion EMPRESA = new UsuarioSesion(8, "Luis", "Díaz", "luis@example.test", "empresa", 9L, "Empresa", "reclutador");
    @Autowired private WebApplicationContext contexto;
    @Autowired private FiltroAccesoPanel filtro;
    @MockitoBean private ServicioSesion sesiones;
    @MockitoBean private ServicioChat chat;
    private MockMvc cliente;

    @BeforeEach
    void preparar() {
        cliente = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtro).build();
        when(sesiones.consultarUsuario(7, "postulante", null)).thenReturn(POSTULANTE);
        when(sesiones.consultarUsuario(8, "empresa", 9L)).thenReturn(EMPRESA);
    }

    @Test
    void protegeLasApiYLasPaginasSinCrearSesionesAnonimas() throws Exception {
        for (String ruta : List.of("/api/chat", "/api/chat/postulaciones/15", "/api/chat/postulaciones/15/mensajes")) {
            var respuesta = cliente.perform(get(ruta)).andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", "no-store")).andReturn();
            assertThat(respuesta.getRequest().getSession(false)).isNull();
        }
        cliente.perform(get("/panel/postulante/mensajes")).andExpect(redirectedUrl("/iniciar-sesion"));
        verifyNoInteractions(chat);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/panel/empresa/mensajes", "/panel/empresa/postulaciones/15/chat", "/api/chat/no-autorizada",
            "/%70anel/empresa/mensajes", "/panel//empresa/mensajes", "/panel/empresa;otra=1/mensajes"})
    void rechazaRutasCruzadasODesconocidasAntesDelControlador(String ruta) throws Exception {
        cliente.perform(get(java.net.URI.create(ruta)).session(sesion(POSTULANTE))).andExpect(status().isForbidden());
        verifyNoInteractions(chat);
    }

    @Test
    void exigeCsrfYRechazaLaSuplantacionDeEmisorEnElContrato() throws Exception {
        cliente.perform(post("/api/chat/postulaciones/15/mensajes").session(sesion(EMPRESA))
                .contentType(MediaType.APPLICATION_JSON).content(datos())).andExpect(status().isForbidden());
        cliente.perform(post("/api/chat/postulaciones/15/mensajes").session(sesion(EMPRESA)).header("X-CSRF-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(datos().replace("}", ",\"idEmisor\":7}")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(chat);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{malformado}", "{\"idEnvio\":\"incorrecto\",\"contenido\":\"Hola\"}",
            "{\"idEnvio\":\"4ac08510-d431-43e7-aa35-c5e12cc79ed1\",\"contenido\":\"  \"}"})
    void validaElContratoDelMensaje(String datos) throws Exception {
        cliente.perform(post("/api/chat/postulaciones/15/mensajes").session(sesion(EMPRESA)).header("X-CSRF-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isBadRequest());
        verifyNoInteractions(chat);
    }

    @Test
    void distingueElPrimerEnvioDelReintentoYUsaLaIdentidadRevalidada() throws Exception {
        var solicitud = new SolicitudMensaje(ENVIO, "Hola");
        when(chat.enviar(EMPRESA, 15, solicitud)).thenReturn(new ResultadoEnvio(mensaje(), true), new ResultadoEnvio(mensaje(), false));
        for (int estado : List.of(201, 200)) {
            cliente.perform(post("/api/chat/postulaciones/15/mensajes").session(sesion(EMPRESA)).header("X-CSRF-Token", TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(datos())).andExpect(status().is(estado))
                    .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.mensaje.idEmisor").value(8));
        }
    }

    @Test
    void elHistorialRecibeCursoresYNoExponeErroresDeBaseDeDatos() throws Exception {
        when(chat.consultar(POSTULANTE, 15, null, 1L)).thenReturn(detalle());
        cliente.perform(get("/api/chat/postulaciones/15").session(sesion(POSTULANTE)).param("despuesDe", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contexto.idPostulacion").value(15));
        when(chat.consultar(POSTULANTE, 15, null, null)).thenThrow(new DataAccessResourceFailureException("SELECT secreto"));
        String respuesta = cliente.perform(get("/api/chat/postulaciones/15").session(sesion(POSTULANTE)))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain("SELECT", "secreto");
        cliente.perform(get("/api/chat/postulaciones/15").session(sesion(POSTULANTE)).param("antesDe", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renderizaTextoSeguroHoraDeLimaYSoloLecturaConPrefijoDeContexto() throws Exception {
        when(chat.consultar(POSTULANTE, 15, null, null)).thenReturn(detalle());
        String html = cliente.perform(get("/primerpaso/panel/postulante/postulaciones/15/chat").contextPath("/primerpaso")
                .session(sesion(POSTULANTE))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("&lt;script&gt;", "02/11/2026 13:00 (Lima)", "/primerpaso/js/chat.js",
                "data-puede-enviar=\"false\"", "disabled", "href=\"/primerpaso/panel/postulante/mensajes\"")
                .doesNotContain("<script>alert", "th:text", "th:href");
    }

    @Test
    void laBandejaVaciaOfreceVolverALasPostulaciones() throws Exception {
        when(chat.listar(EMPRESA, 0)).thenReturn(new PaginaConversaciones(List.of(), 0, 0, 0));
        String html = cliente.perform(get("/panel/empresa/mensajes").session(sesion(EMPRESA))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Todavía no hay conversaciones", "Selecciona una postulación", "href=\"/panel/empresa/postulaciones\"");
    }

    private Detalle detalle() {
        return new Detalle(new Contexto(15, 12, "Practicante <web>", "Empresa", "Ana Pérez", "descartada", true),
                List.of(mensaje()), true, false, "Conversación en modo de lectura.", false, false);
    }

    private Mensaje mensaje() {
        return new Mensaje(2, 8, ENVIO, "Luis Díaz", "empresa", "<script>alert('prueba')</script>\nHola", Instant.parse("2026-11-02T18:00:00Z"));
    }

    private String datos() {
        return "{\"idEnvio\":\"" + ENVIO + "\",\"contenido\":\"Hola\"}";
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
