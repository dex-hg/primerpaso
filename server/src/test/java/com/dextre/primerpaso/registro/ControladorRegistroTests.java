package com.dextre.primerpaso.registro;

import java.sql.SQLException;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.dextre.primerpaso.registro.DatosRegistro.RespuestaRegistro;
import com.dextre.primerpaso.registro.DatosRegistro.SolicitudEmpresa;
import com.dextre.primerpaso.registro.DatosRegistro.SolicitudPostulante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControladorRegistroTests {

    private static final String RUTA_POSTULANTES = "/api/registro/postulantes";
    private static final String RUTA_EMPRESAS = "/api/registro/empresas";
    private static final String POSTULANTE_VALIDO = """
            {"nombres":"Ana","apellidos":"Pérez","correo":"ana@example.test",
            "contrasena":" Clave de prueba á 123 ","aceptaTerminos":true,
            "institucion":"Universidad de prueba","carrera":"Sistemas",
            "condicionAcademica":"estudiante","cicloActual":7,"anioEgreso":null,
            "habilidades":["java"],"intereses":["technology"],
            "aceptaComunicaciones":false}
            """;
    private static final String EMPRESA_VALIDA = """
            {"nombres":"Luis","apellidos":"Pérez","correo":"luis@example.test",
            "contrasena":" Clave de prueba á 123 ","aceptaTerminos":true,
            "nombreComercial":"Empresa de prueba","codigoPais":"PE",
            "identificacionFiscal":"20100070970","sector":"technology",
            "ciudad":"Lima","sitioWeb":"https://example.test",
            "telefono":"987654321","intereses":["technology"]}
            """;

    private ServicioRegistro servicio;
    private MockMvc cliente;

    @BeforeEach
    void prepararControlador() {
        servicio = mock(ServicioRegistro.class);
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        cliente = MockMvcBuilders.standaloneSetup(new ControladorRegistro(servicio,
                new DisponibilidadRegistro(true, true)))
                .setControllerAdvice(new ManejadorErroresRegistro())
                .setValidator(validador).build();
    }

    @Test
    void registraPostulanteConContratoPublicoYRespuestaSinCache() throws Exception {
        when(servicio.registrarPostulante(any(SolicitudPostulante.class)))
                .thenReturn(new RespuestaRegistro(21, "postulante", "Cuenta creada."));

        cliente.perform(post(RUTA_POSTULANTES).contentType(MediaType.APPLICATION_JSON)
                .content(POSTULANTE_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.idUsuario").value(21))
                .andExpect(jsonPath("$.tipoCuenta").value("postulante"))
                .andExpect(jsonPath("$.mensaje").value("Cuenta creada."))
                .andExpect(jsonPath("$.contrasena").doesNotExist());
        verify(servicio).registrarPostulante(any(SolicitudPostulante.class));
    }

    @Test
    void registraEmpresaConContratoPublicoYRespuestaSinCache() throws Exception {
        when(servicio.registrarEmpresa(any(SolicitudEmpresa.class)))
                .thenReturn(new RespuestaRegistro(22, "empresa", "Cuenta creada."));

        cliente.perform(post(RUTA_EMPRESAS).contentType(MediaType.APPLICATION_JSON)
                .content(EMPRESA_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.idUsuario").value(22))
                .andExpect(jsonPath("$.tipoCuenta").value("empresa"))
                .andExpect(jsonPath("$.mensaje").value("Cuenta creada."))
                .andExpect(jsonPath("$.contrasena").doesNotExist());
        verify(servicio).registrarEmpresa(any(SolicitudEmpresa.class));
    }

    @ParameterizedTest
    @MethodSource("solicitudesInvalidas")
    void rechazaCamposInvalidosAntesDeInvocarServicio(String ruta, String contenido,
            String campo) throws Exception {
        cliente.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.errores." + campo).isString());
        verifyNoInteractions(servicio);
    }

    static Stream<Arguments> solicitudesInvalidas() {
        return Stream.of(new String[] {RUTA_POSTULANTES, POSTULANTE_VALIDO},
                new String[] {RUTA_EMPRESAS, EMPRESA_VALIDA}).flatMap(datos -> Stream.of(
                        Arguments.of(datos[0], datos[1].replace("@example.test", ""), "correo"),
                        Arguments.of(datos[0], datos[1].replace(" Clave de prueba á 123 ", "x".repeat(14)), "contrasena"),
                        Arguments.of(datos[0], datos[1].replace(" Clave de prueba á 123 ", "x".repeat(129)), "contrasena"),
                        Arguments.of(datos[0], datos[1].replace(" Clave de prueba á 123 ", " ".repeat(15)), "contrasena"),
                        Arguments.of(datos[0], datos[1].replace("\"aceptaTerminos\":true",
                                "\"aceptaTerminos\":false"), "aceptaTerminos"),
                        Arguments.of(datos[0], datos[1].replace("\"aceptaTerminos\":true",
                                "\"aceptaTerminos\":null"), "aceptaTerminos")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1", "123456789012345678901"})
    void rechazaTelefonosInvalidosAntesDeInvocarServicio(String telefono) throws Exception {
        cliente.perform(post(RUTA_EMPRESAS).contentType(MediaType.APPLICATION_JSON)
                .content(EMPRESA_VALIDA.replace("987654321", telefono)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.telefono").isString());
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {RUTA_POSTULANTES, RUTA_EMPRESAS})
    void rechazaCuerpoNuloSinInvocarServicio(String ruta) throws Exception {
        cliente.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {RUTA_POSTULANTES, RUTA_EMPRESAS})
    void rechazaJsonMalformadoSinInvocarServicio(String ruta) throws Exception {
        cliente.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content("{\"correo\":"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.mensaje").value("El contenido del registro no es válido."));
        verifyNoInteractions(servicio);
    }

    @Test
    void devuelveConflictoParaDuplicadoSinExponerDetallesSql() throws Exception {
        SQLException causa = new SQLException("constraint correo_sql clave_secreta", "23505");
        when(servicio.registrarPostulante(any(SolicitudPostulante.class)))
                .thenThrow(new DuplicateKeyException("INSERT INTO usuarios clave_secreta", causa));

        String respuesta = cliente.perform(post(RUTA_POSTULANTES)
                .contentType(MediaType.APPLICATION_JSON).content(POSTULANTE_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.mensaje").value(
                        "Ya existe una cuenta con ese correo o una empresa con esa identificación fiscal."))
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain("INSERT", "23505", "correo_sql", "clave_secreta");
    }

    @Test
    void devuelveIndisponibilidadSinExponerCredenciales() throws Exception {
        when(servicio.registrarEmpresa(any(SolicitudEmpresa.class)))
                .thenThrow(new DataAccessResourceFailureException(
                        "jdbc:postgresql://localhost/PrimerPaso password=clave_secreta"));

        String respuesta = cliente.perform(post(RUTA_EMPRESAS)
                .contentType(MediaType.APPLICATION_JSON).content(EMPRESA_VALIDA))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.mensaje").value(
                        "No se pudo acceder a la base de datos. Inténtalo nuevamente más tarde."))
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain("jdbc:", "password", "clave_secreta", "localhost");
    }
}
