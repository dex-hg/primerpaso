package com.dextre.primerpaso.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.chat.DatosChat.Contexto;
import com.dextre.primerpaso.chat.DatosChat.Detalle;
import com.dextre.primerpaso.chat.DatosChat.Mensaje;
import com.dextre.primerpaso.chat.DatosChat.PaginaConversaciones;
import com.dextre.primerpaso.chat.DatosChat.ResultadoEnvio;
import com.dextre.primerpaso.chat.DatosChat.SolicitudMensaje;
import com.dextre.primerpaso.postulaciones.RepositorioPostulaciones;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.RepositorioVacantes;

@Service
public class ServicioChat {

    private static final int TAMANIO_HISTORIAL = 50;
    private static final int TAMANIO_BANDEJA = 12;
    private final RepositorioChat repositorio;
    private final RepositorioPostulaciones postulaciones;
    private final RepositorioVacantes vacantes;

    public ServicioChat(RepositorioChat repositorio, RepositorioPostulaciones postulaciones, RepositorioVacantes vacantes) {
        this.repositorio = repositorio;
        this.postulaciones = postulaciones;
        this.vacantes = vacantes;
    }

    @Transactional(readOnly = true)
    public PaginaConversaciones listar(UsuarioSesion usuario, int pagina) {
        comprobarAcceso(usuario, false);
        if (pagina < 0) throw regla(HttpStatus.BAD_REQUEST, "El número de página no es válido.");
        long total = repositorio.contarConversaciones(usuario);
        int totalPaginas = (int) Math.min(Integer.MAX_VALUE, (total + TAMANIO_BANDEJA - 1) / TAMANIO_BANDEJA);
        int paginaActual = totalPaginas == 0 ? 0 : Math.min(pagina, totalPaginas - 1);
        return new PaginaConversaciones(repositorio.listarConversaciones(usuario, paginaActual, TAMANIO_BANDEJA),
                paginaActual, totalPaginas, total);
    }

    @Transactional(readOnly = true)
    public Detalle consultar(UsuarioSesion usuario, long idPostulacion, Long antesDe, Long despuesDe) {
        comprobarAcceso(usuario, false);
        validarIdentificador(idPostulacion);
        if ((antesDe != null && antesDe <= 0) || (despuesDe != null && despuesDe <= 0)
                || (antesDe != null && despuesDe != null)) {
            throw regla(HttpStatus.BAD_REQUEST, "Usa un único cursor de historial válido.");
        }
        Contexto contexto = obtenerContexto(usuario, idPostulacion, false);
        boolean iniciada = repositorio.estaIniciada(idPostulacion);
        List<Mensaje> consulta = repositorio.listarMensajes(idPostulacion, antesDe, despuesDe, TAMANIO_HISTORIAL + 1);
        boolean hayMas = consulta.size() > TAMANIO_HISTORIAL;
        List<Mensaje> mensajes = new ArrayList<>(consulta.subList(0, Math.min(consulta.size(), TAMANIO_HISTORIAL)));
        if (despuesDe == null) Collections.reverse(mensajes);
        String aviso = explicarDisponibilidad(usuario, contexto, iniciada);
        return new Detalle(contexto, mensajes, iniciada, aviso.isEmpty(), aviso,
                despuesDe == null && hayMas, despuesDe != null && hayMas);
    }

    @Transactional
    public ResultadoEnvio enviar(UsuarioSesion usuario, long idPostulacion, SolicitudMensaje datos) {
        comprobarAcceso(usuario, true);
        validarIdentificador(idPostulacion);
        validarMensaje(datos);
        // El mismo bloqueo serializa los envíos y los cambios de estado de la postulación.
        Contexto contexto = obtenerContexto(usuario, idPostulacion, true);
        var anterior = repositorio.buscarEnvio(idPostulacion, usuario.idUsuario(), datos.idEnvio());
        if (anterior.isPresent()) {
            if (!anterior.get().contenido().equals(datos.contenido())) {
                throw regla(HttpStatus.CONFLICT, "Este envío ya se usó para otro contenido. Actualiza la conversación.");
            }
            return new ResultadoEnvio(anterior.get(), false);
        }
        String aviso = explicarDisponibilidad(usuario, contexto, repositorio.estaIniciada(idPostulacion));
        if (!aviso.isEmpty()) throw regla(HttpStatus.CONFLICT, aviso);
        if (repositorio.excedeFrecuencia(idPostulacion, usuario.idUsuario())) {
            throw regla(HttpStatus.TOO_MANY_REQUESTS, "Espera un minuto antes de enviar más mensajes en esta conversación.");
        }
        return new ResultadoEnvio(repositorio.guardar(idPostulacion, usuario, datos.idEnvio(), datos.contenido()), true);
    }

    private String explicarDisponibilidad(UsuarioSesion usuario, Contexto contexto, boolean iniciada) {
        if (!contexto.participantesActivos()) return "La conversación está en modo de lectura porque una cuenta o empresa está inactiva.";
        if ("finalizada".equals(contexto.estadoPostulacion())) {
            return "Conversación en modo de lectura. El proceso de selección finalizó.";
        }
        boolean contratada = "contratada".equals(contexto.estadoPostulacion());
        if (!contratada && !"seleccionada".equals(contexto.estadoPostulacion())) {
            return iniciada ? "Conversación en modo de lectura. La postulación ya no está seleccionada."
                    : "El chat se habilita cuando la empresa selecciona esta postulación para continuar el proceso.";
        }
        if (!iniciada && "postulante".equals(usuario.tipoCuenta())) {
            return contratada ? "La empresa confirmó tu contratación. Podrás responder cuando inicie la conversación."
                    : "Tu postulación está seleccionada. Podrás responder cuando el reclutador envíe el primer mensaje.";
        }
        return "";
    }

    private void comprobarAcceso(UsuarioSesion usuario, boolean bloquear) {
        if (usuario == null || usuario.idUsuario() <= 0) throw regla(HttpStatus.FORBIDDEN, "Necesitas una cuenta activa.");
        if ("postulante".equals(usuario.tipoCuenta()) && usuario.idEmpresa() == null
                && postulaciones.postulanteActivo(usuario.idUsuario(), bloquear)) return;
        if ("empresa".equals(usuario.tipoCuenta()) && usuario.idEmpresa() != null && usuario.idEmpresa() > 0
                && ("administrador".equals(usuario.rolEmpresa()) || "reclutador".equals(usuario.rolEmpresa()))
                && vacantes.membresiaActiva(usuario.idUsuario(), usuario.idEmpresa(), bloquear)) return;
        throw regla(HttpStatus.FORBIDDEN, "Tu cuenta no tiene acceso a las conversaciones.");
    }

    private Contexto obtenerContexto(UsuarioSesion usuario, long idPostulacion, boolean bloquear) {
        return repositorio.consultarContexto(usuario, idPostulacion, bloquear)
                .orElseThrow(() -> regla(HttpStatus.NOT_FOUND, "La conversación no está disponible para tu cuenta."));
    }

    private void validarIdentificador(long identificador) {
        if (identificador <= 0) throw regla(HttpStatus.BAD_REQUEST, "La postulación no es válida.");
    }

    private void validarMensaje(SolicitudMensaje datos) {
        if (datos == null || datos.idEnvio() == null || datos.contenido() == null || datos.contenido().isBlank()
                || datos.contenido().length() > 4000 || datos.contenido().codePoints()
                    .allMatch(caracter -> Character.isWhitespace(caracter) || Character.isSpaceChar(caracter))
                || datos.contenido().codePoints().anyMatch(caracter -> Character.isISOControl(caracter)
                    && caracter != '\n' && caracter != '\t')) {
            throw regla(HttpStatus.BAD_REQUEST, "Escribe un mensaje de 1 a 4000 caracteres, sin caracteres de control.");
        }
    }

    private ResponseStatusException regla(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }
}
