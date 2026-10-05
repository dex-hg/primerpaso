package com.dextre.primerpaso.vacantes;

import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice(assignableTypes = ControladorVistasVacantes.class)
public class ManejadorErroresVistasVacantes {

    @ExceptionHandler(ReglaVacanteException.class)
    public ModelAndView manejarRegla(ReglaVacanteException excepcion, HttpServletResponse respuesta) {
        respuesta.setStatus(excepcion.estado());
        if (excepcion.estado() == 404) return new ModelAndView("error/404");
        if (excepcion.estado() == 403) return new ModelAndView("error/403");
        return new ModelAndView("vacantes/error", "mensaje", excepcion.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView manejarParametros(HttpServletResponse respuesta) {
        respuesta.setStatus(400);
        return new ModelAndView("vacantes/error", "mensaje", "Revisa el número de página y los parámetros de búsqueda.");
    }

    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    public ModelAndView manejarServicio(HttpServletResponse respuesta) {
        respuesta.setStatus(503);
        return new ModelAndView("error/503");
    }
}
