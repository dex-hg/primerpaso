package com.dextre.primerpaso.cuenta;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = ControladorCuenta.class)
public class ManejadorErroresCuenta {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorCuenta> manejarRegla(ResponseStatusException excepcion) {
        return responder(excepcion.getStatusCode().value(), excepcion.getReason(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorCuenta> manejarValidacion(MethodArgumentNotValidException excepcion) {
        Map<String, String> errores = new LinkedHashMap<>();
        excepcion.getBindingResult().getFieldErrors().forEach(error ->
                errores.putIfAbsent(error.getField(), "Revisa el valor y la longitud de este campo."));
        return responder(400, "Revisa los campos indicados.", errores);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorCuenta> manejarContenido() {
        return responder(400, "Los datos enviados no son válidos.", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorCuenta> manejarIntegridad() {
        return responder(409, "El correo ya está registrado o los datos cambiaron. Revisa tu información.", Map.of());
    }

    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    public ResponseEntity<ErrorCuenta> manejarServicio() {
        return responder(503, "No se pudo guardar tu cuenta. Inténtalo nuevamente más tarde.", Map.of());
    }

    private ResponseEntity<ErrorCuenta> responder(int estado, String mensaje, Map<String, String> errores) {
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
                .body(new ErrorCuenta(mensaje, errores));
    }

    public record ErrorCuenta(String mensaje, Map<String, String> errores) {
    }
}
