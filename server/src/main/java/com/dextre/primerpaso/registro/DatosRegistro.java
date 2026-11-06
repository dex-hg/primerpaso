package com.dextre.primerpaso.registro;

import java.util.List;
import java.util.Locale;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class DatosRegistro {

    static final int MINIMO_CONTRASENA = 15;
    static final int MAXIMO_CONTRASENA = 128;
    static final String MENSAJE_CONTRASENA = "La contraseña debe tener entre 15 y 128 caracteres.";

    private DatosRegistro() {
    }

    public record SolicitudPostulante(
            @NotBlank @Size(max = 120) String nombres,
            @NotBlank @Size(max = 120) String apellidos,
            @NotBlank @Email @Size(max = 254) String correo,
            @NotBlank @Size(min = MINIMO_CONTRASENA, max = MAXIMO_CONTRASENA) String contrasena,
            @NotNull @AssertTrue(message = "Debes aceptar los términos para crear tu cuenta.") Boolean aceptaTerminos,
            @NotBlank @Size(max = 180) String institucion,
            @NotBlank @Size(max = 160) String carrera,
            @NotBlank @Pattern(regexp = "estudiante|egresado|titulado") String condicionAcademica,
            Integer cicloActual,
            Integer anioEgreso,
            @Size(max = 6) List<@NotBlank @Size(max = 32) String> habilidades,
            @Size(max = 9) List<@NotBlank @Size(max = 32) String> intereses,
            @NotNull Boolean aceptaComunicaciones) {

        public SolicitudPostulante {
            nombres = normalizarTexto(nombres);
            apellidos = normalizarTexto(apellidos);
            correo = normalizarCorreo(correo);
            institucion = normalizarTexto(institucion);
            carrera = normalizarTexto(carrera);
            condicionAcademica = condicionAcademica == null ? null
                    : condicionAcademica.strip().toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return "SolicitudPostulante[datos privados]";
        }
    }

    public record SolicitudEmpresa(
            @NotBlank @Size(max = 120) String nombres,
            @NotBlank @Size(max = 120) String apellidos,
            @NotBlank @Email @Size(max = 254) String correo,
            @NotBlank @Size(min = MINIMO_CONTRASENA, max = MAXIMO_CONTRASENA) String contrasena,
            @NotNull @AssertTrue(message = "Debes aceptar los términos para crear tu cuenta.") Boolean aceptaTerminos,
            @NotBlank @Size(max = 180) String nombreComercial,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String codigoPais,
            @NotBlank @Size(max = 32) String identificacionFiscal,
            @NotBlank @Size(max = 32) String sector,
            @NotBlank @Size(max = 120) String ciudad,
            @Size(max = 2000) String sitioWeb,
            @NotBlank @Size(max = 30)
            @Pattern(regexp = "(?=(?:\\D*\\d){6,20}\\D*$)\\+?[0-9 ().-]+") String telefono,
            @Size(max = 9) List<@NotBlank @Size(max = 32) String> intereses) {

        public SolicitudEmpresa {
            nombres = normalizarTexto(nombres);
            apellidos = normalizarTexto(apellidos);
            correo = normalizarCorreo(correo);
            nombreComercial = normalizarTexto(nombreComercial);
            codigoPais = codigoPais == null ? null : codigoPais.strip().toUpperCase(Locale.ROOT);
            identificacionFiscal = normalizarTexto(identificacionFiscal);
            sector = normalizarTexto(sector);
            ciudad = normalizarTexto(ciudad);
            sitioWeb = normalizarTexto(sitioWeb);
            telefono = normalizarTexto(telefono);
        }

        @Override
        public String toString() {
            return "SolicitudEmpresa[datos privados]";
        }
    }

    public record RespuestaRegistro(long idUsuario, String tipoCuenta, String mensaje) {
    }

    private static String normalizarTexto(String texto) {
        return texto == null ? null : texto.strip();
    }

    private static String normalizarCorreo(String correo) {
        return correo == null ? null : correo.strip().toLowerCase(Locale.ROOT);
    }
}
