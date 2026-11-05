package com.dextre.primerpaso.contratacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.contratacion.DatosContratacion.Aviso;
import com.dextre.primerpaso.contratacion.DatosContratacion.Ficha;
import com.dextre.primerpaso.contratacion.DatosContratacion.Oferta;
import com.dextre.primerpaso.contratacion.DatosContratacion.Postulacion;
import com.dextre.primerpaso.contratacion.DatosContratacion.Proceso;
import com.dextre.primerpaso.contratacion.DatosContratacion.SolicitudCancelacion;
import com.dextre.primerpaso.contratacion.DatosContratacion.SolicitudConfirmacion;
import com.dextre.primerpaso.contratacion.DatosContratacion.SolicitudOferta;
import com.dextre.primerpaso.postulaciones.RepositorioPostulaciones;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.RepositorioVacantes;

@Service
public class ServicioContratacion {

    private static final Instant FECHA_MAXIMA = Instant.parse("9999-12-31T23:59:59Z");
    private static final LocalDate INICIO_MAXIMO = LocalDate.of(9999, 12, 31);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final RepositorioContratacion repositorio;
    private final RepositorioPostulaciones postulaciones;
    private final RepositorioVacantes vacantes;

    public ServicioContratacion(RepositorioContratacion repositorio, RepositorioPostulaciones postulaciones,
            RepositorioVacantes vacantes) {
        this.repositorio = repositorio;
        this.postulaciones = postulaciones;
        this.vacantes = vacantes;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Ficha consultar(UsuarioSesion usuario, long idPostulacion) {
        boolean empresa = esEmpresa(usuario);
        comprobarCuenta(usuario, empresa, false);
        var contexto = consultarContexto(usuario, idPostulacion, false);
        var proceso = contexto.proceso();
        var postulacion = contexto.postulacion();
        var ofertas = repositorio.ofertas(idPostulacion);
        var progreso = empresa ? repositorio.progreso(proceso) : null;
        boolean reservada = !ofertas.isEmpty() && ofertas.getFirst().reserva();
        boolean enCurso = proceso.enCurso();
        boolean seleccionada = "seleccionada".equals(postulacion.estado());
        boolean gestionable = enCurso && seleccionada && proceso.empresaActiva() && postulacion.cuentaActiva();
        boolean evaluable = empresa && enCurso && !reservada
                && (postulacion.activa() || "descartada".equals(postulacion.estado()));
        return new Ficha(ofertas, progreso, evaluable,
                empresa && gestionable && !reservada && progreso.quedanPlazas(), gestionable,
                empresa && enCurso && seleccionada && reservada, !empresa && enCurso && postulacion.activa());
    }

    @Transactional(readOnly = true)
    public List<Aviso> avisos(UsuarioSesion usuario, Long idVacante) {
        comprobarCuenta(usuario, esEmpresa(usuario), false);
        return repositorio.avisos(usuario, idVacante);
    }

    @Transactional
    public Oferta enviarOferta(UsuarioSesion usuario, long idPostulacion, SolicitudOferta datos) {
        comprobarCuenta(usuario, true, true);
        validarOferta(datos);
        var contexto = consultarContexto(usuario, idPostulacion, true);
        var anterior = repositorio.buscarEnvio(idPostulacion, datos.idEnvio());
        if (anterior.isPresent()) {
            Oferta oferta = anterior.get();
            if (oferta.condiciones().equals(datos.condiciones()) && oferta.fechaInicio().equals(datos.fechaInicio())
                    && oferta.venceEn().equals(datos.venceEn())) return oferta;
            throw regla(HttpStatus.CONFLICT, "Este envío ya corresponde a otra oferta. Recarga la postulación.");
        }

        exigirSeleccionActiva(contexto, true);
        comprobarVersion(contexto.postulacion().version(), datos.version());
        if (repositorio.tieneReserva(idPostulacion)) {
            throw regla(HttpStatus.CONFLICT, "Esta postulación ya tiene una oferta pendiente o aceptada.");
        }
        if (!repositorio.progreso(contexto.proceso()).quedanPlazas()) {
            throw regla(HttpStatus.CONFLICT, "Todas las plazas están cubiertas o reservadas.");
        }
        // Materializa el vencimiento antes de reservar otra vez para esta postulación.
        repositorio.vencerOfertas(idPostulacion);
        long idOferta = repositorio.crearOferta(idPostulacion, usuario.idUsuario(), datos)
                .orElseThrow(() -> regla(HttpStatus.BAD_REQUEST, "El plazo de respuesta debe terminar en el futuro."));
        repositorio.actualizarPostulacion(idPostulacion, null);
        repositorio.avisar(idPostulacion, "postulante", "oferta_enviada");
        return buscarOferta(idPostulacion, idOferta);
    }

    @Transactional
    public Oferta responder(UsuarioSesion usuario, long idPostulacion, long idOferta, boolean aceptada) {
        comprobarCuenta(usuario, false, true);
        var contexto = consultarContexto(usuario, idPostulacion, true);
        Oferta oferta = buscarOferta(idPostulacion, idOferta);
        String estado = aceptada ? "aceptada" : "rechazada";
        if (estado.equals(oferta.estado()) || (aceptada && "contratada".equals(oferta.estado()))) return oferta;

        exigirSeleccionActiva(contexto, true);
        if (!"pendiente".equals(oferta.estado())
                || !repositorio.cambiarOferta(idOferta, "pendiente", estado, usuario.idUsuario())) {
            throw regla(HttpStatus.CONFLICT, "La oferta ya se resolvió o pasó el plazo de respuesta.");
        }
        repositorio.actualizarPostulacion(idPostulacion, null);
        repositorio.avisar(idPostulacion, "empresa", aceptada ? "oferta_aceptada" : "oferta_rechazada");
        return buscarOferta(idPostulacion, idOferta);
    }

    @Transactional
    public Oferta retirarOferta(UsuarioSesion usuario, long idPostulacion, long idOferta) {
        comprobarCuenta(usuario, true, true);
        var contexto = consultarContexto(usuario, idPostulacion, true);
        Oferta oferta = buscarOferta(idPostulacion, idOferta);
        if ("retirada".equals(oferta.estado())) return oferta;

        exigirSeleccionActiva(contexto, false);
        if (!oferta.reserva() || !repositorio.cambiarOferta(idOferta, oferta.estado(), "retirada", usuario.idUsuario())) {
            throw regla(HttpStatus.CONFLICT, "Solo se puede retirar una oferta pendiente o aceptada.");
        }
        repositorio.actualizarPostulacion(idPostulacion, null);
        repositorio.avisar(idPostulacion, "postulante", "oferta_retirada");
        return buscarOferta(idPostulacion, idOferta);
    }

    @Transactional
    public void retirarPostulacion(UsuarioSesion usuario, long idPostulacion, Instant version) {
        comprobarCuenta(usuario, false, true);
        var contexto = consultarContexto(usuario, idPostulacion, true);
        if ("retirada".equals(contexto.postulacion().estado())) return;

        exigirProcesoEnCurso(contexto.proceso());
        if (!contexto.postulacion().activa()) {
            throw regla(HttpStatus.CONFLICT, "Solo puedes retirar una postulación activa.");
        }
        comprobarVersion(contexto.postulacion().version(), version);
        repositorio.retirarOfertasDePostulacion(idPostulacion, usuario.idUsuario());
        repositorio.actualizarPostulacion(idPostulacion, "retirada");
        repositorio.avisar(idPostulacion, "empresa", "postulacion_retirada");
    }

    @Transactional
    public Oferta confirmar(UsuarioSesion usuario, long idPostulacion, long idOferta, SolicitudConfirmacion datos) {
        comprobarCuenta(usuario, true, true);
        var contexto = consultarContexto(usuario, idPostulacion, true);
        Oferta oferta = buscarOferta(idPostulacion, idOferta);
        if ("contratada".equals(oferta.estado())) return oferta;

        exigirSeleccionActiva(contexto, true);
        if (!"aceptada".equals(oferta.estado())) {
            throw regla(HttpStatus.CONFLICT, "El postulante debe aceptar la oferta antes de confirmar su contratación.");
        }
        Proceso proceso = contexto.proceso();
        var progreso = repositorio.progreso(proceso);
        comprobarVersion(proceso.version(), datos.versionVacante());
        comprobarPendientes(progreso.alFinalizarUltimaPlaza(), datos.postulacionesAFinalizar());
        if (progreso.cubiertas() >= progreso.plazas() || progreso.cubiertas() + progreso.reservadas() > progreso.plazas()) {
            throw regla(HttpStatus.CONFLICT, "Ya no hay cupo para confirmar esta contratación.");
        }

        if (!repositorio.cambiarOferta(idOferta, "aceptada", "contratada", usuario.idUsuario())) {
            throw regla(HttpStatus.CONFLICT, "La oferta cambió. Recarga la postulación.");
        }
        repositorio.actualizarPostulacion(idPostulacion, "contratada");
        repositorio.avisar(idPostulacion, "postulante", "contratacion_confirmada");
        if (progreso.ultimaPlaza()) {
            terminarProceso(proceso.id(), "vacante_cubierta", null, usuario.idUsuario());
        } else {
            repositorio.actualizarVersionVacante(proceso.id());
        }
        return buscarOferta(idPostulacion, idOferta);
    }

    @Transactional
    public void cancelarProceso(UsuarioSesion usuario, long idVacante, SolicitudCancelacion datos) {
        comprobarCuenta(usuario, true, true);
        validarId(idVacante);
        validarTexto(datos.motivo(), 500, "Escribe el motivo de cancelación en hasta 500 caracteres.");
        Proceso proceso = repositorio.bloquearProceso(idVacante, usuario.idEmpresa())
                .orElseThrow(() -> regla(HttpStatus.NOT_FOUND, "La vacante no está disponible para tu empresa."));
        if ("proceso_cancelado".equals(proceso.motivoFin()) && datos.motivo().equals(proceso.detalleCancelacion())) return;

        exigirProcesoEnCurso(proceso);
        comprobarVersion(proceso.version(), datos.versionVacante());
        comprobarPendientes(repositorio.progreso(proceso).postulacionesActivas(), datos.postulacionesAFinalizar());
        if (repositorio.tieneOfertasAceptadas(idVacante)) {
            throw regla(HttpStatus.CONFLICT, "Confirma la contratación o retira cada oferta aceptada antes de cancelar el proceso.");
        }
        terminarProceso(idVacante, "proceso_cancelado", datos.motivo(), usuario.idUsuario());
    }

    private void terminarProceso(long idVacante, String motivo, String detalle, long idResponsable) {
        repositorio.retirarOfertasPendientesDeVacante(idVacante, idResponsable);
        repositorio.cerrarProceso(idVacante, motivo, detalle, idResponsable);
        repositorio.finalizarPostulaciones(idVacante, motivo);
    }

    private Contexto consultarContexto(UsuarioSesion usuario, long idPostulacion, boolean bloquear) {
        validarId(idPostulacion);
        // Para mutaciones, la vacante se bloquea antes de releer la postulación y sus ofertas.
        Proceso proceso = repositorio.procesoDePostulacion(usuario, idPostulacion, bloquear)
                .orElseThrow(() -> regla(HttpStatus.NOT_FOUND, "La postulación no está disponible para tu cuenta."));
        return new Contexto(proceso, repositorio.consultarPostulacion(idPostulacion, proceso.id(), bloquear));
    }

    private Oferta buscarOferta(long idPostulacion, long idOferta) {
        validarId(idOferta);
        return repositorio.buscarOferta(idPostulacion, idOferta)
                .orElseThrow(() -> regla(HttpStatus.NOT_FOUND, "La oferta no está disponible para esta postulación."));
    }

    private void exigirSeleccionActiva(Contexto contexto, boolean exigirPostulanteActivo) {
        exigirProcesoEnCurso(contexto.proceso());
        if (!"seleccionada".equals(contexto.postulacion().estado())) {
            throw regla(HttpStatus.CONFLICT, "La postulación debe estar seleccionada.");
        }
        if (!contexto.proceso().empresaActiva() || (exigirPostulanteActivo && !contexto.postulacion().cuentaActiva())) {
            throw regla(HttpStatus.CONFLICT, "La empresa y la cuenta del postulante deben estar activas.");
        }
    }

    private void exigirProcesoEnCurso(Proceso proceso) {
        if (!proceso.enCurso()) throw regla(HttpStatus.CONFLICT, "La vacante no tiene un proceso de selección activo.");
    }

    private void comprobarCuenta(UsuarioSesion usuario, boolean empresa, boolean bloquear) {
        if (usuario != null && usuario.idUsuario() > 0) {
            if (empresa && esEmpresa(usuario) && usuario.idEmpresa() != null && usuario.idEmpresa() > 0
                    && vacantes.membresiaActiva(usuario.idUsuario(), usuario.idEmpresa(), bloquear)) return;
            if (!empresa && "postulante".equals(usuario.tipoCuenta()) && usuario.idEmpresa() == null
                    && postulaciones.postulanteActivo(usuario.idUsuario(), bloquear)) return;
        }
        throw regla(HttpStatus.FORBIDDEN, empresa
                ? "Necesitas una membresía empresarial activa para realizar esta operación."
                : "Necesitas una cuenta de postulante activa para realizar esta operación.");
    }

    private boolean esEmpresa(UsuarioSesion usuario) {
        return usuario != null && "empresa".equals(usuario.tipoCuenta());
    }

    private void comprobarVersion(Instant actual, Instant esperada) {
        if (!actual.equals(esperada)) {
            throw regla(HttpStatus.CONFLICT, "El proceso cambió. Recarga la página y revisa el resultado antes de continuar.");
        }
    }

    private void comprobarPendientes(long actual, Long esperado) {
        if (!Objects.equals(actual, esperado)) {
            throw regla(HttpStatus.CONFLICT, "Cambió el número de postulaciones por finalizar. Recarga la página para revisar el impacto.");
        }
    }

    private void validarOferta(SolicitudOferta datos) {
        validarTexto(datos.condiciones(), 4000, "Escribe las condiciones de la oferta en hasta 4000 caracteres.");
        if (datos.venceEn().isAfter(FECHA_MAXIMA) || datos.venceEn().isBefore(Instant.EPOCH)
                || datos.fechaInicio().isAfter(INICIO_MAXIMO)
                || datos.fechaInicio().isBefore(datos.venceEn().atZone(LIMA).toLocalDate())) {
            throw regla(HttpStatus.BAD_REQUEST, "La fecha de inicio debe ser igual o posterior al día del plazo de respuesta.");
        }
    }

    private void validarTexto(String texto, int maximo, String mensaje) {
        if (texto == null || texto.isBlank() || texto.length() > maximo
                || texto.codePoints().anyMatch(caracter -> Character.isISOControl(caracter)
                    && caracter != '\n' && caracter != '\r' && caracter != '\t')) {
            throw regla(HttpStatus.BAD_REQUEST, mensaje);
        }
    }

    private void validarId(long id) {
        if (id <= 0) throw regla(HttpStatus.BAD_REQUEST, "El identificador no es válido.");
    }

    private ResponseStatusException regla(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    private record Contexto(Proceso proceso, Postulacion postulacion) {
    }
}
