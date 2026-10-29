package com.dextre.primerpaso.postulaciones;

import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice(assignableTypes = ControladorVistasPostulaciones.class)
public class ManejadorErroresVistasPostulaciones {

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView manejarRegla(ResponseStatusException excepcion, HttpServletResponse respuesta) {
        int estado = excepcion.getStatusCode().value();
        respuesta.setStatus(estado);
        if (estado == 403 || estado == 404) return new ModelAndView("error/" + estado);
        return new ModelAndView("postulaciones/error", "mensaje", excepcion.getReason());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView manejarParametros(HttpServletResponse respuesta) {
        respuesta.setStatus(400);
        return new ModelAndView("postulaciones/error", "mensaje", "Revisa los filtros y el número de página.");
    }

    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    public ModelAndView manejarServicio(HttpServletResponse respuesta) {
        respuesta.setStatus(503);
        return new ModelAndView("error/503");
    }
}
