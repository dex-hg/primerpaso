package com.dextre.primerpaso.chat;

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
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.chat.DatosChat.Contexto;
import com.dextre.primerpaso.chat.DatosChat.Mensaje;
import com.dextre.primerpaso.chat.DatosChat.SolicitudMensaje;
import com.dextre.primerpaso.postulaciones.RepositorioPostulaciones;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.RepositorioVacantes;

class ServicioChatTests {

    private static final UUID ENVIO = UUID.fromString("4ac08510-d431-43e7-aa35-c5e12cc79ed1");
    private static final UsuarioSesion POSTULANTE = new UsuarioSesion(7, "Ana", "Pérez", "ana@example.test", "postulante", null, null, null);
    private static final UsuarioSesion EMPRESA = new UsuarioSesion(8, "Luis", "Díaz", "luis@example.test", "empresa", 9L, "Empresa", "reclutador");
    private RepositorioChat repositorio;
    private RepositorioPostulaciones postulaciones;
    private RepositorioVacantes vacantes;
    private ServicioChat servicio;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioChat.class);
        postulaciones = mock(RepositorioPostulaciones.class);
        vacantes = mock(RepositorioVacantes.class);
        servicio = new ServicioChat(repositorio, postulaciones, vacantes);
        when(postulaciones.postulanteActivo(anyLong(), anyBoolean())).thenReturn(true);
        when(vacantes.membresiaActiva(anyLong(), anyLong(), anyBoolean())).thenReturn(true);
        when(repositorio.consultarContexto(any(), anyLong(), anyBoolean())).thenReturn(Optional.of(contexto("seleccionada", true)));
        when(repositorio.estaIniciada(15)).thenReturn(true);
        when(repositorio.guardar(15, EMPRESA, ENVIO, "Hola\n¿Tienes disponibilidad?")).thenReturn(mensaje(1));
    }

    @Test
    void elReclutadorIniciaConLaIdentidadDeSesionYConservaLosParrafos() {
        when(repositorio.estaIniciada(15)).thenReturn(false);
        var resultado = servicio.enviar(EMPRESA, 15, new SolicitudMensaje(ENVIO, "  Hola\r\n¿Tienes disponibilidad?  "));
        assertThat(resultado.creado()).isTrue();
        verify(repositorio).consultarContexto(EMPRESA, 15, true);
        verify(vacantes).membresiaActiva(8, 9, true);
        verify(repositorio).guardar(15, EMPRESA, ENVIO, "Hola\n¿Tienes disponibilidad?");
    }

    @Test
    void elPostulantePuedeLeerPeroNoIniciarUnaConversacion() {
        when(repositorio.estaIniciada(15)).thenReturn(false);
        assertThat(servicio.consultar(POSTULANTE, 15, null, null).puedeEnviar()).isFalse();
        exigirError(() -> servicio.enviar(POSTULANTE, 15, solicitud()), 409);
        verify(repositorio, never()).guardar(anyLong(), any(), any(), any());
    }

    @Test
    void elPostulanteRespondeDespuesDelPrimerMensajeEmpresarial() {
        when(repositorio.guardar(15, POSTULANTE, ENVIO, solicitud().contenido())).thenReturn(mensaje(2));
        assertThat(servicio.consultar(POSTULANTE, 15, null, null).puedeEnviar()).isTrue();
        assertThat(servicio.enviar(POSTULANTE, 15, solicitud()).creado()).isTrue();
        verify(postulaciones).postulanteActivo(7, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"enviada", "en_revision", "descartada", "retirada"})
    void conservaLecturaSinPermitirEscriturasSiLaPostulacionNoEstaSeleccionada(String estado) {
        when(repositorio.consultarContexto(any(), anyLong(), anyBoolean())).thenReturn(Optional.of(contexto(estado, true)));
        var detalle = servicio.consultar(EMPRESA, 15, null, null);
        assertThat(detalle.iniciada()).isTrue();
        assertThat(detalle.puedeEnviar()).isFalse();
        assertThat(detalle.aviso()).contains("modo de lectura");
        exigirError(() -> servicio.enviar(EMPRESA, 15, solicitud()), 409);
    }

    @Test
    void unaCuentaDestinatariaInactivaImpideEnviarPeroConservaElHistorial() {
        when(repositorio.consultarContexto(any(), anyLong(), anyBoolean())).thenReturn(Optional.of(contexto("seleccionada", false)));
        assertThat(servicio.consultar(EMPRESA, 15, null, null).puedeEnviar()).isFalse();
        exigirError(() -> servicio.enviar(EMPRESA, 15, solicitud()), 409);
    }

    @Test
    void revalidaMembresiaYCandidatoYRechazaCuentasCorruptas() {
        when(vacantes.membresiaActiva(8, 9, true)).thenReturn(false);
        exigirError(() -> servicio.enviar(EMPRESA, 15, solicitud()), 403);
        when(postulaciones.postulanteActivo(7, true)).thenReturn(false);
        exigirError(() -> servicio.enviar(POSTULANTE, 15, solicitud()), 403);
        exigirError(() -> servicio.consultar(null, 15, null, null), 403);
        exigirError(() -> servicio.consultar(new UsuarioSesion(8, "A", "B", "a@example.test", "empresa", 9L, "E", "invitado"), 15, null, null), 403);
    }

    @Test
    void unRecursoAjenoNoExponeSiTieneConversacion() {
        when(repositorio.consultarContexto(EMPRESA, 15, false)).thenReturn(Optional.empty());
        exigirError(() -> servicio.consultar(EMPRESA, 15, null, null), 404);
        verify(repositorio, never()).estaIniciada(15);
    }

    @Test
    void unReintentoDevuelveElMensajeOriginalInclusoSiElEstadoYaCambio() {
        var guardado = mensaje(1);
        when(repositorio.buscarEnvio(15, 8, ENVIO)).thenReturn(Optional.of(guardado));
        when(repositorio.consultarContexto(EMPRESA, 15, true)).thenReturn(Optional.of(contexto("descartada", true)));
        var resultado = servicio.enviar(EMPRESA, 15, solicitud());
        assertThat(resultado.creado()).isFalse();
        assertThat(resultado.mensaje()).isSameAs(guardado);
        verify(repositorio, never()).guardar(anyLong(), any(), any(), any());
    }

    @Test
    void noReutilizaUnIdentificadorDeEnvioParaUnTextoDistinto() {
        when(repositorio.buscarEnvio(15, 8, ENVIO)).thenReturn(Optional.of(mensaje(1)));
        exigirError(() -> servicio.enviar(EMPRESA, 15, new SolicitudMensaje(ENVIO, "Otro contenido")), 409);
    }

    @Test
    void limitaLaFrecuenciaSinContabilizarLosReintentosYaGuardados() {
        when(repositorio.excedeFrecuencia(15, 8)).thenReturn(true);
        exigirError(() -> servicio.enviar(EMPRESA, 15, solicitud()), 429);
        verify(repositorio, never()).guardar(anyLong(), any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("mensajesInvalidos")
    void validaElContenidoSinDependerDelControlador(SolicitudMensaje datos) {
        exigirError(() -> servicio.enviar(EMPRESA, 15, datos), 400);
        verify(repositorio, never()).guardar(anyLong(), any(), any(), any());
    }

    @Test
    void paginaLosUltimosMensajesEnOrdenCronologicoSinPerderElLimite() {
        when(repositorio.listarMensajes(15, null, null, 51)).thenReturn(IntStream.iterate(51, i -> i - 1).limit(51).mapToObj(this::mensaje).toList());
        var detalle = servicio.consultar(EMPRESA, 15, null, null);
        assertThat(detalle.mensajes()).hasSize(50);
        assertThat(detalle.mensajes().getFirst().id()).isEqualTo(2);
        assertThat(detalle.mensajes().getLast().id()).isEqualTo(51);
        assertThat(detalle.hayAnteriores()).isTrue();
        assertThat(detalle.haySiguientes()).isFalse();
    }

    @Test
    void laLecturaIncrementalEntregaElSiguienteBloqueSinSaltarMensajes() {
        when(repositorio.listarMensajes(15, null, 10L, 51)).thenReturn(IntStream.rangeClosed(11, 61).mapToObj(this::mensaje).toList());
        var detalle = servicio.consultar(EMPRESA, 15, null, 10L);
        assertThat(detalle.mensajes().getFirst().id()).isEqualTo(11);
        assertThat(detalle.mensajes().getLast().id()).isEqualTo(60);
        assertThat(detalle.haySiguientes()).isTrue();
    }

    @Test
    void rechazaCursoresIncompatiblesYPaginasInvalidas() {
        exigirError(() -> servicio.consultar(EMPRESA, 15, 1L, 2L), 400);
        exigirError(() -> servicio.consultar(EMPRESA, 15, 0L, null), 400);
        exigirError(() -> servicio.consultar(EMPRESA, 15, null, -1L), 400);
        exigirError(() -> servicio.consultar(EMPRESA, 0, null, null), 400);
        exigirError(() -> servicio.listar(EMPRESA, -1), 400);
    }

    @Test
    void limitaLaPaginaDeBandejaYConservaLaEmpresaDeSesion() {
        when(repositorio.contarConversaciones(EMPRESA)).thenReturn(13L);
        var pagina = servicio.listar(EMPRESA, Integer.MAX_VALUE);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
        verify(repositorio).listarConversaciones(EMPRESA, 1, 12);
    }

    private Contexto contexto(String estado, boolean activos) {
        return new Contexto(15, 12, "Practicante", "Empresa", "Ana Pérez", estado, activos);
    }

    private SolicitudMensaje solicitud() {
        return new SolicitudMensaje(ENVIO, "Hola\n¿Tienes disponibilidad?");
    }

    private Mensaje mensaje(long id) {
        return new Mensaje(id, 8, ENVIO, "Luis Díaz", "empresa", solicitud().contenido(), Instant.parse("2026-11-02T18:00:00Z"));
    }

    private void exigirError(Runnable accion, int estado) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class,
                excepcion -> assertThat(excepcion.getStatusCode().value()).isEqualTo(estado));
    }

    static Stream<SolicitudMensaje> mensajesInvalidos() {
        return Stream.of(null, new SolicitudMensaje(null, "Hola"), new SolicitudMensaje(ENVIO, null),
                new SolicitudMensaje(ENVIO, " \n\t"), new SolicitudMensaje(ENVIO, "\u00a0"),
                new SolicitudMensaje(ENVIO, "a".repeat(4001)), new SolicitudMensaje(ENVIO, "Hola\u0000equipo"));
    }
}
