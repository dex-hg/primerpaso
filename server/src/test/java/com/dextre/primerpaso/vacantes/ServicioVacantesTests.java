package com.dextre.primerpaso.vacantes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.DatosVacantes.Catalogos;
import com.dextre.primerpaso.vacantes.DatosVacantes.Habilidad;
import com.dextre.primerpaso.vacantes.DatosVacantes.OpcionCatalogo;
import com.dextre.primerpaso.vacantes.DatosVacantes.SolicitudEstadoVacante;
import com.dextre.primerpaso.vacantes.DatosVacantes.SolicitudVacante;
import com.dextre.primerpaso.vacantes.DatosVacantes.Vacante;

class ServicioVacantesTests {

    private static final Instant AHORA = Instant.parse("2026-10-05T15:00:00Z");
    private static final Instant VERSION = AHORA.minusSeconds(60);
    private static final UsuarioSesion EMPRESA = new UsuarioSesion(10, "Ana", "Díaz", "ana@example.test",
            "empresa", 20L, "Empresa de prueba", "administrador");
    private RepositorioVacantes repositorio;
    private ServicioVacantes servicio;

    @BeforeEach
    void prepararServicio() {
        repositorio = mock(RepositorioVacantes.class);
        servicio = new ServicioVacantes(repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));
        when(repositorio.membresiaActiva(10, 20, true)).thenReturn(true);
        when(repositorio.membresiaActiva(10, 20, false)).thenReturn(true);
        when(repositorio.bloquearCarreraActiva(30)).thenReturn(true);
        when(repositorio.bloquearHabilidadesActivas(List.of(40L, 41L))).thenReturn(2);
        when(repositorio.crear(eq(20L), eq(10L), any())).thenReturn(50L);
        when(repositorio.consultar(eq(20L), eq(50L), anyBoolean(), any()))
                .thenReturn(Optional.of(vacante("borrador", AHORA.plusSeconds(3600))));
    }

    @Test
    void creaBorradorConEmpresaYAutorDeLaSesionTrasBloquearMembresiaYCatalogos() {
        SolicitudVacante datos = solicitud(null);

        Vacante resultado = servicio.crear(EMPRESA, datos);

        var orden = inOrder(repositorio);
        orden.verify(repositorio).membresiaActiva(10, 20, true);
        orden.verify(repositorio).bloquearCarreraActiva(30);
        orden.verify(repositorio).bloquearHabilidadesActivas(List.of(40L, 41L));
        orden.verify(repositorio).crear(20, 10, datos);
        orden.verify(repositorio).sustituirHabilidades(20, 50, List.of(40L, 41L));
        assertEquals("borrador", resultado.estado());
        assertEquals("Practicante de sistemas", datos.titulo());
        assertEquals("PE", datos.codigoPais());
        assertEquals("hibrida", datos.modalidad());
        assertTrue(resultado.editable());
    }

    @Test
    void permiteQueUnReclutadorGestioneVacantesDeSuEmpresa() {
        UsuarioSesion reclutador = new UsuarioSesion(10, "Ana", "Díaz", "ana@example.test",
                "empresa", 20L, "Empresa de prueba", "reclutador");

        servicio.editar(reclutador, 50, solicitud(VERSION));

        verify(repositorio).editar(eq(20L), eq(50L), any());
    }

    @Test
    void impideTodasLasOperacionesDeUnPostulanteAntesDeConsultarDatos() {
        UsuarioSesion postulante = new UsuarioSesion(10, "Ana", "Díaz", "ana@example.test",
                "postulante", null, null, null);
        List<Runnable> operaciones = List.of(() -> servicio.crear(postulante, solicitud(null)),
                () -> servicio.editar(postulante, 50, solicitud(VERSION)),
                () -> servicio.publicar(postulante, 50, new SolicitudEstadoVacante(VERSION)),
                () -> servicio.cerrar(postulante, 50, new SolicitudEstadoVacante(VERSION)),
                () -> servicio.consultar(postulante, 50), () -> servicio.listar(postulante, 0, "todos"),
                () -> servicio.catalogos(postulante));

        operaciones.forEach(operacion -> assertEquals(403,
                assertThrows(ReglaVacanteException.class, operacion::run).estado()));
        verifyNoInteractions(repositorio);
    }

    @Test
    void bloqueaMembresiaDesactivadaAunqueLaSesionSigaAbierta() {
        when(repositorio.membresiaActiva(10, 20, true)).thenReturn(false);

        assertEquals(403, assertThrows(ReglaVacanteException.class,
                () -> servicio.editar(EMPRESA, 50, solicitud(VERSION))).estado());

        verify(repositorio, never()).consultar(anyLong(), anyLong(), anyBoolean(), any());
        verificarSinEscrituras();
    }

    @Test
    void ocultaUnaVacanteAjenaYNuncaConsultaFueraDeLaEmpresaDeSesion() {
        when(repositorio.consultar(20, 90, true, AHORA)).thenReturn(Optional.empty());

        assertEquals(404, assertThrows(ReglaVacanteException.class,
                () -> servicio.editar(EMPRESA, 90, solicitud(VERSION))).estado());

        verify(repositorio).consultar(20, 90, true, AHORA);
        verificarSinEscrituras();
    }

    @Test
    void impideSobrescribirUnaVersionAnteriorInclusoPorUnMicrosegundo() {
        assertEquals(409, assertThrows(ReglaVacanteException.class,
                () -> servicio.editar(EMPRESA, 50, solicitud(VERSION.minusNanos(1000)))).estado());
        assertEquals(400, assertThrows(ReglaVacanteException.class,
                () -> servicio.editar(EMPRESA, 50, solicitud(null))).estado());
        verificarSinEscrituras();
    }

    @Test
    void editaPublicadaVigenteTrasBloquearLaVacanteYCambiaSusHabilidadesEnLaMismaOperacion() {
        when(repositorio.consultar(20, 50, true, AHORA))
                .thenReturn(Optional.of(vacante("publicada", AHORA.plusSeconds(3600))));

        servicio.editar(EMPRESA, 50, solicitud(VERSION));

        var orden = inOrder(repositorio);
        orden.verify(repositorio).membresiaActiva(10, 20, true);
        orden.verify(repositorio).consultar(20, 50, true, AHORA);
        orden.verify(repositorio).bloquearCarreraActiva(30);
        orden.verify(repositorio).bloquearHabilidadesActivas(List.of(40L, 41L));
        orden.verify(repositorio).editar(eq(20L), eq(50L), any());
        orden.verify(repositorio).sustituirHabilidades(20, 50, List.of(40L, 41L));
    }

    @Test
    void impideEditarVacantesCerradasOPublicadasVencidas() {
        for (Vacante vacante : List.of(vacante("cerrada", AHORA.plusSeconds(3600)),
                vacante("publicada", AHORA), vacante("publicada", AHORA.minusSeconds(1)))) {
            when(repositorio.consultar(20, 50, true, AHORA)).thenReturn(Optional.of(vacante));
            assertEquals(409, assertThrows(ReglaVacanteException.class,
                    () -> servicio.editar(EMPRESA, 50, solicitud(VERSION))).estado());
            assertFalse(vacante.editable());
        }
        verificarSinEscrituras();
    }

    @Test
    void revalidaElVencimientoSiLaEsperaPorBloqueosCruzaSuFecha() {
        Clock reloj = mock(Clock.class);
        when(reloj.instant()).thenReturn(AHORA, AHORA.plusSeconds(2));
        ServicioVacantes servicioConEspera = new ServicioVacantes(repositorio, reloj);
        when(repositorio.consultar(20, 50, true, AHORA))
                .thenReturn(Optional.of(vacante("publicada", AHORA.plusSeconds(1))));

        assertEquals(409, assertThrows(ReglaVacanteException.class,
                () -> servicioConEspera.editar(EMPRESA, 50, solicitud(VERSION))).estado());

        verificarSinEscrituras();
    }

    @Test
    void revalidaLaFechaDelBorradorTrasEsperarPorSusCatalogos() {
        Clock reloj = mock(Clock.class);
        when(reloj.instant()).thenReturn(AHORA, AHORA.plusSeconds(3601));
        ServicioVacantes servicioConEspera = new ServicioVacantes(repositorio, reloj);

        assertEquals(400, assertThrows(ReglaVacanteException.class,
                () -> servicioConEspera.crear(EMPRESA, solicitud(null))).estado());

        verificarSinEscrituras();
    }

    @Test
    void publicaSoloBorradoresCompletosConCatalogosActivosYVersionActual() {
        servicio.publicar(EMPRESA, 50, new SolicitudEstadoVacante(VERSION));

        var orden = inOrder(repositorio);
        orden.verify(repositorio).membresiaActiva(10, 20, true);
        orden.verify(repositorio).consultar(20, 50, true, AHORA);
        orden.verify(repositorio).bloquearCarreraActiva(30);
        orden.verify(repositorio).bloquearHabilidadesActivas(List.of(40L, 41L));
        orden.verify(repositorio).publicar(20, 50);
    }

    @Test
    void noPermiteRepublicarNiReabrirUnaVacante() {
        for (String estado : List.of("publicada", "cerrada")) {
            when(repositorio.consultar(20, 50, true, AHORA))
                    .thenReturn(Optional.of(vacante(estado, AHORA.plusSeconds(3600))));
            assertEquals(409, assertThrows(ReglaVacanteException.class,
                    () -> servicio.publicar(EMPRESA, 50, new SolicitudEstadoVacante(VERSION))).estado());
        }
        verificarSinEscrituras();
    }

    @Test
    void exigeCompletarElVencimientoDeUnBorradorAnteriorAntesDePublicarlo() {
        for (Instant vencimiento : Arrays.asList(null, AHORA, AHORA.minusSeconds(1))) {
            when(repositorio.consultar(20, 50, true, AHORA))
                    .thenReturn(Optional.of(vacante("borrador", vencimiento)));
            assertEquals(400, assertThrows(ReglaVacanteException.class,
                    () -> servicio.publicar(EMPRESA, 50, new SolicitudEstadoVacante(VERSION))).estado());
        }
        verificarSinEscrituras();
    }

    @Test
    void impideGuardarOSeleccionarUnCatalogoQueYaNoEstaActivo() {
        when(repositorio.bloquearCarreraActiva(30)).thenReturn(false);
        assertEquals(400, assertThrows(ReglaVacanteException.class,
                () -> servicio.crear(EMPRESA, solicitud(null))).estado());
        when(repositorio.bloquearCarreraActiva(30)).thenReturn(true);
        when(repositorio.bloquearHabilidadesActivas(List.of(40L, 41L))).thenReturn(1);
        assertEquals(400, assertThrows(ReglaVacanteException.class,
                () -> servicio.publicar(EMPRESA, 50, new SolicitudEstadoVacante(VERSION))).estado());
        verificarSinEscrituras();
    }

    @Test
    void cierraUnaPublicadaVencidaSinExigirCatalogosNiAlterarSuContenido() {
        when(repositorio.consultar(20, 50, true, AHORA))
                .thenReturn(Optional.of(vacante("publicada", AHORA.minusSeconds(1))));

        servicio.cerrar(EMPRESA, 50, new SolicitudEstadoVacante(VERSION));

        verify(repositorio).cerrar(20, 50);
        verify(repositorio, never()).bloquearCarreraActiva(anyLong());
        verify(repositorio, never()).sustituirHabilidades(anyLong(), anyLong(), any());
    }

    @Test
    void impideCerrarBorradorOCerradaYExigeVersionAlPublicarOCerrar() {
        for (String estado : List.of("borrador", "cerrada")) {
            when(repositorio.consultar(20, 50, true, AHORA))
                    .thenReturn(Optional.of(vacante(estado, AHORA.plusSeconds(3600))));
            assertEquals(409, assertThrows(ReglaVacanteException.class,
                    () -> servicio.cerrar(EMPRESA, 50, new SolicitudEstadoVacante(VERSION))).estado());
        }
        assertEquals(400, assertThrows(ReglaVacanteException.class,
                () -> servicio.cerrar(EMPRESA, 50, null)).estado());
        assertEquals(409, assertThrows(ReglaVacanteException.class,
                () -> servicio.publicar(EMPRESA, 50, new SolicitudEstadoVacante(AHORA))).estado());
        verificarSinEscrituras();
    }

    @Test
    void rechazaCamposInvalidosYHabilidadesDuplicadasAntesDePersistir() {
        List<SolicitudVacante> solicitudes = new ArrayList<>();
        solicitudes.add(new SolicitudVacante(" ", "Descripción", 30L, List.of(40L), "remota", "Lima", "PE",
                AHORA.plusSeconds(60), null));
        solicitudes.add(new SolicitudVacante("Título", "x".repeat(12001), 30L, List.of(40L), "remota", "Lima",
                "PE", AHORA.plusSeconds(60), null));
        solicitudes.add(new SolicitudVacante("Título", "Descripción", 0L, List.of(40L), "remota", "Lima",
                "PE", AHORA.plusSeconds(60), null));
        for (List<Long> habilidades : Arrays.asList(List.<Long>of(), List.of(40L, 40L), List.of(-1L),
                Arrays.asList(40L, null), LongStream.rangeClosed(1, 31).boxed().toList())) {
            solicitudes.add(new SolicitudVacante("Título", "Descripción", 30L, habilidades, "remota", "Lima",
                    "PE", AHORA.plusSeconds(60), null));
        }
        solicitudes.add(new SolicitudVacante("Título", "Descripción", 30L, List.of(40L), "otra", "Lima", "PE",
                AHORA.plusSeconds(60), null));
        solicitudes.add(new SolicitudVacante("Título", "Descripción", 30L, List.of(40L), "remota", " ", "PE",
                AHORA.plusSeconds(60), null));
        solicitudes.add(new SolicitudVacante("Título", "Descripción", 30L, List.of(40L), "remota", "Lima", "PER",
                AHORA.plusSeconds(60), null));
        solicitudes.add(new SolicitudVacante("Título", "Descripción", 30L, List.of(40L), "remota", "Lima", "PE",
                AHORA, null));

        solicitudes.forEach(datos -> assertEquals(400, assertThrows(ReglaVacanteException.class,
                () -> servicio.crear(EMPRESA, datos)).estado()));
        assertEquals(400, assertThrows(ReglaVacanteException.class,
                () -> servicio.crear(EMPRESA, null)).estado());
        verifyNoInteractions(repositorio);
    }

    @Test
    void listaSoloLaEmpresaActualYPaginaHastaElUltimoResultadoDisponible() {
        when(repositorio.contar(20, "vencida", AHORA)).thenReturn(25L);
        when(repositorio.listar(20, 2, 12, "vencida", AHORA)).thenReturn(List.of());

        var pagina = servicio.listar(EMPRESA, Integer.MAX_VALUE, " VENCIDA ");

        assertEquals(2, pagina.pagina());
        assertEquals(3, pagina.totalPaginas());
        assertEquals(25, pagina.total());
        verify(repositorio).membresiaActiva(10, 20, false);
        verify(repositorio).listar(20, 2, 12, "vencida", AHORA);
    }

    @Test
    void admiteListadoVacioYPaginaCeroSinDivisionesInvalidas() {
        when(repositorio.listar(20, 0, 12, "todos", AHORA)).thenReturn(List.of());
        var pagina = servicio.listar(EMPRESA, 0, null);
        assertEquals(0, pagina.total());
        assertEquals(0, pagina.totalPaginas());
        assertEquals(0, pagina.pagina());
    }

    @Test
    void noPermiteFiltrosNiIdentificadoresInvalidos() {
        assertEquals(400, assertThrows(ReglaVacanteException.class,
                () -> servicio.listar(EMPRESA, -1, "todos")).estado());
        assertEquals(400, assertThrows(ReglaVacanteException.class,
                () -> servicio.listar(EMPRESA, 0, "eliminada")).estado());
        assertEquals(400, assertThrows(ReglaVacanteException.class,
                () -> servicio.consultar(EMPRESA, 0)).estado());
        verifyNoInteractions(repositorio);
    }

    @Test
    void consultaCatalogosSinCrearlosYSinBloqueosDeEscritura() {
        Catalogos catalogos = new Catalogos(List.of(new OpcionCatalogo(30, "Sistemas")),
                List.of(new OpcionCatalogo(40, "Java")));
        when(repositorio.catalogos()).thenReturn(catalogos);

        assertEquals(catalogos, servicio.catalogos(EMPRESA));
        verify(repositorio).membresiaActiva(10, 20, false);
        verificarSinEscrituras();
    }

    @Test
    void revierteLaCreacionCompletaCuandoFallaLaRelacionDeHabilidades() {
        PlatformTransactionManager administrador = mock(PlatformTransactionManager.class);
        TransactionStatus estado = mock(TransactionStatus.class);
        when(administrador.getTransaction(any(TransactionDefinition.class))).thenReturn(estado);
        ProxyFactory fabrica = new ProxyFactory(servicio);
        fabrica.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(administrador);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        fabrica.addAdvice(interceptor);
        ServicioVacantes servicioTransaccional = (ServicioVacantes) fabrica.getProxy();
        doThrow(new DataIntegrityViolationException("Fallo controlado"))
                .when(repositorio).sustituirHabilidades(20, 50, List.of(40L, 41L));

        assertThrows(DataIntegrityViolationException.class,
                () -> servicioTransaccional.crear(EMPRESA, solicitud(null)));

        verify(administrador).rollback(estado);
        verify(administrador, never()).commit(any());
        verify(repositorio, never()).consultar(anyLong(), anyLong(), anyBoolean(), any());
    }

    private SolicitudVacante solicitud(Instant version) {
        return new SolicitudVacante(" Practicante de sistemas ", " Descripción de la vacante ", 30L,
                List.of(40L, 41L), " HIBRIDA ", " Lima ", " pe ", AHORA.plusSeconds(3600), version);
    }

    private Vacante vacante(String estado, Instant vencimiento) {
        return new Vacante(50, "Practicante de sistemas", "Descripción de la vacante", 30, "Sistemas",
                List.of(new Habilidad(40, "Java"), new Habilidad(41, "SQL")), "hibrida", "Lima", "PE", estado,
                AHORA.minusSeconds(7200), "borrador".equals(estado) ? null : AHORA.minusSeconds(3600),
                vencimiento, VERSION, "publicada".equals(estado) && vencimiento != null
                        && !vencimiento.isAfter(AHORA));
    }

    private void verificarSinEscrituras() {
        verify(repositorio, never()).crear(anyLong(), anyLong(), any());
        verify(repositorio, never()).editar(anyLong(), anyLong(), any());
        verify(repositorio, never()).sustituirHabilidades(anyLong(), anyLong(), any());
        verify(repositorio, never()).publicar(anyLong(), anyLong());
        verify(repositorio, never()).cerrar(anyLong(), anyLong());
    }
}
