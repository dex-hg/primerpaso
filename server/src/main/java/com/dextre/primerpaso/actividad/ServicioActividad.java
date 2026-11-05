package com.dextre.primerpaso.actividad;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.actividad.DatosActividad.Accion;
import com.dextre.primerpaso.actividad.DatosActividad.Bandeja;
import com.dextre.primerpaso.actividad.DatosActividad.Pagina;
import com.dextre.primerpaso.postulaciones.RepositorioPostulaciones;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.RepositorioVacantes;

@Service
public class ServicioActividad {

    private static final int TAMANIO_PAGINA = 12;
    private final RepositorioNotificaciones notificaciones;
    private final RepositorioHistorial historial;
    private final RepositorioPostulaciones postulaciones;
    private final RepositorioVacantes vacantes;

    public ServicioActividad(RepositorioNotificaciones notificaciones, RepositorioHistorial historial,
            RepositorioPostulaciones postulaciones, RepositorioVacantes vacantes) {
        this.notificaciones = notificaciones;
        this.historial = historial;
        this.postulaciones = postulaciones;
        this.vacantes = vacantes;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Bandeja bandeja(UsuarioSesion usuario, String estado, int numero) {
        comprobarCuenta(usuario, false);
        if (!("todas".equals(estado) || "sin_leer".equals(estado))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El filtro de notificaciones no es válido.");
        }
        var avisos = notificaciones.listar(usuario, "sin_leer".equals(estado),
                desplazamiento(numero), TAMANIO_PAGINA + 1);
        return new Bandeja(pagina(avisos, numero), notificaciones.pendientes(usuario));
    }

    @Transactional(readOnly = true)
    public Pagina<Accion> historial(UsuarioSesion usuario, int numero) {
        comprobarCuenta(usuario, false);
        return pagina(historial.listar(usuario, desplazamiento(numero), TAMANIO_PAGINA + 1), numero);
    }

    @Transactional(readOnly = true)
    public long pendientes(UsuarioSesion usuario) {
        comprobarCuenta(usuario, false);
        return notificaciones.pendientes(usuario);
    }

    @Transactional
    public long marcarLeida(UsuarioSesion usuario, long idAviso) {
        comprobarCuenta(usuario, true);
        return notificaciones.marcarLeida(usuario, idAviso)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La notificación no está disponible."));
    }

    @Transactional
    public void marcarTodasLeidas(UsuarioSesion usuario) {
        comprobarCuenta(usuario, true);
        notificaciones.marcarTodasLeidas(usuario);
    }

    private void comprobarCuenta(UsuarioSesion usuario, boolean bloquear) {
        if (usuario != null && usuario.idUsuario() > 0) {
            if ("postulante".equals(usuario.tipoCuenta()) && usuario.idEmpresa() == null
                    && postulaciones.postulanteActivo(usuario.idUsuario(), bloquear)) return;
            if ("empresa".equals(usuario.tipoCuenta()) && usuario.idEmpresa() != null && usuario.idEmpresa() > 0
                    && vacantes.membresiaActiva(usuario.idUsuario(), usuario.idEmpresa(), bloquear)) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Necesitas una cuenta activa.");
    }

    private long desplazamiento(int numero) {
        if (numero < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de página no es válido.");
        return (long) numero * TAMANIO_PAGINA;
    }

    private <T> Pagina<T> pagina(List<T> filas, int numero) {
        int visibles = Math.min(TAMANIO_PAGINA, filas.size());
        return new Pagina<>(filas.subList(0, visibles), numero, filas.size() > visibles);
    }
}
