# Entrega nocturna y respaldo preparado

Herramientas locales, sin conexión al VPS ni SQL productivo ejecutados. Usar Python >=3.11. El objetivo es terminar la preparación antes de abrir la ventana, publicar fuera de 07:00–21:00 America/Lima y decidir el retorno a los 40 minutos para conservar margen dentro de una hora.

## 1. Preflight acotado y de sólo lectura

```sh
sudo python3 ops/deploy/preflight.py --database NOMBRE_REAL_BASE --port 5432 --service pos-backend.service
```

Usar consola Hostinger; no se necesita reconfigurar SSH. Comprobar nombre real de base antes del comando. Sólo consulta estado del servicio, `nginx -t`, sincronización de reloj, versión/tablas por socket PostgreSQL y espacio. No imprime secretos, environment del servicio ni datos de clientes. Que una tabla exista no demuestra versión completa de esquema/migración; comparar columnas/índices con los SQL exactos de esta entrega en una copia. No aplica migraciones, reinicia ni publica.

## 2. Preparar selección y respaldo cifrado

Revisar `backup-selection.example.json`: contiene rutas históricas, no acredita que todas existan. Guardar una selección corregida privada fuera de Git. Debe incluir aplicación/configuración, frontend y archivos realmente utilizados, API SUNAT privada y su configuración, adjuntos/XML/CDR, credenciales de runtime necesarias para recuperar y registros de revocación/políticas. Añadir rutas de datos que se hayan movido. No seleccionar todo `/etc` o `/var`; los enlaces se resuelven explícitamente. Agregar la CA emisora y su base/seriales sólo una vez instalada y en un respaldo separado con custodia adecuada; la raíz nunca está en el VPS.

Antes del respaldo de corte: detener/drenar usuarios, escritores, jobs, callbacks y agente fiscal según inventario real. No descartar callbacks; deben persistirse o provocar reintento seguro. El script exige acuse, pero no puede demostrar que todos los escritores fueron detenidos.

```sh
sudo python3 ops/deploy/backup_release.py --database NOMBRE_REAL_BASE --port 5432 --selection-json /root/imbasac-release/backup-selection.json --destination /var/backups/imbasac-release-20261030-2100 --passphrase-file /root/imbasac-release/backup.pass --acknowledge-writers-paused
```

El padre del destino debe existir y el destino debe ser nuevo. La contraseña fuerte se prepara localmente en archivo 0600; no enviarla al chat. El script utiliza `pg_dump` custom por socket local con `postgres`, comprueba TOC con `pg_restore --list`, archiva la selección y sus checksums, cifra el paquete con GPG AES256 y deja únicamente paquete cifrado + checksum bajo 0700/0600. Nunca sobreescribe un respaldo. Un éxito de `--list` todavía **no** acredita restauración. No incluye contraseñas de roles PostgreSQL: conservar configuración privada para recrear roles/accesos en destino.

Copiar paquete cifrado y checksum a un destino independiente disponible (puede ser la computadora del operador, sin contratar otro VPS) y verificar checksum allí. Custodiar contraseña por separado. No basta con guardarlo en otra carpeta del VPS. Las claves de clientes no se respaldan/exportan desde el ERP. El respaldo temporal de archivos contiene secretos y debe permanecer privado; borrar un archivo no demuestra borrado criptográfico del medio.

## 3. Restauración aislada local

En una carpeta nueva privada fuera del repositorio, descifrar con `gpg --output release-backup.tar --decrypt release-backup.tar.gpg`; solicitará la contraseña localmente. Revisar el índice del tar antes de extraer, sin privilegios, sin sobrescribir rutas del sistema y verificando checksums del manifiesto. Los archivos contenidos son `database.dump`, `selected-files.tar` y `manifest.json`; la restauración de archivos se hace sólo en una carpeta/VM de ensayo, jamás sobre las rutas originales. No ejecutar archivos recuperados sin revisar.

Con Docker local y la imagen PostgreSQL 18 ya disponible:

```sh
python3 ops/deploy/restore_check.py --dump /ruta/privada/database.dump --image postgres:18-alpine
```

La herramienta crea nombre aleatorio, red `none`, ningún puerto publicado y restaura sólo dentro de ese contenedor. No acepta host, base existente ni imagen arbitraria; elimina exclusivamente su contenedor y volúmenes creados. No instala Docker en el VPS ni ejecuta la comprobación allí por defecto. El resultado comprueba restauración PostgreSQL; sigue pendiente levantar aplicación aislada y verificar permisos, archivos/SUNAT simulado, revocaciones vigentes, venta/idempotencia y compatibilidad de retorno. Un dump productivo sigue conteniendo datos sensibles incluso en pruebas: mantener equipo de ensayo controlado.

## 4. Paquete y secuencia de ventana

Preparar antes: JAR/Angular compilados y probados, checksums de artefactos, migraciones exactas, configuración sin secretos, respaldo de configuración anterior, versión de retorno compatible y smoke tests sin ventas/fiscalización reales. Registrar flags iniciales desactivados y activar cada frente por separado. Comparar migraciones con versión real; no ejecutar el historial completo de SQL.

El conjunto de seguridad actual tiene **seis migraciones**, en este orden de ensayo/aplicación revisada:

1. [20261029_device_access_foundation.sql](../../src/main/resources/db/scripts/20261029_device_access_foundation.sql).
2. [20261029_access_audit.sql](../../src/main/resources/db/scripts/20261029_access_audit.sql).
3. [20261030_access_control.sql](../../src/main/resources/db/scripts/20261030_access_control.sql).
4. [20261030_device_certificates.sql](../../src/main/resources/db/scripts/20261030_device_certificates.sql).
5. [20261030_ca_revocation_outbox.sql](../../src/main/resources/db/scripts/20261030_ca_revocation_outbox.sql).
6. [20261030_private_sunat_files.sql](../../src/main/resources/db/scripts/20261030_private_sunat_files.sql).

Si una ya fue aplicada, comprobar su definición efectiva y no ejecutarla de nuevo automáticamente. En particular, la primera crea el índice original de una PC y la tercera lo sustituye por cupos/excepciones: no reaplicar la primera después de la tercera. No hay grants automáticos ni flags activados por esos SQL. Antes de cerrar los aliases de XML/CDR de Nginx, verificar el permiso `VIEW_SUNAT_DOCUMENTS`, el permiso comercial correspondiente y la descarga autorizada a través del ERP para evitar romper comprobantes legítimos. La API PHP SUNAT sigue siendo exclusiva del backend.

GeoIP es opcional: `security.geoip.enabled=false` permite preparar certificados sin archivo MMDB. Si se decide activarlo, el propietario aporta un MMDB de ciudad con licencia válida, fuera de Git y legible por backend, y define actualización. La configuración no puede activarse con archivo ausente/inválido; ubicación desconocida nunca equivale por sí sola a equipo ajeno ni genera un strike.

| Minutos orientativos | Acción |
|---|---|
| 0–10 | Mantenimiento y drenar escritores; registrar último corte; respaldo protegido y copia independiente ya ensayados |
| 10–25 | Migraciones aditivas revisadas, paquete preparado y configuración validada |
| 25–40 | Salud, dominio, roles exentos/requeridos, sesión, archivos privados, API SUNAT inaccesible por front, prueba de regresión autorizada |
| A los 40 | Decidir continuar o aplicar retorno compatible; no consumir el margen improvisando |
| 40–60 | Finalizar retorno si aplica, verificar estado comercial/seguridad y reabrir |

Los tiempos son objetivos que necesitan ensayo, no garantía. Una copia grande puede no caber en 10 minutos: preparar copia base/ensayo y calcular el corte real antes. Si no cabe, rediseñar la entrega; no omitir respaldo para cumplir la hora.

## 5. Retorno y continuidad

Volver a un JAR/Angular/configuración anterior sólo si conserva políticas, sesiones, revocaciones, idempotencia y esquema actuales. **No restaurar una base antigua para deshacer el JAR**. No reabrir el acceso requerido con una versión que ignore sus restricciones; mantenerlo cerrado y diagnosticar. Restauración de desastre requiere además reconciliar el diario vigente de revocaciones antes de permitir acceso.

El respaldo del cierre protege el corte y los datos históricos; no conserva automáticamente las ventas confirmadas durante la jornada siguiente ante pérdida total del VPS. RPO cero sigue sin acreditarse hasta disponer de persistencia síncrona independiente y ensayo completo. Este frente no bloquea desarrollo local de certificados; debe informarse como garantía pendiente.

Comprobaciones locales: `python3 -m unittest discover -s ops/deploy -p 'test_*.py' -v`. Se acompañan de ensayo real aislado de `restore_check.py` con PostgreSQL 18 y datos sintéticos cuando se registra en las acciones del operador. Ninguna prueba documental despliega producción.

Referencias: [PostgreSQL 18 pg_dump](https://www.postgresql.org/docs/18/app-pgdump.html), [pg_restore](https://www.postgresql.org/docs/18/app-pgrestore.html), [respaldo](https://www.postgresql.org/docs/18/backup.html), [replicación síncrona](https://www.postgresql.org/docs/18/warm-standby.html#SYNCHRONOUS-REPLICATION).
