# PrimerPaso: PostgreSQL en Supabase

El navegador sigue hablando con Spring Boot. Spring se conecta por JDBC a la base `postgres` de Supabase y usa el esquema privado `primerpaso`. El registro, el inicio de sesión y los permisos por postulante y empresa siguen en PrimerPaso. Para esta integración no hacen falta Supabase Auth, Storage, claves de API ni cambios en el frontend.

**Ruta recomendada para la primera prueba:** instala el esquema vacío y crea cuentas nuevas desde la aplicación. El archivo `datos_demostracion.sql` contiene cuentas con contraseñas conocidas y está destinado a PostgreSQL local. Si necesitas conservar registros existentes, sigue la ruta de copia antes de abrir el acceso a usuarios.

## 1. Configura un proyecto nuevo

1. Crea un proyecto dedicado a PrimerPaso. Elige una región cercana al lugar donde ejecutarás Spring. Guarda la contraseña de administración de la base en un gestor de secretos y activa MFA en tu cuenta de Supabase.
2. En la integración **Data API**, apaga **Enable Data API** y deja `primerpaso` fuera de **Exposed schemas**. Las tablas de usuarios, perfiles, postulaciones y mensajes solo las consulta el backend. [Seguridad de la Data API](https://supabase.com/docs/guides/api/securing-your-api#disable-the-data-api).
3. En **Database Settings → SSL Configuration**, activa **Enforce SSL on incoming connections** y descarga el certificado CA. Guarda el certificado en una ruta legible por Spring. Si la PC y el servidor tienen IP de salida estable, permite solo sus rangos en **Network Restrictions**; incluye la conexión de mantenimiento y confirma el acceso antes de cerrar la sesión de administración. [TLS](https://supabase.com/docs/guides/platform/ssl-enforcement) · [Restricciones de red](https://supabase.com/docs/guides/platform/network-restrictions).
4. En **Connect**, copia el **host exacto**. Elige una conexión según la red de cada equipo:

   | Conexión | Host y puerto | Usuario de administración | Usuario de Spring |
   | --- | --- | --- | --- |
   | Directa, preferida para mantenimiento y servidores con IPv6 o IPv4 dedicado | `db.<REF>.supabase.co:5432` | `postgres` | `primerpaso_app` |
   | Pooler compartido en modo **Session**, si la red solo tiene IPv4 | Host del panel `*.pooler.supabase.com:5432` | `postgres.<REF>` | `primerpaso_app.<REF>` |

   `<REF>` es la referencia del proyecto. En ambos casos la base se llama `postgres`. El perfil usa conexiones de sesión y sentencias preparadas: elige el puerto `5432`. [Modos y cadenas de conexión](https://supabase.com/docs/guides/database/connecting-to-postgres).

## 2. Instala y verifica el esquema

Los ejemplos se ejecutan desde la raíz del repositorio con PowerShell 7 y las herramientas de PostgreSQL instaladas. Cambia la ruta de los binarios si hace falta. `-W` solicita la contraseña sin escribirla en el comando.

```powershell
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$binPg = 'C:\Program Files\PostgreSQL\18\bin'
$psql = Join-Path $binPg 'psql.exe'
$servidorSupabase = '<host exacto de Connect, sin puerto>'
$usuarioAdminSupabase = 'postgres.<REF>' # Para conexión directa, escribe postgres
$certificadoSupabase = (Resolve-Path -LiteralPath '<ruta del certificado CA descargado>').Path
$env:PGSSLMODE = 'verify-full'
$env:PGSSLROOTCERT = $certificadoSupabase
$env:PGCLIENTENCODING = 'UTF8'
$env:PGCONNECT_TIMEOUT = '10'
$conexionAdmin = @('-h', $servidorSupabase, '-p', '5432', '-U', $usuarioAdminSupabase, '-d', 'postgres', '-W')

& $psql -X @conexionAdmin -c 'SELECT current_database(), current_user, version();'
& $psql -X @conexionAdmin -f .\db\supabase\01_inicializar.sql
```

Ejecuta [01_inicializar.sql](01_inicializar.sql) con `psql`, que resuelve el archivo incluido mediante `\ir`. El esquema base ya incluye las migraciones V001 a V004. El instalador se detiene si detecta una instalación previa. Si falla, revisa el error antes de continuar: puede haber creado el esquema privado aunque no haya habilitado el acceso.

Abre la consola de PostgreSQL y asigna al usuario de Spring una contraseña aleatoria, distinta de la de administración:

```powershell
& $psql -X @conexionAdmin -v ON_ERROR_STOP=1
```

Dentro de `psql`:

```sql
SET password_encryption = 'scram-sha-256';
\password primerpaso_app
ALTER ROLE primerpaso_app LOGIN;
\q
```

Luego comprueba los permisos efectivos. El resultado esperado en un proyecto nuevo es `20` tablas con RLS:

```powershell
& $psql -X @conexionAdmin -f .\db\supabase\02_verificar.sql
$usuarioSpring = 'primerpaso_app.<REF>' # Para conexión directa, escribe primerpaso_app
$conexionSpring = @('-h', $servidorSupabase, '-p', '5432', '-U', $usuarioSpring, '-d', 'postgres', '-W')
& $psql -X @conexionSpring -c '\conninfo' -c 'SELECT current_user, current_schema();' -c 'SELECT count(*) FROM primerpaso.users;'
```

La consulta debe mostrar `primerpaso_app` y `primerpaso`, y `\conninfo` debe indicar SSL. [02_verificar.sql](02_verificar.sql) revisa que los roles de API no puedan acceder al esquema, las tablas ni las secuencias, que RLS esté habilitada y que el rol de Spring no sea administrador ni propietario. El borrado está limitado a las habilidades asociadas a vacantes y a los contadores temporales de acceso. RLS separa el rol de Spring de los demás; la separación entre cuentas y empresas la comprueba el backend.

Si elegiste instalar sin datos, ejecuta el inventario y pasa a la sección 4:

```powershell
& $psql -X @conexionAdmin -f .\db\supabase\03_inventario.sql
```

### Actualizar una instalación existente

Si tu instalación ya usa el esquema privado y el rol `primerpaso_app`, ejecuta estos dos archivos como administrador antes de arrancar el backend actualizado:

```powershell
& $psql -X @conexionAdmin -f .\db\supabase\04_limites_inicio_sesion.sql
& $psql -X @conexionAdmin -f .\db\supabase\02_verificar.sql
```

V004 añade la tabla y sus permisos, sin modificar las cuentas ni los datos de contratación.

## 3. Opcional: conserva datos existentes

Transfiere solo los registros que tengas autorización para conservar. La base puede contener correos, teléfonos, cartas, mensajes y hashes de contraseñas. Guarda los respaldos en una ubicación cifrada y fuera del repositorio; el formato de `pg_dump` no los cifra por sí solo. El [inventario](03_inventario.sql) detecta marcadores conocidos de demostración o pruebas y cuenta filas sin mostrar datos personales. Revísalos también manualmente: una cuenta ficticia sin esos marcadores puede pasar el filtro.

Haz primero un ensayo con datos inventados en un entorno descartable. Comprueba la versión de destino y ensaya la restauración si es anterior a la de origen; PostgreSQL no garantiza que una exportación de una versión nueva se restaure en una más antigua. Consulta [pg_dump](https://www.postgresql.org/docs/current/app-pgdump.html) y [pg_restore](https://www.postgresql.org/docs/current/app-pgrestore.html).

**En otra terminal**, prepara una copia local. Cambia la carpeta por un directorio privado existente y usa un nombre de base de preparación que aún no exista. El ejemplo presupone que el esquema `public` de origen contiene solo objetos de PrimerPaso.

```powershell
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$binPg = 'C:\Program Files\PostgreSQL\18\bin'
$psql = Join-Path $binPg 'psql.exe'
$pgDump = Join-Path $binPg 'pg_dump.exe'
$pgRestore = Join-Path $binPg 'pg_restore.exe'
$createdb = Join-Path $binPg 'createdb.exe'
$carpetaPrivada = '<ruta de la carpeta cifrada>'
$respaldo = Join-Path $carpetaPrivada 'primerpaso-origen.dump'
$traslado = Join-Path $carpetaPrivada 'primerpaso-traslado.dump'
$basePreparacion = 'primerpaso_preparacion'
$env:PGCLIENTENCODING = 'UTF8'
$conexionLocal = @('-h', '127.0.0.1', '-p', '5432', '-U', 'postgres', '-W')

if ((Test-Path -LiteralPath $respaldo) -or (Test-Path -LiteralPath $traslado)) {
    throw 'Elige nombres nuevos para el respaldo y el traslado.'
}
& $pgDump @conexionLocal -d PrimerPaso -Fc --schema=public -f $respaldo
& $createdb @conexionLocal -T template0 $basePreparacion
& $pgRestore @conexionLocal -d $basePreparacion --clean --if-exists --no-owner --no-acl --single-transaction --exit-on-error $respaldo
& $psql -X @conexionLocal -d $basePreparacion -v esquema=public -f .\db\supabase\03_inventario.sql
```

Usa `--clean` solo en la copia recién creada: permite reemplazar su esquema `public` vacío. Si el inventario detecta cuentas de prueba, prepara un conjunto depurado que respete las relaciones entre usuarios, empresas, postulaciones y mensajes. Una cuenta de prueba puede tener registros asociados a una cuenta legítima; revisa esas relaciones antes de excluir datos. Si falta una migración V001 a V003, aplícala en la copia y vuelve a comprobarla. No continúes hasta obtener el inventario sin errores.

Renombra `public` **solo en la copia** para que el archivo de datos apunte a `primerpaso`. La exportación incluye filas e identificadores de secuencia. El esquema y los permisos del destino ya los creó el instalador.

```powershell
& $psql -X @conexionLocal -d $basePreparacion -v ON_ERROR_STOP=1 -c 'ALTER SCHEMA public RENAME TO primerpaso;'
& $psql -X @conexionLocal -d $basePreparacion -f .\db\supabase\03_inventario.sql
& $pgDump @conexionLocal -d $basePreparacion -Fc --data-only --schema=primerpaso --exclude-table-data=primerpaso.limites_inicio_sesion -f $traslado
```

En la terminal de Supabase del paso 2, define la ruta al archivo de traslado. Verifica que todas las tablas de destino estén vacías antes de restaurar. Si vas a hacer el cambio definitivo, detén las escrituras en el origen antes del respaldo final y mantenlas detenidas hasta la validación.

```powershell
$pgRestore = Join-Path $binPg 'pg_restore.exe'
$traslado = '<ruta absoluta de primerpaso-traslado.dump>'
& $psql -X @conexionAdmin -v exigir_vacia=on -f .\db\supabase\03_inventario.sql
& $pgRestore @conexionAdmin --no-owner --no-acl --single-transaction --exit-on-error $traslado
& $psql -X @conexionAdmin -f .\db\supabase\03_inventario.sql
& $psql -X @conexionAdmin -f .\db\supabase\02_verificar.sql
```

Compara los recuentos con los de la copia, excepto los contadores temporales de acceso, y confirma que el inventario no marque secuencias atrasadas. Antes de reabrir el servicio, prueba un alta y la lectura de registros relacionados. Conserva el origen y el respaldo hasta verificar el cambio. Si falla la validación, puedes volver al origen antes de aceptar escrituras en Supabase; si ambos entornos recibieron escrituras, concilia los cambios primero.

## 4. Conecta y prueba Spring

Usa el perfil `supabase` y guarda la contraseña del rol de aplicación en las variables de entorno del servidor o del alojamiento. La clave va separada de la URL. Estas instrucciones prueban Spring en tu PC mientras PostgreSQL ya está en Supabase:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'supabase'
$env:PRIMERPASO_SUPABASE_HOST = $servidorSupabase
$env:PRIMERPASO_SUPABASE_SUFIJO_USUARIO = '.<REF>' # Para conexión directa, omite la variable o déjala vacía
$env:PRIMERPASO_SUPABASE_CA = $certificadoSupabase
$env:PRIMERPASO_SUPABASE_CLAVE = Read-Host 'Contraseña de primerpaso_app' -MaskInput
$env:PRIMERPASO_SUPABASE_COOKIE_SEGURA = 'false' # Solo para esta prueba HTTP en 127.0.0.1

Push-Location .\server
try {
    .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.address=127.0.0.1'
} finally {
    Pop-Location
    Remove-Item Env:PRIMERPASO_SUPABASE_CLAVE, Env:PRIMERPASO_SUPABASE_COOKIE_SEGURA
}
```

Abre [127.0.0.1:8080](http://127.0.0.1:8080). Confirma que Spring registra **Conexión con PostgreSQL y tablas del proyecto comprobadas**. Registra un postulante y dos empresas con credenciales nuevas; comprueba que una empresa pueda publicar una vacante, que el postulante pueda enviar una solicitud y que la otra empresa no pueda abrirla ni leer su chat. Prueba la evaluación, los mensajes, las ofertas y las notificaciones. Reinicia Spring y confirma que los datos persisten volviendo a iniciar sesión.

Si la conexión falla, revisa el host, el sufijo `.<REF>` del modo Session, el acceso de red y que la ruta al CA sea legible. Conserva `verify-full` para comprobar el certificado y el nombre del servidor. Si el proceso arranca pero la sesión no se mantiene, comprueba que estés usando el mismo host en todas las vistas y la configuración de cookie que corresponde a HTTP local o HTTPS.

## 5. Antes de publicar Spring en internet

- Sigue la [guía de Render](../../server/DESPLIEGUE-RENDER.md) para configurar HTTPS, secretos y la conexión por el puerto 5432. PrimerPaso aplica sus propios límites de acceso y registro.
- Revisa **Security Advisor**, mantén apagados los servicios y extensiones que no uses, y verifica la cobertura de respaldos de tu plan y una restauración de prueba. Usa acceso de administración para migraciones posteriores con `search_path` en `primerpaso`. Repite [02_verificar.sql](02_verificar.sql) tras cambiar tablas, permisos o políticas. [Lista de producción de Supabase](https://supabase.com/docs/guides/security/production-checklist) · [Respaldos](https://supabase.com/docs/guides/platform/backups).
- Si sigues utilizando PostgreSQL en la PC para desarrollo y solo se conecta esta máquina, limítalo a loopback en la configuración del servidor y evita publicar el puerto 5432. Para el despliegue, el perfil `supabase` usa credenciales distintas de `server/.env`.
