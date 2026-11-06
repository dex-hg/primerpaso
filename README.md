# PrimerPaso

Plataforma web que conecta a estudiantes, egresados y jóvenes profesionales con oportunidades laborales. Incluye espacios para postulantes y empresas.

## Funciones

- Registro, inicio de sesión y edición de cuenta con permisos por tipo de usuario.
- Búsqueda de vacantes, envío de cartas de presentación y seguimiento de postulaciones.
- Creación, publicación y cierre de vacantes; revisión de postulaciones por empresa.
- Chat iniciado por la empresa, con historial y bandeja de conversaciones.
- Ofertas, contratación por plazas y cierre de la convocatoria.
- Bandeja privada de notificaciones e historial personal desde Mi cuenta.
- Vistas adaptables a móviles con temas oscuro, claro y sistema. El tema oscuro es el inicial y la elección se recuerda en el navegador.

## Tecnologías

Java 21, Spring Boot, Thymeleaf, PostgreSQL, Bootstrap y JavaScript. El servidor entrega las vistas y la API desde una misma aplicación.

## Ejecución local

Requisitos: JDK 21 o superior y PostgreSQL.

1. Para una base nueva, crear `PrimerPaso` y ejecutar [db/primerpaso.sql](db/primerpaso.sql). No ejecutar este script sobre una base existente.
   Para una base existente, aplica las migraciones pendientes. El orden es [V001 de chat](db/migraciones/V001__mensajes_chat.sql), [V002 de contratación](db/migraciones/V002__contratacion.sql), [V003 de notificaciones](db/migraciones/V003__lecturas_notificaciones.sql) y [V004 de seguridad](db/migraciones/V004__limites_inicio_sesion.sql).
2. Copiar [server/.env.example](server/.env.example) como `server/.env` y completar la conexión. Este archivo está excluido de Git.
3. Desde la raíz del proyecto, ejecutar:

```powershell
cd server
.\mvnw.cmd spring-boot:run
```

Abrir [localhost:8080](http://localhost:8080). Las vistas requieren Spring; no se ejecutan con Live Server.

## Pruebas

Desde `server` (las pruebas JavaScript requieren Node.js):

```powershell
.\mvnw.cmd test
node --test src/test/js/*.test.cjs
```

Consulta la [documentación técnica y contribuciones](server/README.md) y las [cuentas de demostración](db/README-demostracion.md).
La [bandeja y el historial](server/README.md#notificaciones-e-historial) se consultan desde la navegación privada.
Para alojar PostgreSQL en Supabase, sigue la [guía de seguridad, migración y conexión](db/supabase/README.md).
Para publicar el backend y las vistas Thymeleaf, consulta la [guía de despliegue en Render](server/DESPLIEGUE-RENDER.md).

Las cifras y oportunidades del inicio son demostrativas. La carga de CV, el acceso social, la recuperación de contraseña y el envío de contacto y suscripciones siguen pendientes.

Licencia [MIT](LICENSE.txt).
