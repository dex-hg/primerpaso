package com.dextre.primerpaso.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DatosChat {

    private DatosChat() {
    }

    public record SolicitudMensaje(@NotNull UUID idEnvio, @NotBlank @Size(max = 4000) String contenido) {
        public SolicitudMensaje {
            contenido = contenido == null ? null : contenido.replace("\r\n", "\n").replace('\r', '\n').strip();
        }

        @Override
        public String toString() {
            return "SolicitudMensaje[contenido privado]";
        }
    }

    public record Contexto(long idPostulacion, long idVacante, String tituloVacante, String nombreEmpresa,
            String nombrePostulante, String estadoPostulacion, boolean participantesActivos) {
    }

    public record Mensaje(long id, long idEmisor, UUID idEnvio, String nombreEmisor, String tipoEmisor,
            String contenido, Instant fechaEnvio) {
    }

    public record Detalle(Contexto contexto, List<Mensaje> mensajes, boolean iniciada, boolean puedeEnviar,
            String aviso, boolean hayAnteriores, boolean haySiguientes) {
        public Detalle {
            mensajes = List.copyOf(mensajes);
        }
    }

    public record Conversacion(long idPostulacion, String tituloVacante, String nombreEmpresa,
            String nombrePostulante, String estadoPostulacion, String ultimoMensaje, Instant fechaUltimoMensaje) {
    }

    public record PaginaConversaciones(List<Conversacion> conversaciones, int pagina, int totalPaginas, long total) {
        public PaginaConversaciones {
            conversaciones = List.copyOf(conversaciones);
        }
    }

    public record ResultadoEnvio(Mensaje mensaje, boolean creado) {
    }
}
