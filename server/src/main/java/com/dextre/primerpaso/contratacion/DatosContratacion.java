package com.dextre.primerpaso.contratacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class DatosContratacion {

    private static final Set<String> ESTADOS_ACTIVOS = Set.of("enviada", "en_revision", "seleccionada");

    private DatosContratacion() {
    }

    public record SolicitudOferta(@NotNull UUID idEnvio, @NotNull Instant version,
            @NotBlank @Size(max = 4000) String condiciones, @NotNull LocalDate fechaInicio, @NotNull Instant venceEn) {
        public SolicitudOferta {
            condiciones = condiciones == null ? null : condiciones.strip();
            venceEn = venceEn == null ? null : venceEn.truncatedTo(ChronoUnit.MICROS);
        }

        @Override
        public String toString() {
            return "SolicitudOferta[condiciones privadas]";
        }
    }

    public record SolicitudRespuesta(@NotNull Boolean aceptada) {
    }

    public record SolicitudConfirmacion(@NotNull Instant versionVacante,
            @NotNull @PositiveOrZero Long postulacionesAFinalizar) {
    }

    public record SolicitudCancelacion(@NotNull Instant versionVacante,
            @NotNull @PositiveOrZero Long postulacionesAFinalizar, @NotBlank @Size(max = 500) String motivo) {
        public SolicitudCancelacion {
            motivo = motivo == null ? null : motivo.strip();
        }

        @Override
        public String toString() {
            return "SolicitudCancelacion[motivo privado]";
        }
    }

    public record SolicitudRetiro(@NotNull Instant version) {
    }

    record Proceso(long id, String estadoPublicacion, String motivoFin, String detalleCancelacion,
            int plazas, Instant version, boolean empresaActiva) {
        boolean enCurso() {
            return !"borrador".equals(estadoPublicacion) && motivoFin == null;
        }
    }

    record Postulacion(long id, String estado, Instant version, boolean cuentaActiva) {
        boolean activa() {
            return ESTADOS_ACTIVOS.contains(estado);
        }
    }

    public record Oferta(long id, String condiciones, LocalDate fechaInicio, Instant venceEn, String estado,
            Instant enviadaEn, Instant respondidaEn, Instant resueltaEn) {
        public boolean reserva() {
            return "pendiente".equals(estado) || "aceptada".equals(estado);
        }

        public String nombreEstado() {
            return switch (estado) {
                case "pendiente" -> "Oferta enviada";
                case "aceptada" -> "Oferta aceptada";
                case "rechazada" -> "Oferta rechazada";
                case "vencida" -> "Oferta vencida";
                case "retirada" -> "Oferta retirada";
                case "contratada" -> "Contratación confirmada";
                default -> throw new IllegalStateException("Estado de oferta desconocido.");
            };
        }
    }

    public record Progreso(int plazas, long cubiertas, long reservadas, long postulacionesActivas,
            Instant versionVacante) {
        public boolean ultimaPlaza() {
            return cubiertas + 1 == plazas;
        }

        public long alFinalizarUltimaPlaza() {
            return ultimaPlaza() ? Math.max(0, postulacionesActivas - 1) : 0;
        }

        boolean quedanPlazas() {
            return cubiertas + reservadas < plazas;
        }
    }

    public record Ficha(List<Oferta> ofertas, Progreso progreso, boolean puedeEvaluar, boolean puedeOfertar,
            boolean puedeGestionarOferta, boolean puedeRetirarOferta, boolean puedeRetirarPostulacion) {
        public Ficha {
            ofertas = List.copyOf(ofertas);
        }
    }

    public record Aviso(long idPostulacion, String tituloVacante, String tipo, Instant fecha) {
        public String mensaje() {
            return switch (tipo) {
                case "oferta_enviada" -> "Tienes una oferta para revisar.";
                case "oferta_aceptada" -> "El postulante aceptó la oferta.";
                case "oferta_rechazada" -> "El postulante rechazó la oferta.";
                case "oferta_retirada" -> "La empresa retiró la oferta.";
                case "contratacion_confirmada" -> "La empresa confirmó tu contratación.";
                case "vacante_cubierta" -> "La empresa cubrió las plazas de esta convocatoria. Gracias por postular.";
                case "proceso_cancelado" -> "La empresa canceló el proceso de selección.";
                case "postulacion_retirada" -> "El postulante retiró su solicitud.";
                default -> throw new IllegalStateException("Tipo de aviso desconocido.");
            };
        }
    }
}
