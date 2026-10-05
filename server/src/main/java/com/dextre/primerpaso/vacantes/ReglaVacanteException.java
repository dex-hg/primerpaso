package com.dextre.primerpaso.vacantes;

public class ReglaVacanteException extends RuntimeException {

    private final int estado;

    public ReglaVacanteException(int estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public int estado() {
        return estado;
    }
}
