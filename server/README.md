# PrimerPaso

El registro y el inicio de sesión de postulantes y empresas están conectados a PostgreSQL. Las páginas se renderizan con Thymeleaf desde `src/main/resources/templates`; CSS, JavaScript e imágenes están en `src/main/resources/static`. Spring sirve la aplicación desde `http://localhost:8080`. Estos recursos también se incluyen en el JAR.

## Ejecución local

Requisitos: Java 21 o superior, PostgreSQL y la base de datos `PrimerPaso` con las tablas del proyecto.

1. Copiar `server/.env.example` como `server/.env` y completar usuario y contraseña. El archivo usa formato Java properties: los valores no llevan comillas; si una contraseña contiene una barra invertida, escribirla como `\\`.
2. Para una base nueva, ejecutar `db/primerpaso.sql` desde la raíz del proyecto una sola vez. La migración a Thymeleaf no modifica el esquema. Para una base existente, comprobar las tres columnas de datos del reclutador en `company_members`: `first_name_company_member`, `last_name_company_member` y `phone_company_member`. El SQL actual las incluye; el servidor comprueba su presencia al arrancar.
3. Abrir una terminal **dentro de `server`** y ejecutar:

```powershell
.\mvnw.cmd spring-boot:run
```

Abrir `http://localhost:8080`. No es necesario iniciar un segundo servidor para el frontend.

`.env` está excluido de Git. `.env.example` contiene únicamente valores de ejemplo. La aplicación comprueba la conexión y las tablas al arrancar; no crea ni modifica el esquema automáticamente.

Las plantillas requieren Spring y no se pueden abrir directamente con Live Server. La configuración CORS de la API conserva los orígenes locales de los puertos 5500, 4173 y 8000 para clientes externos; se pueden cambiar mediante `PRIMERPASO_CORS_ORIGENES`.

## Páginas y contribución

Las rutas públicas son `/`, `/iniciar-sesion`, `/registro`, `/registro-postulante`, `/registro-empresa` y `/contacto`. Las rutas `/panel`, `/panel/postulante`, `/panel/empresa` y `/cuenta` requieren una sesión válida. Los enlaces anteriores a `/index.html` y `/html/*.html` redirigen a sus rutas equivalentes; el alias de cuenta también exige autenticación. Una página pública inexistente muestra la plantilla de error 404.

Mallqui Liberato Yefrit realizó la migración de las vistas a Thymeleaf, su organización en `templates` y `static`, los fragmentos compartidos de cabecera y pie, las categorías del inicio renderizadas desde el modelo y las páginas de contacto y error 404. La integración conserva su aporte y lo combina con el registro y las sesiones existentes.

Las categorías, cifras y oportunidades del inicio siguen siendo datos de demostración. El formulario de contacto valida los campos, pero no envía ni guarda mensajes; su información de contacto está por confirmar. La suscripción del pie también sigue pendiente.

## Registro disponible

`POST /api/registro/postulantes` recibe nombres, apellidos, correo, contraseña, aceptación de términos, institución, carrera, condición académica, ciclo o año de egreso, habilidades, intereses y consentimiento de comunicaciones. Un estudiante debe indicar un ciclo entre 1 y 30; un egresado o titulado, un año entre 1950 y el año actual.

`POST /api/registro/empresas` recibe los datos de la empresa y del reclutador, país, identificación fiscal, sector, ciudad, sitio web opcional e intereses. Para Perú se exige un RUC de 11 dígitos. La cuenta del reclutador recibe la membresía de administrador de la empresa registrada.

La cuenta y su perfil, membresía y preferencias se guardan en una transacción. Los correos se normalizan a minúsculas; las contraseñas conservan sus caracteres y se almacenan con PBKDF2-HMAC-SHA-256, sal aleatoria de 16 bytes y 600 000 iteraciones. La API devuelve `201` al crear, `400` para datos inválidos, `409` para correo o identificación fiscal duplicados y `503` cuando el servicio no puede completar la operación.

Las carreras escritas en el formulario y las opciones seleccionadas se resuelven en sus catálogos sin duplicar nombres. Un catálogo inactivo impide el registro y revierte la transacción. `terms_version_user` usa `registro-v1` para identificar esta versión del formulario; el contenido legal de los enlaces de términos sigue pendiente.

Después del registro se puede acceder desde `/iniciar-sesion`, eligiendo el tipo de cuenta registrado. El registro no inicia una sesión automáticamente. La carga de CV, la publicación de la primera vacante, el acceso social, la recuperación de contraseña y el acceso persistente con «Recordarme» siguen pendientes.

## Inicio y cierre de sesión

El inicio comprueba el hash de la contraseña y el estado activo de la cuenta. Un postulante debe tener su perfil; una empresa requiere una membresía activa en una empresa activa. Las credenciales incorrectas, el tipo equivocado y las cuentas no habilitadas devuelven el mismo mensaje con estado `401`.

1. `GET /api/sesion/seguridad` obtiene `tokenCsrf` y una cookie de sesión previa al acceso.
2. `POST /api/sesion` recibe `correo`, `contrasena` y `tipoCuenta` (`postulante` o `empresa`) en JSON. Requiere esa cookie y la cabecera `X-CSRF-Token`. Al autenticar devuelve `200`, cambia el identificador de sesión y renueva el token.
3. `GET /api/sesion` devuelve los datos públicos de la cuenta y, cuando corresponde, la empresa y el rol. Revalida los permisos contra PostgreSQL en cada consulta. Una sesión ausente, vencida o revocada devuelve `401`.
4. `DELETE /api/sesion` requiere la cookie y el token actual, obtenido nuevamente de `/seguridad`. Invalida la sesión, borra la cookie y devuelve `204`.

Después de iniciar sesión, el navegador abre `/panel`. El servidor consulta el tipo de cuenta validado y redirige a `/panel/postulante` o `/panel/empresa`. La página `/cuenta` conserva la consulta de datos y el cierre de sesión, con navegación para volver al panel. El navegador envía la cookie mediante `credentials: include`; no guarda contraseñas ni sesiones en `localStorage` o `sessionStorage`.

## Paneles y permisos

Los paneles muestran nombres, correo y tipo de cuenta obtenidos de PostgreSQL. El empresarial también muestra la empresa y el rol actual de la membresía (`administrador` o `reclutador`). El acceso a la cuenta y el cierre de sesión están disponibles. Vacantes, CV, edición de perfiles y postulaciones aparecen como funciones próximas; sus tarjetas no ejecutan operaciones ni muestran cifras simuladas.

| Ruta | Postulante | Empresa con membresía activa |
| --- | --- | --- |
| `/panel` | Redirige al panel de postulante | Redirige al panel empresarial |
| `/panel/postulante` | Permitida | HTTP 403 |
| `/panel/empresa` | HTTP 403 | Permitida |
| `/cuenta` y su alias | Solo datos de su sesión | Solo datos de su sesión |
| Otra ruta bajo `/panel/` | HTTP 403 | HTTP 403 |

El filtro `FiltroAccesoPanel` protege las páginas antes de ejecutar el controlador. `AccesoSesion` comparte con la API la revalidación del usuario y de la empresa exacta guardados en la sesión. El tipo de cuenta y el identificador de empresa no se toman de parámetros de la URL. Sin sesión, el servidor redirige a `/iniciar-sesion`; si la cuenta se suspende o la membresía o empresa se desactiva, invalida la sesión y borra la cookie. Una falla temporal de PostgreSQL devuelve HTTP 503 con un mensaje genérico y permite reintentar sin revocar la sesión. Las respuestas privadas, incluidas las redirecciones y errores, usan `Cache-Control: no-store`.

La comprobación del backend se realiza en cada solicitud privada, de acuerdo con la [guía de autorización de OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html). Ambos roles empresariales pueden consultar su panel en esta etapa; las reglas para futuras operaciones de vacantes o administración se implementarán junto con esos módulos. El navegador vuelve a comprobar la sesión al restaurar el panel o regresar a su pestaña y oculta los datos ante errores, expiración o cambio de cuenta, empresa o rol.

La cookie `PRIMERPASO_SESION` usa `HttpOnly`, `SameSite=Strict` y ruta `/`. La sesión vence después de 30 minutos de inactividad y se conserva en memoria del servidor, de modo que reiniciar Spring cierra las sesiones. Todas las respuestas de sesión usan `Cache-Control: no-store`. Las solicitudes que modifican la sesión sin el token válido devuelven `403`; los datos inválidos, `400`; los fallos del servicio, `503`, sin exponer detalles internos.

En localhost se usa `PRIMERPASO_COOKIE_SEGURA=false`. Al servir mediante HTTPS, configurar `PRIMERPASO_COOKIE_SEGURA=true`. Para probar con un servidor estático, usar el mismo nombre de host en cliente y API: `localhost` en ambos o `127.0.0.1` en ambos.

## Verificación

Dentro de `server`:

```powershell
.\mvnw.cmd verify
```

Las pruebas automatizadas cubren validación, contrato HTTP, respuestas de error sin datos internos, normalización, contraseñas, catálogos, rollback, autenticación por tipo de cuenta, revocación de permisos, rotación de sesión y protección CSRF. También renderizan las páginas Thymeleaf, verifican los fragmentos, los recursos y las redirecciones de enlaces anteriores. El contexto de prueba no depende de las credenciales ni de una instancia PostgreSQL disponible.

Las pruebas de paneles comprueban acceso anónimo, tipos cruzados, sesiones corruptas, cambios de rol, revocación, errores temporales, escape de HTML, prefijo de contexto y variantes de URL con codificación o parámetros. Para verificar el comportamiento de los scripts, ejecutar también desde `server`:

```powershell
node --test src/test/js/panel.test.cjs
```

Estas pruebas usan los scripts reales y comprueban reintentos, cambios de identidad, restauración del navegador y cierre con CSRF. No sustituyen la verificación de integración con PostgreSQL.

La configuración externa sigue la [documentación de Spring Boot](https://docs.spring.io/spring-boot/reference/features/external-config.html). La transacción de registro aplica el rollback de excepciones de ejecución definido por [Spring Framework](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html).
La protección CSRF emplea un token asociado a la sesión, siguiendo el [patrón documentado por Spring Security](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html#csrf-explained-protection).
Las vistas comparten cabecera y pie mediante los [fragmentos de plantilla documentados por Thymeleaf](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html#template-layout).
