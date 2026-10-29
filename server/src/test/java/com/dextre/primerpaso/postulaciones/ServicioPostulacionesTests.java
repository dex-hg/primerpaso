package com.dextre.primerpaso.postulaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.postulaciones.DatosPostulaciones.Detalle;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.Perfil;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.Resumen;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.SolicitudEstado;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.SolicitudPostulacion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.RepositorioVacantes;

class ServicioPostulacionesTests {

    private static final Instant VERSION = Instant.parse("2026-10-28T15:00:00.123456Z");
    private static final UsuarioSesion POSTULANTE = new UsuarioSesion(7, "Ana", "Pérez", "ana@example.test",
            "postulante", null, null, null);
    private static final UsuarioSesion EMPRESA = new UsuarioSesion(8, "Luis", "Díaz", "luis@example.test",
            "empresa", 9L, "Empresa", "reclutador");
    private RepositorioPostulaciones repositorio;
    private RepositorioVacantes vacantes;
    private ServicioPostulaciones servicio;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioPostulaciones.class);
        vacantes = mock(RepositorioVacantes.class);
        servicio = new ServicioPostulaciones(repositorio, vacantes);
        when(repositorio.postulanteActivo(7, true)).thenReturn(true);
        when(repositorio.postulanteActivo(7, false)).thenReturn(true);
        when(vacantes.membresiaActiva(8, 9, true)).thenReturn(true);
        when(vacantes.membresiaActiva(8, 9, false)).thenReturn(true);
        when(repositorio.bloquearVacanteDisponible(12)).thenReturn(true);
        when(repositorio.crear(7, 12, "Hola\nMe interesa esta vacante.")).thenReturn(Optional.of(15L));
        when(repositorio.consultar(POSTULANTE, 15, false)).thenReturn(Optional.of(detalle("enviada")));
        when(repositorio.consultar(EMPRESA, 15, true)).thenReturn(Optional.of(detalle("enviada")));
        when(repositorio.consultar(EMPRESA, 15, false)).thenReturn(Optional.of(detalle("en_revision")));
    }

    @Test
    void guardaLaCartaCompletaConLaIdentidadDeSesionYConservaLosParrafos() {
        Detalle resultado = servicio.postular(POSTULANTE, new SolicitudPostulacion(12L, "  Hola\nMe interesa esta vacante.  "));
        assertThat(resultado.postulacion().id()).isEqualTo(15);
        verify(repositorio).crear(7, 12, "Hola\nMe interesa esta vacante.");
        verify(repositorio).postulanteActivo(7, true);
    }

    @ParameterizedTest
    @MethodSource("solicitudesInvalidas")
    void rechazaCartasVaciasExcesivasOConControlesYVacantesInvalidas(SolicitudPostulacion datos) {
        exigirError(() -> servicio.postular(POSTULANTE, datos), 400);
        verify(repositorio, never()).crear(anyLong(), anyLong(), any());
    }

    static Stream<SolicitudPostulacion> solicitudesInvalidas() {
        return Stream.of(null, new SolicitudPostulacion(null, "Hola"), new SolicitudPostulacion(0L, "Hola"),
                new SolicitudPostulacion(12L, null), new SolicitudPostulacion(12L, " \n\t "),
                new SolicitudPostulacion(12L, "a".repeat(12001)), new SolicitudPostulacion(12L, "Hola\u0000equipo"));
    }

    @Test
    void impideDuplicadosInclusoCuandoOtroEnvioGanaLaCarrera() {
        when(repositorio.buscarPropia(7, 12)).thenReturn(Optional.of(15L));
        exigirError(() -> servicio.postular(POSTULANTE, solicitud()), 409);
        verify(repositorio, never()).crear(anyLong(), anyLong(), any());
        when(repositorio.buscarPropia(7, 12)).thenReturn(Optional.empty());
        when(repositorio.crear(7, 12, solicitud().cartaPresentacion())).thenReturn(Optional.empty());
        exigirError(() -> servicio.postular(POSTULANTE, solicitud()), 409);
    }

    @Test
    void rechazaLaVacanteNoVigenteYNoPermitePostularDesdeUnaEmpresa() {
        when(repositorio.bloquearVacanteDisponible(12)).thenReturn(false);
        exigirError(() -> servicio.postular(POSTULANTE, solicitud()), 404);
        exigirError(() -> servicio.postular(EMPRESA, solicitud()), 403);
        verify(repositorio, never()).crear(anyLong(), anyLong(), any());
    }

    @Test
    void revalidaLaCuentaYLaMembresiaAntesDeConsultarDatos() {
        when(repositorio.postulanteActivo(7, false)).thenReturn(false);
        when(vacantes.membresiaActiva(8, 9, false)).thenReturn(false);
        exigirError(() -> servicio.consultar(POSTULANTE, 15), 403);
        exigirError(() -> servicio.consultar(EMPRESA, 15), 403);
        exigirError(() -> servicio.consultar(null, 15), 403);
        verify(repositorio, never()).consultar(any(), anyLong(), anyBoolean());
    }

    @Test
    void limitaDetalleYFiltroDeVacanteALaEmpresaDeSesion() {
        when(repositorio.consultar(EMPRESA, 15, false)).thenReturn(Optional.empty());
        exigirError(() -> servicio.consultar(EMPRESA, 15), 404);
        exigirError(() -> servicio.listar(EMPRESA, 0, "todos", 12L), 404);
        verify(repositorio).tituloVacanteEmpresa(9, 12);
        verify(repositorio, never()).contar(any(), any(), any());
    }

    @Test
    void paginaElHistorialPropioYConservaElFiltroDeEstadoYVacante() {
        when(repositorio.contar(POSTULANTE, "seleccionada", 12L)).thenReturn(13L);
        when(repositorio.listar(POSTULANTE, "seleccionada", 12L, 1, 12)).thenReturn(List.of(detalle("seleccionada").postulacion()));
        var pagina = servicio.listar(POSTULANTE, Integer.MAX_VALUE, "seleccionada", 12L);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
        assertThat(pagina.total()).isEqualTo(13);
        assertThat(pagina.postulaciones()).hasSize(1);
    }

    @Test
    void listaLaVacantePropiaInclusoSiSuConvocatoriaYaTermino() {
        when(repositorio.tituloVacanteEmpresa(9, 12)).thenReturn(Optional.of("Convocatoria cerrada"));
        var pagina = servicio.listar(EMPRESA, 999, "todos", 12L);
        assertThat(pagina.tituloVacante()).isEqualTo("Convocatoria cerrada");
        assertThat(pagina.pagina()).isZero();
    }

    @Test
    void rechazaFiltrosEIdentificadoresInvalidos() {
        exigirError(() -> servicio.listar(POSTULANTE, -1, "todos", null), 400);
        exigirError(() -> servicio.listar(EMPRESA, 0, "inventado", null), 400);
        exigirError(() -> servicio.listar(EMPRESA, 0, null, null), 400);
        exigirError(() -> servicio.listar(EMPRESA, 0, "todos", -1L), 400);
        exigirError(() -> servicio.consultar(POSTULANTE, 0), 400);
        exigirError(() -> servicio.buscarPropia(POSTULANTE, -1), 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_revision", "seleccionada", "descartada"})
    void permiteAlReclutadorGuardarEstadosConLaVersionExacta(String estado) {
        servicio.cambiarEstado(EMPRESA, 15, new SolicitudEstado(estado, VERSION));
        verify(repositorio).actualizarEstado(9, 15, estado, VERSION);
        verify(vacantes).membresiaActiva(8, 9, true);
    }

    @Test
    void evitaSobrescribirUnCambioConcurrenteYPreservaPostulacionesRetiradas() {
        exigirError(() -> servicio.cambiarEstado(EMPRESA, 15,
                new SolicitudEstado("en_revision", VERSION.minusSeconds(1))), 409);
        when(repositorio.consultar(EMPRESA, 15, true)).thenReturn(Optional.of(detalle("retirada")));
        exigirError(() -> servicio.cambiarEstado(EMPRESA, 15, new SolicitudEstado("seleccionada", VERSION)), 409);
        verify(repositorio, never()).actualizarEstado(anyLong(), anyLong(), any(), any());
    }

    @Test
    void elPostulanteNoPuedeCambiarSuEstadoYNadiePuedeAsignarEstadosInventados() {
        exigirError(() -> servicio.cambiarEstado(POSTULANTE, 15, new SolicitudEstado("seleccionada", VERSION)), 403);
        exigirError(() -> servicio.cambiarEstado(EMPRESA, 15, new SolicitudEstado("enviada", VERSION)), 400);
        exigirError(() -> servicio.cambiarEstado(EMPRESA, 15, new SolicitudEstado("descartada", null)), 400);
        exigirError(() -> servicio.cambiarEstado(EMPRESA, 15, null), 400);
        verify(repositorio, never()).actualizarEstado(anyLong(), anyLong(), any(), any());
    }

    @Test
    void guardarElMismoEstadoEsIdempotenteYNoCambiaLaFecha() {
        when(repositorio.consultar(EMPRESA, 15, true)).thenReturn(Optional.of(detalle("en_revision")));
        var resultado = servicio.cambiarEstado(EMPRESA, 15, new SolicitudEstado("en_revision", VERSION));
        assertThat(resultado.postulacion().version()).isEqualTo(VERSION);
        verify(repositorio, never()).actualizarEstado(anyLong(), anyLong(), any(), any());
    }

    private SolicitudPostulacion solicitud() {
        return new SolicitudPostulacion(12L, "Hola\nMe interesa esta vacante.");
    }

    private Detalle detalle(String estado) {
        return new Detalle(new Resumen(15, 12, "Practicante", "Empresa", "Ana", "Pérez", estado, VERSION, VERSION),
                "Hola\nMe interesa esta vacante.", new Perfil("ana@example.test", null, null, "Institución", "Sistemas",
                        "estudiante", 6, null, List.of("Java")));
    }

    private void exigirError(Runnable accion, int estado) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class,
                excepcion -> assertThat(excepcion.getStatusCode().value()).isEqualTo(estado));
    }
}
