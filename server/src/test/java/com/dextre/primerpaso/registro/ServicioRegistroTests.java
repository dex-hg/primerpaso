package com.dextre.primerpaso.registro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.Year;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.dextre.primerpaso.registro.DatosRegistro.SolicitudEmpresa;
import com.dextre.primerpaso.registro.DatosRegistro.SolicitudPostulante;
import com.dextre.primerpaso.registro.RepositorioRegistro.MiembroEmpresa;
import com.dextre.primerpaso.registro.RepositorioRegistro.PerfilEmpresa;
import com.dextre.primerpaso.registro.RepositorioRegistro.PerfilPostulante;

class ServicioRegistroTests {

    private RepositorioRegistro repositorio;
    private CodificadorContrasena codificador;
    private ServicioRegistro servicio;

    @BeforeEach
    void prepararServicio() {
        repositorio = mock(RepositorioRegistro.class);
        codificador = mock(CodificadorContrasena.class);
        servicio = new ServicioRegistro(repositorio, codificador);
        when(codificador.codificar(anyString())).thenReturn("hash-seguro-de-prueba");
        when(repositorio.crearUsuario(anyString(), anyString())).thenReturn(10L);
        when(repositorio.resolverCarrera(anyString())).thenReturn(20L);
        when(repositorio.crearEmpresa(any())).thenReturn(30L);
        when(repositorio.resolverHabilidad(anyString())).thenReturn(40L);
        when(repositorio.resolverArea(anyString())).thenReturn(50L);
    }

    @Test
    void normalizaPostulanteSinModificarContrasenaYDeduplicaSelecciones() {
        SolicitudPostulante solicitud = new SolicitudPostulante(" Ana ", " López ", " ANA@EJEMPLO.COM ",
                " contraseña de prueba 123 ", true, " Universidad ", " Sistemas ", "estudiante", 4, null,
                List.of("python", "PYTHON", "sql"), List.of("technology", "technology"), true);

        var respuesta = servicio.registrarPostulante(solicitud);

        verify(codificador).codificar(" contraseña de prueba 123 ");
        verify(repositorio).crearUsuario("ana@ejemplo.com", "hash-seguro-de-prueba");
        verify(repositorio).resolverCarrera("Sistemas");
        ArgumentCaptor<PerfilPostulante> perfil = ArgumentCaptor.forClass(PerfilPostulante.class);
        verify(repositorio).crearPostulante(eq(10L), eq(20L), perfil.capture());
        assertEquals("Ana", perfil.getValue().nombres());
        assertEquals("López", perfil.getValue().apellidos());
        assertEquals("Universidad", perfil.getValue().institucion());
        assertEquals(4, perfil.getValue().cicloActual());
        assertEquals(true, perfil.getValue().aceptaComunicaciones());
        verify(repositorio).resolverHabilidad("Python");
        verify(repositorio).resolverHabilidad("SQL");
        verify(repositorio).resolverArea("Tecnología");
        assertEquals(10L, respuesta.idUsuario());
        assertEquals("postulante", respuesta.tipoCuenta());
    }

    @Test
    void aceptaEgresadoConAnioValidoSinCicloNiOpciones() {
        SolicitudPostulante solicitud = solicitudPostulante("egresado", null, Year.now().getValue(), null);

        servicio.registrarPostulante(solicitud);

        ArgumentCaptor<PerfilPostulante> perfil = ArgumentCaptor.forClass(PerfilPostulante.class);
        verify(repositorio).crearPostulante(eq(10L), eq(20L), perfil.capture());
        assertNull(perfil.getValue().cicloActual());
        assertEquals(Year.now().getValue(), perfil.getValue().anioEgreso());
        assertEquals(false, perfil.getValue().aceptaComunicaciones());
        verify(repositorio, never()).resolverHabilidad(anyString());
        verify(repositorio, never()).resolverArea(anyString());
    }

    @Test
    void rechazaDatosAcademicosIncoherentesAntesDeEscribir() {
        List<SolicitudPostulante> solicitudes = List.of(
                solicitudPostulante("estudiante", 0, null, List.of()),
                solicitudPostulante("estudiante", 31, null, List.of()),
                solicitudPostulante("estudiante", 4, 2020, List.of()),
                solicitudPostulante("egresado", 4, 2020, List.of()),
                solicitudPostulante("titulado", null, 1949, List.of()),
                solicitudPostulante("egresado", null, Year.now().getValue() + 1, List.of()),
                solicitudPostulante("otro", 4, null, List.of()));

        for (SolicitudPostulante solicitud : solicitudes) {
            assertThrows(IllegalArgumentException.class, () -> servicio.registrarPostulante(solicitud));
        }
        verifyNoInteractions(repositorio, codificador);
    }

    @Test
    void rechazaOpcionesDesconocidasAntesDeEscribir() {
        var habilidadDesconocida = solicitudPostulante("estudiante", 4, null, List.of("opcion-inexistente"));
        var interesDesconocido = solicitudEmpresa("technology", List.of("opcion-inexistente"));
        var sectorDesconocido = solicitudEmpresa("opcion-inexistente", List.of("technology"));

        assertThrows(IllegalArgumentException.class, () -> servicio.registrarPostulante(habilidadDesconocida));
        assertThrows(IllegalArgumentException.class, () -> servicio.registrarEmpresa(interesDesconocido));
        assertThrows(IllegalArgumentException.class, () -> servicio.registrarEmpresa(sectorDesconocido));
        verifyNoInteractions(repositorio, codificador);
    }

    @Test
    void registraEmpresaConDatosDelRepresentanteYAreaCanonica() {
        var respuesta = servicio.registrarEmpresa(solicitudEmpresa("technology", List.of("human-resources")));

        verify(repositorio).crearUsuario("empresa@ejemplo.com", "hash-seguro-de-prueba");
        ArgumentCaptor<PerfilEmpresa> perfil = ArgumentCaptor.forClass(PerfilEmpresa.class);
        ArgumentCaptor<MiembroEmpresa> miembro = ArgumentCaptor.forClass(MiembroEmpresa.class);
        verify(repositorio).crearEmpresa(perfil.capture());
        verify(repositorio).crearMiembroEmpresa(eq(30L), eq(10L), miembro.capture());
        assertEquals("Empresa de prueba", perfil.getValue().nombreComercial());
        assertEquals("PE", perfil.getValue().codigoPais());
        assertEquals("20123456789", perfil.getValue().identificacionFiscal());
        assertEquals("Tecnología", perfil.getValue().sector());
        assertEquals("Lima", perfil.getValue().ciudad());
        assertNull(perfil.getValue().sitioWeb());
        assertEquals(new MiembroEmpresa("Luis", "Pérez", "+51 999 123 456"), miembro.getValue());
        verify(repositorio).resolverArea("Recursos humanos");
        verify(repositorio).vincularAreaEmpresa(30L, 50L);
        assertEquals("empresa", respuesta.tipoCuenta());
    }

    @Test
    void rechazaSitiosWebMalformadosAntesDeEscribir() {
        List<String> sitiosInvalidos = List.of("https://", "https://-", "https:///ruta",
                "ftp://example.test", "https://example.test/con espacio", "https://example.test/[invalido]");

        for (String sitioWeb : sitiosInvalidos) {
            SolicitudEmpresa solicitud = solicitudEmpresa("technology", List.of(), sitioWeb);
            assertThrows(IllegalArgumentException.class, () -> servicio.registrarEmpresa(solicitud));
        }
        verifyNoInteractions(repositorio, codificador);
    }

    @Test
    void aceptaSitioWebHttpsConServidorYCamino() {
        servicio.registrarEmpresa(solicitudEmpresa("technology", List.of(), "https://example.test/camino"));

        ArgumentCaptor<PerfilEmpresa> perfil = ArgumentCaptor.forClass(PerfilEmpresa.class);
        verify(repositorio).crearEmpresa(perfil.capture());
        assertEquals("https://example.test/camino", perfil.getValue().sitioWeb());
    }

    @Test
    void fallaMembresiaYRevierteLaTransaccionCompleta() {
        PlatformTransactionManager administrador = mock(PlatformTransactionManager.class);
        TransactionStatus estado = mock(TransactionStatus.class);
        when(administrador.getTransaction(any(TransactionDefinition.class))).thenReturn(estado);
        ProxyFactory fabrica = new ProxyFactory(servicio);
        fabrica.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(administrador);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        fabrica.addAdvice(interceptor);
        ServicioRegistro servicioTransaccional = (ServicioRegistro) fabrica.getProxy();
        doThrow(new DataIntegrityViolationException("Fallo de prueba"))
                .when(repositorio).crearMiembroEmpresa(eq(30L), eq(10L), any());

        assertThrows(DataIntegrityViolationException.class,
                () -> servicioTransaccional.registrarEmpresa(solicitudEmpresa("technology", List.of())));

        verify(administrador).rollback(estado);
        verify(administrador, never()).commit(any());
        verify(repositorio, never()).resolverArea(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rechazaUnaHabilidadInactivaDelCatalogo() throws Exception {
        JdbcTemplate plantilla = mock(JdbcTemplate.class);
        ResultSet resultado = mock(ResultSet.class);
        when(resultado.getLong(1)).thenReturn(70L);
        when(resultado.getBoolean(2)).thenReturn(false);
        when(plantilla.queryForObject(anyString(), any(RowMapper.class), eq("Java")))
                .thenAnswer(invocacion -> {
                    RowMapper<?> mapeador = invocacion.getArgument(1);
                    return mapeador.mapRow(resultado, 0);
                });

        var error = assertThrows(IllegalArgumentException.class,
                () -> new RepositorioRegistro(plantilla).resolverHabilidad("Java"));

        assertEquals("Una habilidad seleccionada no está disponible.", error.getMessage());
    }

    private SolicitudPostulante solicitudPostulante(String condicion, Integer ciclo, Integer anio,
            List<String> habilidades) {
        return new SolicitudPostulante("Ana", "López", "ana@ejemplo.com", "Frase de prueba 123", true,
                "Universidad", "Sistemas", condicion, ciclo, anio, habilidades, null, null);
    }

    private SolicitudEmpresa solicitudEmpresa(String sector, List<String> intereses) {
        return solicitudEmpresa(sector, intereses, " ");
    }

    private SolicitudEmpresa solicitudEmpresa(String sector, List<String> intereses, String sitioWeb) {
        return new SolicitudEmpresa(" Luis ", " Pérez ", " EMPRESA@EJEMPLO.COM ", "Frase de prueba 123", true,
                " Empresa de prueba ", " pe ", " 20123456789 ", sector, " Lima ", sitioWeb,
                " +51 999 123 456 ", intereses);
    }
}
