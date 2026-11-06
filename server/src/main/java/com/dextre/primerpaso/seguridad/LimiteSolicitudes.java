package com.dextre.primerpaso.seguridad;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Control rápido en memoria para una instancia. Limita también los intentos
 * inválidos, antes de acceder a PostgreSQL o reservar una sesión.
 */
@Component
class LimiteSolicitudes {

    private static final int MAXIMO_ORIGENES = 4_096;
    private final Map<Clave, Ventana> origenes = new HashMap<>();
    private final Map<Operacion, Ventana> globales = new EnumMap<>(Operacion.class);
    private final Clock reloj;
    private Instant limpiarEn = Instant.EPOCH;

    LimiteSolicitudes() {
        this(Clock.systemUTC());
    }

    LimiteSolicitudes(Clock reloj) {
        this.reloj = reloj;
    }

    /** Devuelve 0 al admitir o los segundos de espera al rechazar. */
    synchronized int consumir(Operacion operacion, String origen) {
        Instant ahora = reloj.instant();
        if (!limpiarEn.isAfter(ahora)) {
            origenes.values().removeIf(ventana -> !ventana.venceEn.isAfter(ahora));
            limpiarEn = ahora.plusSeconds(60);
        }
        Clave clave = new Clave(operacion, origen);
        Ventana porOrigen = origenes.get(clave);
        Ventana global = globales.get(operacion);
        int espera = Math.max(espera(porOrigen, operacion.maximoOrigen, ahora),
                espera(global, operacion.maximoGlobal, ahora));
        if (espera > 0) {
            return espera;
        }
        if (porOrigen == null && origenes.size() >= MAXIMO_ORIGENES) {
            return 60;
        }
        origenes.put(clave, consumir(porOrigen, operacion.segundosOrigen, ahora));
        globales.put(operacion, consumir(global, 60, ahora));
        return 0;
    }

    private int espera(Ventana ventana, int maximo, Instant ahora) {
        if (ventana == null || ventana.usadas < maximo || !ventana.venceEn.isAfter(ahora)) {
            return 0;
        }
        return Math.max(1, (int) (ventana.venceEn.getEpochSecond() - ahora.getEpochSecond()
                + (ventana.venceEn.getNano() > ahora.getNano() ? 1 : 0)));
    }

    private Ventana consumir(Ventana ventana, int segundos, Instant ahora) {
        return ventana == null || !ventana.venceEn.isAfter(ahora)
                ? new Ventana(1, ahora.plusSeconds(segundos))
                : new Ventana(ventana.usadas + 1, ventana.venceEn);
    }

    enum Operacion {
        INICIO_SESION(25, 900, 90),
        REGISTRO(6, 900, 30),
        TOKEN_CSRF(60, 60, 300);

        private final int maximoOrigen;
        private final int segundosOrigen;
        private final int maximoGlobal;

        Operacion(int maximoOrigen, int segundosOrigen, int maximoGlobal) {
            this.maximoOrigen = maximoOrigen;
            this.segundosOrigen = segundosOrigen;
            this.maximoGlobal = maximoGlobal;
        }
    }

    private record Clave(Operacion operacion, String origen) {
    }

    private record Ventana(int usadas, Instant venceEn) {
    }
}
