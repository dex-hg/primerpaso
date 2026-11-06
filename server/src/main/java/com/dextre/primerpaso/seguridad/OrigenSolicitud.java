package com.dextre.primerpaso.seguridad;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
class OrigenSolicitud {

    private static final String SIN_ORIGEN = "sin-ip";
    private static final Pattern IPV4 = Pattern.compile("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]{2,45}");
    private final boolean proxyRender;

    OrigenSolicitud(@Value("${primerpaso.seguridad.proxy-render:false}") boolean proxyRender) {
        this.proxyRender = proxyRender;
    }

    String identificar(HttpServletRequest solicitud) {
        String direccion = proxyRender ? leerCabeceraRender(solicitud) : solicitud.getRemoteAddr();
        if (direccion == null || direccion.length() > 45) {
            return SIN_ORIGEN;
        }
        if (IPV4.matcher(direccion).matches()) {
            byte[] bytes = new byte[4];
            String[] segmentos = direccion.split("\\.");
            for (int indice = 0; indice < segmentos.length; indice++) {
                int valor = Integer.parseInt(segmentos[indice]);
                if (valor > 255) {
                    return SIN_ORIGEN;
                }
                bytes[indice] = (byte) valor;
            }
            return HexFormat.of().formatHex(bytes);
        }
        if (direccion.indexOf(':') < 0 || !IPV6.matcher(direccion).matches()) {
            return SIN_ORIGEN;
        }
        try {
            // Solo se admiten literales. No se resuelven nombres ni se aceptan listas o puertos.
            byte[] bytes = InetAddress.getByName(direccion).getAddress();
            return HexFormat.of().formatHex(bytes.length == 16 ? Arrays.copyOf(bytes, 8) : bytes);
        } catch (UnknownHostException excepcion) {
            return SIN_ORIGEN;
        }
    }

    private String leerCabeceraRender(HttpServletRequest solicitud) {
        // En el Web Service público, Cloudflare sobrescribe esta cabecera. Si no llega
        // un único valor válido, la solicitud comparte un cupo sin IP verificable.
        Enumeration<String> valores = solicitud.getHeaders("CF-Connecting-IP");
        if (!valores.hasMoreElements()) {
            return null;
        }
        String direccion = valores.nextElement();
        return valores.hasMoreElements() ? null : direccion.strip();
    }
}
