package com.dextre.primerpaso.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reserva un intento por correo antes de comprobar la contraseña. PostgreSQL serializa
 * las reservas simultáneas para la misma clave y conserva los fallos entre reinicios.
 */
@Component
public class LimiteInicioSesion {

    private static final int MAXIMO_INTENTOS = 5;
    private static final int MINUTOS_VENTANA = 15;
    private static final String ALGORITMO = "HmacSHA256";
    private static final Logger REGISTRO = LoggerFactory.getLogger(LimiteInicioSesion.class);
    private final JdbcClient jdbc;
    private final SecretKeySpec secreto;

    public LimiteInicioSesion(JdbcClient jdbc, @Value("${primerpaso.seguridad.clave:}") String clave,
            @Value("${primerpaso.seguridad.exigir-clave:false}") boolean exigirClave) {
        this.jdbc = jdbc;
        this.secreto = new SecretKeySpec(leerClave(clave, exigirClave), ALGORITMO);
    }

    public Reserva reservar(String correo) {
        byte[] clave = identificarCorreo(correo);
        OffsetDateTime vencimiento = jdbc.sql("""
                INSERT INTO limites_inicio_sesion (clave, intentos, vence_en)
                VALUES (:clave, 1, statement_timestamp() + make_interval(mins => :minutos))
                ON CONFLICT (clave) DO UPDATE SET
                    intentos = CASE WHEN limites_inicio_sesion.vence_en <= statement_timestamp()
                        THEN 1 ELSE limites_inicio_sesion.intentos + 1 END,
                    vence_en = CASE WHEN limites_inicio_sesion.vence_en <= statement_timestamp()
                        THEN EXCLUDED.vence_en ELSE limites_inicio_sesion.vence_en END
                WHERE limites_inicio_sesion.vence_en <= statement_timestamp()
                    OR limites_inicio_sesion.intentos < :maximo
                RETURNING vence_en
                """).param("clave", clave).param("minutos", MINUTOS_VENTANA)
                .param("maximo", MAXIMO_INTENTOS).query(OffsetDateTime.class).optional().orElse(null);
        if (vencimiento != null) {
            return new Reserva(clave, vencimiento);
        }
        int segundos = jdbc.sql("""
                SELECT greatest(1, ceil(extract(epoch FROM vence_en - statement_timestamp())))::integer
                FROM limites_inicio_sesion WHERE clave = :clave
                """).param("clave", clave).query(Integer.class).optional().orElse(1);
        throw new DemasiadosIntentosException(segundos);
    }

    public void liberar(Reserva reserva) {
        // Una autenticación correcta devuelve solo su propio cupo, incluso si hay otras en curso.
        jdbc.sql("""
                UPDATE limites_inicio_sesion SET intentos = intentos - 1
                WHERE clave = :clave AND vence_en = :vencimiento AND intentos > 0
                """).param("clave", reserva.clave).param("vencimiento", reserva.vencimiento).update();
    }

    @Scheduled(initialDelay = 15, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    void eliminarVencidos() {
        try {
            jdbc.sql("DELETE FROM limites_inicio_sesion WHERE vence_en <= statement_timestamp()").update();
        } catch (DataAccessException excepcion) {
            REGISTRO.warn("No se pudieron limpiar los contadores de acceso. Se reintentará en la próxima ejecución.");
        }
    }

    private byte[] identificarCorreo(String correo) {
        try {
            Mac hmac = Mac.getInstance(ALGORITMO);
            hmac.init(secreto);
            return hmac.doFinal(correo.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException excepcion) {
            throw new IllegalStateException("No se pudo comprobar el límite de acceso.", excepcion);
        }
    }

    private static byte[] leerClave(String clave, boolean exigirClave) {
        if (clave.isBlank() && !exigirClave) {
            byte[] aleatoria = new byte[32];
            new SecureRandom().nextBytes(aleatoria);
            return aleatoria;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(clave.strip());
            if (bytes.length == 32) {
                return bytes;
            }
        } catch (IllegalArgumentException excepcion) {
            // Se devuelve un error fijo para que los registros no incluyan el secreto.
        }
        throw new IllegalStateException("Configura PRIMERPASO_SEGURIDAD_CLAVE con 32 bytes aleatorios en Base64.");
    }

    public static final class Reserva {
        private final byte[] clave;
        private final OffsetDateTime vencimiento;

        private Reserva(byte[] clave, OffsetDateTime vencimiento) {
            this.clave = clave;
            this.vencimiento = vencimiento;
        }
    }

    public static final class DemasiadosIntentosException extends RuntimeException {
        private final int segundos;

        DemasiadosIntentosException(int segundos) {
            this.segundos = segundos;
        }

        public int segundos() {
            return segundos;
        }
    }
}
