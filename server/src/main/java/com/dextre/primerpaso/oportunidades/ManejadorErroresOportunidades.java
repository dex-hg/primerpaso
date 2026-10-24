package com.dextre.primerpaso.oportunidades;

import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice(assignableTypes = ControladorOportunidades.class)
public class ManejadorErroresOportunidades {

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView manejarRegla(ResponseStatusException excepcion, HttpServletRequest solicitud,
            HttpServletResponse respuesta) {
        respuesta.setStatus(excepcion.getStatusCode().value());
        return crearError(excepcion.getReason(), solicitud);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView manejarParametros(HttpServletRequest solicitud, HttpServletResponse respuesta) {
        respuesta.setStatus(400);
        return crearError("Revisa los filtros y el número de página.", solicitud);
    }

    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    public ModelAndView manejarServicio(HttpServletResponse respuesta) {
        respuesta.setStatus(503);
        return new ModelAndView("error/503");
    }

    private ModelAndView crearError(String mensaje, HttpServletRequest solicitud) {
        var vista = new ModelAndView("oportunidades/error", "mensaje", mensaje);
        vista.addObject("usuario", solicitud.getAttribute("usuarioSesion"));
        return vista;
    }
}
