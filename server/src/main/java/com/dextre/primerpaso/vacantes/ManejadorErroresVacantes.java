package com.dextre.primerpaso.vacantes;

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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = ControladorVacantes.class)
public class ManejadorErroresVacantes {

    @ExceptionHandler(ReglaVacanteException.class)
    public ResponseEntity<ErrorVacante> manejarRegla(ReglaVacanteException excepcion) {
        return responder(excepcion.estado(), excepcion.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorVacante> manejarValidacion(MethodArgumentNotValidException excepcion) {
        Map<String, String> errores = new LinkedHashMap<>();
        excepcion.getBindingResult().getFieldErrors().forEach(error ->
                errores.putIfAbsent(error.getField(), "Revisa el valor y la longitud de este campo."));
        return responder(400, "Revisa los campos indicados.", errores);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorVacante> manejarContenido() {
        return responder(400, "El contenido o los parámetros de la solicitud no son válidos.", Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorVacante> manejarProteccion() {
        return responder(403, "La solicitud no es válida. Recarga la página e inténtalo nuevamente.", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorVacante> manejarIntegridad() {
        return responder(409, "Los datos cambiaron o no cumplen las reglas de la vacante. Recarga y revisa los campos.", Map.of());
    }

    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    public ResponseEntity<ErrorVacante> manejarServicio() {
        return responder(HttpStatus.SERVICE_UNAVAILABLE.value(),
                "No se pudo completar la operación. Inténtalo nuevamente más tarde.", Map.of());
    }

    private ResponseEntity<ErrorVacante> responder(int estado, String mensaje, Map<String, String> errores) {
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
                .body(new ErrorVacante(mensaje, errores));
    }

    public record ErrorVacante(String mensaje, Map<String, String> errores) {
    }
}
