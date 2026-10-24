package com.dextre.primerpaso.oportunidades;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.dextre.primerpaso.vacantes.DatosVacantes.Habilidad;

public final class DatosOportunidades {

    private DatosOportunidades() {
    }

    public record Filtros(String busqueda, String modalidad, Long idCarrera, String ciudad, Long idHabilidad) {
        public Filtros {
            busqueda = normalizar(busqueda);
            modalidad = normalizar(modalidad).toLowerCase(Locale.ROOT);
            ciudad = normalizar(ciudad);
        }
    }

    public record Oportunidad(long id, String titulo, String descripcion, String nombreEmpresa,
            String nombreCarrera, String modalidad, String ciudad, String codigoPais,
            Instant fechaPublicacion, Instant fechaVencimiento, List<Habilidad> habilidades) {
        public Oportunidad {
            habilidades = List.copyOf(habilidades);
        }
    }

    public record PaginaOportunidades(List<Oportunidad> oportunidades, int pagina, int totalPaginas, long total) {
        public PaginaOportunidades {
            oportunidades = List.copyOf(oportunidades);
        }
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.strip();
    }
}
