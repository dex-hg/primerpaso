package com.dextre.primerpaso.oportunidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.oportunidades.DatosOportunidades.Filtros;
import com.dextre.primerpaso.vacantes.RepositorioVacantes;

class ServicioOportunidadesTests {

    private static final Instant AHORA = Instant.parse("2026-10-24T15:00:00Z");
    private RepositorioOportunidades repositorio;
    private ServicioOportunidades servicio;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioOportunidades.class);
        servicio = new ServicioOportunidades(repositorio, mock(RepositorioVacantes.class), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void conservaTodosLosFiltrosYLimitaLasPaginasFueraDelListado() {
        var filtros = new Filtros(" Java ", " REMOTA ", 3L, " Lima ", 4L);
        when(repositorio.contar(filtros, AHORA)).thenReturn(20L);
        when(repositorio.listar(filtros, 2, 9, AHORA)).thenReturn(List.of());
        var pagina = servicio.listar(filtros, Integer.MAX_VALUE);
        assertEquals(2, pagina.pagina());
        assertEquals(3, pagina.totalPaginas());
        assertEquals("Java", filtros.busqueda());
        assertEquals("remota", filtros.modalidad());
        verify(repositorio).listar(filtros, 2, 9, AHORA);
    }

    @Test
    void muestraUnaPaginaVaciaCuandoNoHayResultados() {
        var filtros = new Filtros(null, null, null, null, null);
        when(repositorio.listar(filtros, 0, 9, AHORA)).thenReturn(List.of());
        assertEquals(0, servicio.listar(filtros, 0).total());
    }

    @Test
    void rechazaModalidadLongitudesEIdentificadoresInvalidos() {
        for (Filtros filtros : List.of(new Filtros("", "otro", null, "", null),
                new Filtros("x".repeat(181), "", null, "", null),
                new Filtros("", "", 0L, "", null), new Filtros("", "", null, "", -1L))) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> servicio.listar(filtros, 0)).getStatusCode().value());
        }
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> servicio.listar(new Filtros("", "", null, "", null), -1)).getStatusCode().value());
        verifyNoInteractions(repositorio);
    }

    @Test
    void unaVacanteCerradaVencidaOAjenaALaOfertaDisponibleNoEntregaDetalle() {
        when(repositorio.consultar(7, AHORA)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> servicio.consultar(7)).getStatusCode().value());
    }
}
