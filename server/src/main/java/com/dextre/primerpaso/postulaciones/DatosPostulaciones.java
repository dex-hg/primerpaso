package com.dextre.primerpaso.postulaciones;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class DatosPostulaciones {

    private DatosPostulaciones() {
    }

    public record SolicitudPostulacion(@NotNull @Positive Long idVacante,
            @NotBlank @Size(max = 12000) String cartaPresentacion) {
        public SolicitudPostulacion {
            cartaPresentacion = cartaPresentacion == null ? null : cartaPresentacion.strip();
        }

        @Override
        public String toString() {
            return "SolicitudPostulacion[datos privados]";
        }
    }

    public record SolicitudEstado(@NotBlank @Pattern(regexp = "en_revision|seleccionada|descartada") String estado,
            @NotNull Instant version) {
    }

    public record Resumen(long id, long idVacante, String tituloVacante, String nombreEmpresa,
            String nombres, String apellidos, String estado, Instant fechaEnvio, Instant version) {
        public String nombreEstado() {
            return switch (estado) {
                case "enviada" -> "Enviada";
                case "en_revision" -> "En revisión";
                case "seleccionada" -> "Seleccionada";
                case "descartada" -> "Descartada";
                case "retirada" -> "Retirada";
                default -> "Por confirmar";
            };
        }
    }

    public record Perfil(String correo, String telefono, String ciudad, String institucion,
            String carrera, String condicionAcademica, Integer cicloActual, Integer anioEgreso,
            List<String> habilidades) {
        public Perfil {
            habilidades = List.copyOf(habilidades);
        }
    }

    public record Detalle(Resumen postulacion, String cartaPresentacion, Perfil perfil) {
    }

    public record PaginaPostulaciones(List<Resumen> postulaciones, int pagina, int totalPaginas,
            long total, String tituloVacante) {
        public PaginaPostulaciones {
            postulaciones = List.copyOf(postulaciones);
        }
    }
}
