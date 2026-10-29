package com.dextre.primerpaso.postulaciones;

import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.postulaciones.DatosPostulaciones.Detalle;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.PaginaPostulaciones;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.SolicitudEstado;
import com.dextre.primerpaso.postulaciones.DatosPostulaciones.SolicitudPostulacion;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.RepositorioVacantes;

@Service
public class ServicioPostulaciones {

    private static final int TAMANIO_PAGINA = 12;
    private static final Set<String> ESTADOS = Set.of("todos", "enviada", "en_revision", "seleccionada", "descartada", "retirada");
    private static final Set<String> ESTADOS_EMPRESA = Set.of("en_revision", "seleccionada", "descartada");
    private final RepositorioPostulaciones repositorio;
    private final RepositorioVacantes vacantes;

    public ServicioPostulaciones(RepositorioPostulaciones repositorio, RepositorioVacantes vacantes) {
        this.repositorio = repositorio;
        this.vacantes = vacantes;
    }

    @Transactional
    public Detalle postular(UsuarioSesion usuario, SolicitudPostulacion datos) {
        exigirPostulante(usuario, true);
        if (datos == null || datos.idVacante() == null || datos.idVacante() <= 0
                || datos.cartaPresentacion() == null || datos.cartaPresentacion().isBlank()
                || datos.cartaPresentacion().length() > 12000
                || datos.cartaPresentacion().codePoints().anyMatch(caracter -> Character.isISOControl(caracter)
                    && caracter != '\n' && caracter != '\r' && caracter != '\t')) {
            throw regla(HttpStatus.BAD_REQUEST, "Escribe tu carta de presentación sin superar los 12 000 caracteres y elige una vacante válida.");
        }
        if (repositorio.buscarPropia(usuario.idUsuario(), datos.idVacante()).isPresent()) {
            throw regla(HttpStatus.CONFLICT, "Ya enviaste una postulación a esta vacante. Consúltala en Mis postulaciones.");
        }
        if (!repositorio.bloquearVacanteDisponible(datos.idVacante())) {
            throw regla(HttpStatus.NOT_FOUND, "Esta vacante ya no está disponible para postular.");
        }
        long idPostulacion = repositorio.crear(usuario.idUsuario(), datos.idVacante(), datos.cartaPresentacion())
                .orElseThrow(() -> regla(HttpStatus.CONFLICT,
                        "Ya postulaste o la vacante dejó de estar disponible. Revisa Mis postulaciones antes de reintentar."));
        return obtenerDetalle(usuario, idPostulacion, false);
    }

    @Transactional(readOnly = true)
    public Optional<Long> buscarPropia(UsuarioSesion usuario, long idVacante) {
        exigirPostulante(usuario, false);
        validarIdentificador(idVacante);
        return repositorio.buscarPropia(usuario.idUsuario(), idVacante);
    }

    @Transactional(readOnly = true)
    public PaginaPostulaciones listar(UsuarioSesion usuario, int pagina, String estado, Long idVacante) {
        comprobarAcceso(usuario, false);
        if (pagina < 0 || estado == null || !ESTADOS.contains(estado) || (idVacante != null && idVacante <= 0)) {
            throw regla(HttpStatus.BAD_REQUEST, "Revisa el estado, la vacante y el número de página.");
        }
        String titulo = null;
        if (idVacante != null && "empresa".equals(usuario.tipoCuenta())) {
            titulo = repositorio.tituloVacanteEmpresa(usuario.idEmpresa(), idVacante)
                    .orElseThrow(() -> regla(HttpStatus.NOT_FOUND, "La vacante no está disponible para tu empresa."));
        }
        long total = repositorio.contar(usuario, estado, idVacante);
        int totalPaginas = (int) Math.min(Integer.MAX_VALUE, total / TAMANIO_PAGINA
                + (total % TAMANIO_PAGINA == 0 ? 0 : 1));
        int paginaActual = totalPaginas == 0 ? 0 : Math.min(pagina, totalPaginas - 1);
        return new PaginaPostulaciones(repositorio.listar(usuario, estado, idVacante, paginaActual, TAMANIO_PAGINA),
                paginaActual, totalPaginas, total, titulo);
    }

    @Transactional(readOnly = true)
    public Detalle consultar(UsuarioSesion usuario, long idPostulacion) {
        comprobarAcceso(usuario, false);
        validarIdentificador(idPostulacion);
        return obtenerDetalle(usuario, idPostulacion, false);
    }

    @Transactional
    public Detalle cambiarEstado(UsuarioSesion usuario, long idPostulacion, SolicitudEstado datos) {
        exigirEmpresa(usuario, true);
        validarIdentificador(idPostulacion);
        if (datos == null || datos.estado() == null || !ESTADOS_EMPRESA.contains(datos.estado()) || datos.version() == null) {
            throw regla(HttpStatus.BAD_REQUEST, "Selecciona un estado válido y recarga la postulación antes de guardar.");
        }
        Detalle detalle = obtenerDetalle(usuario, idPostulacion, true);
        if (!datos.version().equals(detalle.postulacion().version())) {
            throw regla(HttpStatus.CONFLICT, "Otra persona actualizó la postulación. Recarga la página antes de continuar.");
        }
        if ("retirada".equals(detalle.postulacion().estado())) {
            throw regla(HttpStatus.CONFLICT, "Una postulación retirada no admite cambios de estado.");
        }
        if (datos.estado().equals(detalle.postulacion().estado())) return detalle;
        repositorio.actualizarEstado(usuario.idEmpresa(), idPostulacion, datos.estado(), datos.version());
        return obtenerDetalle(usuario, idPostulacion, false);
    }

    private void comprobarAcceso(UsuarioSesion usuario, boolean bloquear) {
        if (usuario != null && "empresa".equals(usuario.tipoCuenta())) exigirEmpresa(usuario, bloquear);
        else exigirPostulante(usuario, bloquear);
    }

    private void exigirPostulante(UsuarioSesion usuario, boolean bloquear) {
        if (usuario == null || !"postulante".equals(usuario.tipoCuenta()) || usuario.idUsuario() <= 0
                || usuario.idEmpresa() != null || !repositorio.postulanteActivo(usuario.idUsuario(), bloquear)) {
            throw regla(HttpStatus.FORBIDDEN, "Necesitas una cuenta de postulante activa para realizar esta operación.");
        }
    }

    private void exigirEmpresa(UsuarioSesion usuario, boolean bloquear) {
        if (usuario == null || !"empresa".equals(usuario.tipoCuenta()) || usuario.idUsuario() <= 0
                || usuario.idEmpresa() == null || usuario.idEmpresa() <= 0
                || !("administrador".equals(usuario.rolEmpresa()) || "reclutador".equals(usuario.rolEmpresa()))
                || !vacantes.membresiaActiva(usuario.idUsuario(), usuario.idEmpresa(), bloquear)) {
            throw regla(HttpStatus.FORBIDDEN, "Necesitas una membresía empresarial activa para revisar postulaciones.");
        }
    }

    private Detalle obtenerDetalle(UsuarioSesion usuario, long idPostulacion, boolean bloquear) {
        return repositorio.consultar(usuario, idPostulacion, bloquear)
                .orElseThrow(() -> regla(HttpStatus.NOT_FOUND, "La postulación no está disponible para tu cuenta."));
    }

    private void validarIdentificador(long identificador) {
        if (identificador <= 0) throw regla(HttpStatus.BAD_REQUEST, "El identificador no es válido.");
    }

    private ResponseStatusException regla(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }
}
