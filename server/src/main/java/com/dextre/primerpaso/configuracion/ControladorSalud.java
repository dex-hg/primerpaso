package com.dextre.primerpaso.configuracion;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Render comprueba la disponibilidad sin recibir detalles internos ni abrir conexiones adicionales. */
@RestController
class ControladorSalud {

    private final ApplicationAvailability disponibilidad;

    ControladorSalud(ApplicationAvailability disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    @GetMapping("/salud")
    ResponseEntity<Void> comprobarDisponibilidad() {
        HttpStatus estado = disponibilidad.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC
                ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore()).build();
    }
}
