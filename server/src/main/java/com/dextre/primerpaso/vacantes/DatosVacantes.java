package com.dextre.primerpaso.vacantes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class DatosVacantes {

    private DatosVacantes() {
    }

    public record SolicitudVacante(
            @NotBlank @Size(max = 180) String titulo,
            @NotBlank @Size(max = 12000) String descripcion,
            @NotNull @Positive Long idCarrera,
            @NotNull @Size(min = 1, max = 30) List<@NotNull @Positive Long> habilidades,
            @NotBlank @Pattern(regexp = "presencial|remota|hibrida") String modalidad,
            @NotBlank @Size(max = 120) String ciudad,
            @NotBlank @Pattern(regexp = "[A-Z]{2}") String codigoPais,
            @NotNull Instant fechaVencimiento,
            Instant version) {

        public SolicitudVacante {
            titulo = normalizarTexto(titulo);
            descripcion = normalizarTexto(descripcion);
            ciudad = normalizarTexto(ciudad);
            modalidad = modalidad == null ? null : modalidad.strip().toLowerCase(Locale.ROOT);
            codigoPais = codigoPais == null ? null : codigoPais.strip().toUpperCase(Locale.ROOT);
            habilidades = habilidades == null ? null
                    : Collections.unmodifiableList(new ArrayList<>(habilidades));
        }
    }

    public record SolicitudEstadoVacante(@NotNull Instant version) {
    }

    public record Vacante(long id, String titulo, String descripcion, long idCarrera,
            String nombreCarrera, List<Habilidad> habilidades, String modalidad, String ciudad,
            String codigoPais, String estado, Instant fechaCreacion, Instant fechaPublicacion,
            Instant fechaVencimiento, Instant version, boolean vencida) {

        public Vacante {
            habilidades = habilidades == null ? List.of() : List.copyOf(habilidades);
        }

        public boolean editable() {
            return "borrador".equals(estado) || ("publicada".equals(estado) && !vencida);
        }
    }

    public record Habilidad(long id, String nombre) {
    }

    public record OpcionCatalogo(long id, String nombre) {
    }

    public record Catalogos(List<OpcionCatalogo> carreras, List<OpcionCatalogo> habilidades) {
        public Catalogos {
            carreras = List.copyOf(carreras);
            habilidades = List.copyOf(habilidades);
        }
    }

    public record PaginaVacantes(List<Vacante> vacantes, int pagina, int totalPaginas, long total) {
        public PaginaVacantes {
            vacantes = List.copyOf(vacantes);
        }
    }

    private static String normalizarTexto(String texto) {
        return texto == null ? null : texto.strip();
    }
}
