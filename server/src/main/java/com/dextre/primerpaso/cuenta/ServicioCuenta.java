package com.dextre.primerpaso.cuenta;

import java.net.URI;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.cuenta.DatosCuenta.Cuenta;
import com.dextre.primerpaso.cuenta.DatosCuenta.Empresa;
import com.dextre.primerpaso.cuenta.DatosCuenta.SolicitudCuenta;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

@Service
public class ServicioCuenta {

    private final RepositorioCuenta repositorio;

    public ServicioCuenta(RepositorioCuenta repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public Cuenta consultar(UsuarioSesion usuario) {
        Cuenta cuenta = exigirCuenta(usuario, false);
        return incluirCarreras(cuenta);
    }

    @Transactional
    public Cuenta actualizar(UsuarioSesion usuario, SolicitudCuenta datos) {
        Cuenta actual = exigirCuenta(usuario, true);
        if (datos == null || !Objects.equals(actual.version(), datos.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Los datos de tu cuenta cambiaron. Recarga la página antes de guardar.");
        }
        if (datos.nombres() == null || datos.apellidos() == null || datos.correo() == null) {
            throw invalido("Completa tus nombres, apellidos y correo.");
        }
        if (datos.telefono() != null && !datos.telefono().matches("(?=(?:\\D*\\d){6,20}\\D*$)\\+?[0-9 ().-]+")) {
            throw invalido("Ingresa un teléfono válido de 6 a 20 dígitos.");
        }
        if (actual.formacion() != null) {
            validarFormacion(actual, datos);
            repositorio.guardarPostulante(usuario.idUsuario(), datos);
        } else {
            validarEmpresa(actual, datos);
            repositorio.guardarMiembro(usuario.idUsuario(), usuario.idEmpresa(), datos);
            if ("administrador".equals(actual.usuario().rolEmpresa())) {
                repositorio.guardarEmpresa(usuario.idEmpresa(), datos);
            }
        }
        return incluirCarreras(exigirCuenta(usuario, false));
    }

    private Cuenta exigirCuenta(UsuarioSesion usuario, boolean bloquear) {
        if (usuario == null || usuario.idUsuario() <= 0
                || usuario.tipoCuenta() == null || !Set.of("empresa", "postulante").contains(usuario.tipoCuenta())
                || ("empresa".equals(usuario.tipoCuenta()) && usuario.idEmpresa() == null)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu cuenta no tiene acceso a esta operación.");
        }
        return repositorio.consultar(usuario, bloquear).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu cuenta o membresía ya no está activa."));
    }

    private Cuenta incluirCarreras(Cuenta cuenta) {
        return new Cuenta(cuenta.usuario(), cuenta.datos(), cuenta.formacion(), cuenta.empresa(), cuenta.version(),
                cuenta.formacion() == null ? List.of() : repositorio.carreras());
    }

    private void validarFormacion(Cuenta actual, SolicitudCuenta datos) {
        if (datos.institucion() == null || datos.idCarrera() == null || datos.idCarrera() <= 0
                || datos.condicionAcademica() == null
                || !Set.of("estudiante", "egresado", "titulado").contains(datos.condicionAcademica())) {
            throw invalido("Completa la institución, la carrera y tu condición académica.");
        }
        if ("estudiante".equals(datos.condicionAcademica())) {
            if (datos.cicloActual() == null || datos.cicloActual() < 1 || datos.cicloActual() > 30
                    || datos.anioEgreso() != null) {
                throw invalido("Indica un ciclo entre 1 y 30 y deja vacío el año de egreso.");
            }
        } else if (datos.anioEgreso() == null || datos.anioEgreso() < 1950
                || datos.anioEgreso() > Year.now(ZoneId.of("America/Lima")).getValue()
                || datos.cicloActual() != null) {
            throw invalido("Indica un año de egreso entre 1950 y el año actual y deja vacío el ciclo actual.");
        }
        if (!repositorio.carreraActiva(datos.idCarrera()) && datos.idCarrera() != actual.formacion().idCarrera()) {
            throw invalido("La carrera seleccionada ya no está disponible.");
        }
        if (datos.nombreComercial() != null || datos.sector() != null || datos.sitioWeb() != null) {
            throw invalido("Los datos empresariales no corresponden a una cuenta de postulante.");
        }
    }

    private void validarEmpresa(Cuenta actual, SolicitudCuenta datos) {
        if (datos.institucion() != null || datos.idCarrera() != null || datos.condicionAcademica() != null
                || datos.cicloActual() != null || datos.anioEgreso() != null) {
            throw invalido("La formación académica no corresponde a una cuenta empresarial.");
        }
        if (!"administrador".equals(actual.usuario().rolEmpresa())) {
            Empresa empresa = actual.empresa();
            if (!Objects.equals(empresa.nombreComercial(), datos.nombreComercial())
                    || !Objects.equals(empresa.sector(), datos.sector())
                    || !Objects.equals(empresa.ciudad(), datos.ciudad())
                    || !Objects.equals(empresa.sitioWeb(), datos.sitioWeb())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Solo un administrador puede modificar los datos de la empresa.");
            }
            return;
        }
        if (datos.nombreComercial() == null || datos.sector() == null || datos.ciudad() == null) {
            throw invalido("Completa el nombre comercial, el sector y la ciudad de la empresa.");
        }
        if (datos.sitioWeb() != null) {
            try {
                URI direccion = URI.create(datos.sitioWeb());
                if (direccion.getScheme() == null || !Set.of("http", "https").contains(direccion.getScheme().toLowerCase(Locale.ROOT))
                        || direccion.getHost() == null || direccion.getUserInfo() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException excepcion) {
                throw invalido("El sitio web debe ser una dirección http o https válida.");
            }
        }
    }

    private ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
