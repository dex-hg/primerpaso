package com.dextre.primerpaso.actividad;

import java.time.Instant;
import java.util.List;

public final class DatosActividad {

    private static final String CONTRATACION_CONFIRMADA = "Confirmaron tu contratación";

    private DatosActividad() {
    }

    public record Pagina<T>(List<T> elementos, int numero, boolean haySiguiente) {
        public Pagina {
            elementos = List.copyOf(elementos);
        }
    }

    public record Notificacion(long id, long idPostulacion, String tituloVacante, String nombreEmpresa,
            String tipo, Instant fecha, boolean leida) {
        public String titulo() {
            return switch (tipo) {
                case "oferta_enviada" -> "Recibiste una oferta";
                case "oferta_aceptada" -> "El postulante aceptó la oferta";
                case "oferta_rechazada" -> "El postulante rechazó la oferta";
                case "oferta_retirada" -> "La empresa retiró la oferta";
                case "contratacion_confirmada" -> CONTRATACION_CONFIRMADA;
                case "vacante_cubierta" -> "La empresa cubrió las plazas de la convocatoria";
                case "proceso_cancelado" -> "La empresa canceló el proceso de selección";
                case "postulacion_retirada" -> "El postulante retiró su solicitud";
                default -> throw new IllegalStateException("Tipo de notificación desconocido.");
            };
        }
    }

    public record Accion(Long idPostulacion, long idVacante, String tituloVacante, String nombreEmpresa,
            String tipo, Instant fecha) {
        public String titulo() {
            return switch (tipo) {
                case "postulaste" -> "Postulaste a una vacante";
                case "aceptaste" -> "Aceptaste una oferta";
                case "rechazaste" -> "Rechazaste una oferta";
                case "contratacion_confirmada" -> CONTRATACION_CONFIRMADA;
                case "retiraste" -> "Retiraste tu postulación";
                case "creaste" -> "Creaste una vacante";
                case "enviaste" -> "Enviaste una oferta";
                case "confirmaste" -> "Confirmaste una contratación";
                case "cancelaste" -> "Cancelaste un proceso de selección";
                default -> throw new IllegalStateException("Tipo de acción desconocido.");
            };
        }
    }

    public record Bandeja(Pagina<Notificacion> pagina, long pendientes) {
    }

    public record Pendientes(long cantidad, long idUsuario, Long idEmpresa) {
    }
}
