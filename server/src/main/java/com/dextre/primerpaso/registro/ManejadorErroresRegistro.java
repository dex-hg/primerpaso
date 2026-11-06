package com.dextre.primerpaso.registro;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = ControladorRegistro.class)
public class ManejadorErroresRegistro {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorRegistro> manejarDisponibilidad() {
        return responder(HttpStatus.FORBIDDEN, "El registro de este tipo de cuenta está cerrado.", Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRegistro> manejarValidacion(MethodArgumentNotValidException excepcion) {
        Map<String, String> errores = new LinkedHashMap<>();
        excepcion.getBindingResult().getFieldErrors().forEach(error ->
                errores.putIfAbsent(error.getField(), mensajeCampo(error.getField())));
        return responder(HttpStatus.BAD_REQUEST, "Revisa los campos indicados.", errores);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRegistro> manejarJson() {
        return responder(HttpStatus.BAD_REQUEST, "El contenido del registro no es válido.", Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorRegistro> manejarDatos(IllegalArgumentException excepcion) {
        return responder(HttpStatus.BAD_REQUEST, excepcion.getMessage(), Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorRegistro> manejarRestricciones(DataIntegrityViolationException excepcion) {
        Throwable causa = excepcion;
        while (causa != null) {
            if (causa instanceof SQLException errorSql && "23505".equals(errorSql.getSQLState())) {
                return responder(HttpStatus.CONFLICT,
                        "Ya existe una cuenta con ese correo o una empresa con esa identificación fiscal.", Map.of());
            }
            causa = causa.getCause();
        }
        return responder(HttpStatus.BAD_REQUEST,
                "Los datos no cumplen los requisitos del registro.", Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorRegistro> manejarConexion() {
        return responder(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo acceder a la base de datos. Inténtalo nuevamente más tarde.", Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorRegistro> manejarServicio() {
        return responder(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo completar el registro. Inténtalo nuevamente más tarde.", Map.of());
    }

    private String mensajeCampo(String campo) {
        return switch (campo) {
            case "correo" -> "Ingresa un correo válido de hasta 254 caracteres.";
            case "contrasena" -> DatosRegistro.MENSAJE_CONTRASENA;
            case "aceptaTerminos" -> "Debes aceptar los términos para crear tu cuenta.";
            default -> "Completa este campo con un valor válido y respeta su longitud máxima.";
        };
    }

    private ResponseEntity<ErrorRegistro> responder(HttpStatus estado, String mensaje,
            Map<String, String> errores) {
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
                .body(new ErrorRegistro(mensaje, errores));
    }

    public record ErrorRegistro(String mensaje, Map<String, String> errores) {
    }
}
