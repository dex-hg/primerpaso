package com.dextre.primerpaso.postulaciones;

import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = ControladorPostulaciones.class)
public class ManejadorErroresPostulaciones {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorPostulacion> manejarRegla(ResponseStatusException excepcion) {
        return responder(excepcion.getStatusCode().value(), excepcion.getReason() == null
                ? "No se pudo completar la solicitud." : excepcion.getReason());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorPostulacion> manejarDatos() {
        return responder(400, "Revisa los campos, sus longitudes y los parámetros de la solicitud.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorPostulacion> manejarIntegridad() {
        return responder(409, "Los datos cambiaron. Revisa la postulación antes de volver a intentarlo.");
    }

    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    public ResponseEntity<ErrorPostulacion> manejarServicio() {
        return responder(503, "No se pudo completar la operación. Inténtalo nuevamente más tarde.");
    }

    private ResponseEntity<ErrorPostulacion> responder(int estado, String mensaje) {
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
                .body(new ErrorPostulacion(mensaje, Map.of()));
    }

    public record ErrorPostulacion(String mensaje, Map<String, String> errores) {
    }
}
