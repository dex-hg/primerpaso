package com.dextre.primerpaso.sesion;

import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.seguridad.LimiteInicioSesion.DemasiadosIntentosException;

@RestControllerAdvice(assignableTypes = ControladorSesion.class)
public class ManejadorErroresSesion {

    @ExceptionHandler(AccesoNoAutorizadoException.class)
    public ResponseEntity<ErrorSesion> manejarAcceso(AccesoNoAutorizadoException excepcion) {
        return responder(HttpStatus.UNAUTHORIZED, excepcion.getMessage());
    }

    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<ErrorSesion> manejarLimite(DemasiadosIntentosException excepcion) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.RETRY_AFTER, Integer.toString(excepcion.segundos()))
                .body(new ErrorSesion("Demasiados intentos. Espera antes de volver a iniciar sesión."));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorSesion> manejarDatos() {
        return responder(HttpStatus.BAD_REQUEST, "Revisa el correo, la contraseña y el tipo de cuenta.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorSesion> manejarProteccion() {
        return responder(HttpStatus.FORBIDDEN,
                "La solicitud de sesión no es válida. Recarga la página e inténtalo nuevamente.");
    }

    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    public ResponseEntity<ErrorSesion> manejarServicio() {
        return responder(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo acceder al servicio. Inténtalo nuevamente más tarde.");
    }

    private ResponseEntity<ErrorSesion> responder(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore()).body(new ErrorSesion(mensaje));
    }

    public record ErrorSesion(String mensaje) {
    }
}
