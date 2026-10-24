package com.dextre.primerpaso.cuenta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.dextre.primerpaso.cuenta.DatosCuenta.Cuenta;
import com.dextre.primerpaso.cuenta.DatosCuenta.DatosPersonales;
import com.dextre.primerpaso.cuenta.DatosCuenta.Formacion;
import com.dextre.primerpaso.panel.FiltroAccesoPanel;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.sesion.ServicioSesion;

@SpringBootTest(properties = {"spring.config.import=", "spring.datasource.url=jdbc:postgresql://localhost:1/prueba",
        "spring.datasource.username=prueba", "spring.datasource.password=prueba", "primerpaso.bd.comprobar-al-iniciar=false"})
class ControladorCuentaTests {

    private static final String TOKEN = "a".repeat(43);
    private static final UsuarioSesion USUARIO = new UsuarioSesion(7, "Ana", "Pérez", "ana@example.test",
            "postulante", null, null, null);
    private static final String DATOS = """
            {"nombres":"Ana","apellidos":"Pérez","correo":"ana@example.test","telefono":null,
             "ciudad":"Lima","institucion":"Universidad","idCarrera":3,"condicionAcademica":"estudiante",
             "cicloActual":6,"anioEgreso":null,"nombreComercial":null,"sector":null,"sitioWeb":null,"version":"v1"}
            """;
    @Autowired private WebApplicationContext contexto;
    @Autowired private FiltroAccesoPanel filtro;
    @MockitoBean private ServicioSesion sesiones;
    @MockitoBean private ServicioCuenta cuentas;
    private MockMvc cliente;

    @BeforeEach
    void preparar() {
        cliente = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtro).build();
    }

    @Test
    void protegeConsultaYEdicionAnonimasConRespuestaJson() throws Exception {
        cliente.perform(get("/api/cuenta")).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.mensaje").isString());
        cliente.perform(put("/api/cuenta").contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cuentas);
    }

    @Test
    void consultaYActualizaSoloAlUsuarioRevalidadoDeLaSesion() throws Exception {
        when(sesiones.consultarUsuario(7, "postulante", null)).thenReturn(USUARIO);
        when(cuentas.consultar(USUARIO)).thenReturn(cuenta());
        when(cuentas.actualizar(eq(USUARIO), any())).thenReturn(cuenta());
        cliente.perform(get("/api/cuenta").session(sesion()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos.nombres").value("Ana"))
                .andExpect(header().string("Cache-Control", "no-store"));
        cliente.perform(put("/api/cuenta").session(sesion()).header("X-CSRF-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value("v1"));
    }

    @Test
    void rechazaCsrfAusenteCamposVaciosYAsignacionDeOtraIdentidad() throws Exception {
        when(sesiones.consultarUsuario(7, "postulante", null)).thenReturn(USUARIO);
        cliente.perform(put("/api/cuenta").session(sesion()).contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isForbidden());
        for (String datos : List.of(DATOS.replace("\"nombres\":\"Ana\"", "\"nombres\":\" \""),
                DATOS.replace("\"correo\":\"ana@example.test\"", "\"correo\":\"invalido\""),
                DATOS.replace("\"version\":\"v1\"", "\"version\":\"v1\",\"idUsuario\":99"))) {
            cliente.perform(put("/api/cuenta").session(sesion()).header("X-CSRF-Token", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON).content(datos))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(cuentas);
    }

    @Test
    void unCorreoDuplicadoRespondeSinExponerElErrorDePostgres() throws Exception {
        when(sesiones.consultarUsuario(7, "postulante", null)).thenReturn(USUARIO);
        when(cuentas.actualizar(eq(USUARIO), any())).thenThrow(new DuplicateKeyException("users password_hash secreto"));
        String contenido = cliente.perform(put("/api/cuenta").session(sesion()).header("X-CSRF-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(contenido).doesNotContain("password_hash", "secreto");
    }

    @Test
    void laCuentaEmpresarialConservaVacantesEnLaCabeceraYNoOfreceVolverAlLanding() throws Exception {
        var empresa = new UsuarioSesion(7, "Ana", "Pérez", "ana@example.test", "empresa", 9L, "Empresa", "administrador");
        when(sesiones.consultarUsuario(7, "empresa", 9L)).thenReturn(empresa);
        MockHttpSession sesion = sesion();
        sesion.setAttribute("primerpaso.tipoCuenta", "empresa");
        sesion.setAttribute("primerpaso.idEmpresa", 9L);
        String html = cliente.perform(get("/cuenta").session(sesion)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("href=\"/panel/empresa/vacantes\"", "formulario-cuenta", "Guardar cambios")
                .doesNotContain(">Inicio</a>", "Sesión activa.", "href=\"/\"");
    }

    private Cuenta cuenta() {
        return new Cuenta(USUARIO, new DatosPersonales("Ana", "Pérez", "ana@example.test", null),
                new Formacion("Lima", "Universidad", 3, "Sistemas", "estudiante", 6, null), null, "v1", List.of());
    }

    private MockHttpSession sesion() {
        var sesion = new MockHttpSession();
        sesion.setAttribute("primerpaso.idUsuario", 7L);
        sesion.setAttribute("primerpaso.tipoCuenta", "postulante");
        sesion.setAttribute("primerpaso.tokenCsrf", TOKEN);
        return sesion;
    }
}
