# Cuentas de demostración de PrimerPaso

El archivo `datos_demostracion.sql` crea seis cuentas persistentes con datos sintéticos, tres empresas, 18 vacantes y 15 postulaciones. Se ejecuta de forma explícita sobre el esquema existente; no se carga al iniciar Spring y no modifica las tablas ni las cuentas anteriores.

## Accesos

Contraseña para las seis cuentas: **`PrimerPasoDemo2026!`**.

En la pantalla de inicio de sesión, seleccionar el tipo de cuenta que indica la tabla.

| Tipo de cuenta | Empresa o persona | Correo | Rol o formación |
| --- | --- | --- | --- |
| Empresa | Andes Digital (Demo) | `empresa.andes@demo.primerpaso.test` | Administrador |
| Empresa | Impulso Creativo (Demo) | `empresa.impulso@demo.primerpaso.test` | Administrador |
| Empresa | Horizonte Analítica (Demo) | `empresa.horizonte@demo.primerpaso.test` | Reclutador |
| Postulante | Ana Torres | `ana.postulante@demo.primerpaso.test` | Ingeniería de Software, estudiante de ciclo 6 |
| Postulante | Bruno Medina | `bruno.postulante@demo.primerpaso.test` | Diseño Gráfico, egresado de 2025 |
| Postulante | Carla Rojas | `carla.postulante@demo.primerpaso.test` | Ciencia de Datos, titulada, egreso de 2024 |

Los nombres de empresas llevan `(Demo)` y los títulos de vacantes comienzan con `[Demo]`. Los correos terminan en `.test` y las identificaciones fiscales son ejemplos sin validez real. No hay CV adjuntos ni mensajes de correo enviados. La contraseña conocida se almacena con el mismo PBKDF2-HMAC-SHA-256 de la aplicación, con 600 000 iteraciones y una sal distinta por cuenta.

## Datos disponibles

Cada empresa tiene seis vacantes: tres publicadas vigentes, un borrador, una cerrada y una publicada cuyo plazo ya venció. Las vigentes combinan las modalidades presencial, remota e híbrida. La fecha de vencimiento de las vigentes y los borradores se fija a 60 días desde la primera carga.

Cada postulante tiene cinco solicitudes con carta de presentación, fechas y estados de ejemplo. Cada empresa recibe cinco postulaciones; su primera vacante vigente tiene tres candidatos para comparar. El conjunto incluye tres solicitudes enviadas, cuatro en revisión, tres seleccionadas y cinco descartadas. También hay solicitudes históricas en vacantes cerradas o vencidas.

Los perfiles incluyen ciudad, institución de demostración, carrera, condición académica y habilidades. El teléfono y el sitio web quedan sin registrar. La cuenta de Horizonte permite mostrar el rol de reclutador: gestiona vacantes y postulaciones, consulta los datos de la empresa y edita su propio contacto. Los administradores de Andes e Impulso también pueden actualizar los datos empresariales.

## Recorrido sugerido

1. Entrar como Ana, explorar una vacante `[Demo]` a la que aún no haya postulado y enviar una carta. Consultar el historial y el detalle de una solicitud ya enviada.
2. Entrar como Andes, abrir **Vacantes**, usar **Ver postulaciones** en la oportunidad de backend y revisar los tres candidatos, las cartas y las fechas. Actualizar un estado.
3. Volver a la cuenta del postulante correspondiente y comprobar el estado actualizado.
4. Desde una empresa, publicar su borrador, editar una vacante vigente y consultar la cerrada o la vencida. Estas últimas no admiten nuevas postulaciones.
5. Comparar **Mi cuenta** de un administrador con la de Horizonte para comprobar los permisos del reclutador.

Los cambios que se hagan durante la demostración quedan guardados. El script no los reinicia.

## Repetir la carga en una base local

Desde la raíz del repositorio, después de crear el esquema y configurar la conexión:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -X -h localhost -p 5432 -U postgres -d PrimerPaso -v ON_ERROR_STOP=1 -f .\db\datos_demostracion.sql
```

La contraseña de PostgreSQL se solicita por separado; no es la contraseña de demostración. Ajustar host, puerto, usuario y nombre de base si la instalación local usa otros valores. No ejecutar `primerpaso.sql` sobre una base existente.

La carga usa una sola transacción y un bloqueo para evitar ejecuciones simultáneas del mismo script. Si las seis cuentas con su marca `demostracion-v1` ya existen, termina sin agregar duplicados, restablecer contraseñas ni cambiar fechas, roles o estados. Si un correo pertenece a una cuenta anterior, una identificación fiscal está ocupada, el conjunto de cuentas está incompleto o un catálogo requerido está inactivo, rechaza la carga y revierte la transacción. No hay una operación automática de borrado o reinicio.

## Verificación realizada

La carga se ejecutó en PostgreSQL local y se repitió para comprobar que no modificaba ninguna fila. Las huellas de los registros anteriores de las 15 tablas se conservaron y el esquema permaneció igual. Se verificaron por HTTP los seis inicios de sesión, sus paneles, perfiles y cinco postulaciones por cuenta; también las seis vacantes de cada empresa, sus estados y el rechazo de acceso a paneles de otro tipo de cuenta. Las sesiones usadas para verificar se cerraron sin editar los datos de demostración.

Commit sugerido: `feat(demo): agregar cuentas y escenarios de demostracion`. Incluir únicamente `db/datos_demostracion.sql`, `db/README-demostracion.md` y `server/README.md`.
