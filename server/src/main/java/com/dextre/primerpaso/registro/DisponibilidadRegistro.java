package com.dextre.primerpaso.registro;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Permite abrir cada tipo de alta de forma independiente. */
@Component
public class DisponibilidadRegistro {

    private final boolean postulantes;
    private final boolean empresas;

    public DisponibilidadRegistro(@Value("${primerpaso.registro.postulantes:true}") boolean postulantes,
            @Value("${primerpaso.registro.empresas:true}") boolean empresas) {
        this.postulantes = postulantes;
        this.empresas = empresas;
    }

    public boolean permitePostulantes() {
        return postulantes;
    }

    public boolean permiteEmpresas() {
        return empresas;
    }
}
