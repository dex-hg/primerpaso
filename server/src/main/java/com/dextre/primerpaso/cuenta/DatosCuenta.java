package com.dextre.primerpaso.cuenta;

import java.util.List;
import java.util.Locale;

import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.DatosVacantes.OpcionCatalogo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DatosCuenta {

    private DatosCuenta() {
    }

    public record SolicitudCuenta(
            @NotBlank @Size(max = 120) String nombres,
            @NotBlank @Size(max = 120) String apellidos,
            @NotBlank @Email @Size(max = 254) String correo,
            @Size(max = 30) String telefono,
            @Size(max = 120) String ciudad,
            @Size(max = 180) String institucion,
            Long idCarrera, String condicionAcademica, Integer cicloActual, Integer anioEgreso,
            @Size(max = 180) String nombreComercial,
            @Size(max = 100) String sector,
            @Size(max = 2000) String sitioWeb,
            @NotBlank @Size(max = 64) String version) {

        public SolicitudCuenta {
            nombres = normalizar(nombres);
            apellidos = normalizar(apellidos);
            correo = normalizar(correo);
            correo = correo == null ? null : correo.toLowerCase(Locale.ROOT);
            telefono = normalizar(telefono);
            ciudad = normalizar(ciudad);
            institucion = normalizar(institucion);
            condicionAcademica = normalizar(condicionAcademica);
            nombreComercial = normalizar(nombreComercial);
            sector = normalizar(sector);
            sitioWeb = normalizar(sitioWeb);
        }

        @Override
        public String toString() {
            return "SolicitudCuenta[datos privados]";
        }
    }

    public record DatosPersonales(String nombres, String apellidos, String correo, String telefono) {
    }

    public record Formacion(String ciudad, String institucion, long idCarrera, String nombreCarrera,
            String condicionAcademica, Integer cicloActual, Integer anioEgreso) {
    }

    public record Empresa(String nombreComercial, String sector, String ciudad, String sitioWeb,
            String codigoPais, String identificacionFiscal) {
    }

    public record Cuenta(UsuarioSesion usuario, DatosPersonales datos, Formacion formacion,
            Empresa empresa, String version, List<OpcionCatalogo> carreras) {

        public Cuenta {
            carreras = List.copyOf(carreras);
        }
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
