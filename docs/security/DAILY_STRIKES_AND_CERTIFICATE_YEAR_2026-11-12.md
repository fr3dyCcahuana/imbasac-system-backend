# Conteo diario, bloqueo en días y certificados de un año

Sustituye la acumulación sin vencimiento y la duración en meses del diseño anterior. La obligación individual de certificado continúa disponible para todos los roles y solo afecta a las cuentas configuradas expresamente.

## Reglas acordadas

- Se cuentan tres strikes dentro del mismo día de Lima, desde 00:00 hasta 23:59. El umbral sigue configurado por `security.access-control.strike-threshold`.
- El tercer strike bloquea inicialmente 24 horas desde ese intento. En Reglas de bloqueo se puede elegir entre 1 y 3650 días enteros; cada día equivale a 24 horas. El administrador puede desbloquear antes.
- Al cambiar el día, una cuenta sin bloqueo vigente comienza con cero strikes. El historial permanece completo y este reinicio diario no invalida sus sesiones válidas ni cambia su autorización.
- Si hay bloqueo vigente, el cambio de día no lo levanta ni reinicia su vencimiento. Mientras la cuenta está bloqueada se muestra el contador que produjo el bloqueo.
- Contraseña incorrecta, ciudad distinta por sí sola, certificado propio vencido o incidencia técnica no se convierten en strike de otra PC. Se conserva la deduplicación de intentos e incidentes y la sanción solo afecta a la cuenta, no a las otras personas de su PC.
- Los certificados de PC nuevos y renovados se emiten por 365 días. La CA emisora debe seguir vigente durante todo ese plazo. Los certificados anteriores conservan su fecha firmada; para darles nueva vigencia es necesario renovar.

## Contrato y persistencia

`GET /security/access-control/status` y `PUT /security/access-control/settings` utilizan `blockDays`; el frontend ya no envía ni muestra `blockMonths`. Un cliente antiguo que envíe meses no recibe una conversión implícita a días.

La migración `20261112_daily_access_strikes.sql` añade `block_days` (inicialmente 1) y `strike_day`. La primera ejecución recalcula, para cuentas no bloqueadas, los strikes históricos del día actual y conserva íntegros historial, certificados, permisos, sesiones y autorizaciones. Los bloqueos ya vigentes conservan su vencimiento exacto, aunque se hubieran creado con duración en meses. El administrador puede revisarlos y desbloquearlos expresamente. Repetir la migración no resetea nuevamente los contadores ni la duración elegida.

`block_months` se conserva como columna histórica; el código nuevo usa exclusivamente días. No se debe arrancar el JAR anterior después de aplicar esta política y activar cuentas de otros roles: no comprende las nuevas reglas.

## Compilación habitual

La prueba `PosSystemBackendApplicationTests` es una prueba de carga de componentes: usa perfil `build-test`, valores ficticios y sustitutos de DataSource, Redis y planificador. No necesita credenciales de producción ni conexión a esos servicios. Las pruebas reales de PostgreSQL/Redis siguen separadas y requieren sus variables de pruebas explícitas. Esto permite ejecutar `mvn clean install` habitualmente sin saltar toda la suite.

El perfil `build-test` se declara únicamente en la prueba; no cambia los perfiles ni la configuración del JAR productivo. `application-local.yml` sigue fuera del JAR.

## Aplicación por única vez

La entrega incluye la migración y los archivos del firmante en una carpeta SERVIDOR. El instalador comprueba identidad del VPS, servicio detenido, JAR compatible, integridad de archivos y permisos. Crea un respaldo privado de PostgreSQL y de los dos programas del firmante antes de aplicar la migración e instalar los programas actualizados. No cambia CA ni claves privadas, ni reemite certificados existentes. El operador conserva la subida y el reinicio del servicio.

Para futuras correcciones de código que no cambien esquema o política de la CA, se mantiene el flujo habitual de compilar, subir JAR/frontend y reiniciar.
