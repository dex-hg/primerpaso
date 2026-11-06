package com.dextre.primerpaso.seguridad;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/** Entrega a Spring MVC el cuerpo ya leído dentro del límite de bytes. */
final class CuerpoApi extends HttpServletRequestWrapper {

    private final ServletInputStream entrada;

    CuerpoApi(HttpServletRequest solicitud, byte[] cuerpo) {
        super(solicitud);
        ByteArrayInputStream bytes = new ByteArrayInputStream(cuerpo);
        entrada = new ServletInputStream() {
            @Override
            public int read() {
                return bytes.read();
            }

            @Override
            public int read(byte[] destino, int inicio, int longitud) {
                return bytes.read(destino, inicio, longitud);
            }

            @Override
            public boolean isFinished() {
                return bytes.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener lector) {
                throw new IllegalStateException("El cuerpo de la API usa lectura síncrona.");
            }
        };
    }

    @Override
    public ServletInputStream getInputStream() {
        return entrada;
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
    }
}
