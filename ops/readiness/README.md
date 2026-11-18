# Inventario previo de IMBASAC

Herramientas preparadas el 23 de octubre de 2026. No son un despliegue, una restauración ni una certificación de continuidad. El diagnóstico SQL se ejecutó en PostgreSQL 16.15 y 18.6 desechables, no en la base del VPS. El inventario básico posterior del usuario confirmó PostgreSQL 18/main en producción; ver `docs/security-continuity/VPS_INVENTORY_2026-10-23.md`.

## Uso en Ubuntu

Transferir juntos `inventory.py` y `postgres-readonly.sql` a un directorio de trabajo restringido del VPS, utilizando una conexión SSH cuya identidad de servidor ya esté verificada. El informe se imprime a la salida estándar; conservarlo en un destino privado fuera de Git.

```sh
sudo python3 inventory.py --pg-database postgres --pg-port 5432
```

Agregar `--service nombre-real.service` por cada servicio identificado. No se ha confirmado el nombre de la unidad del JAR y no debe inventarse. Elegir la base y el puerto reales tras comprobar el clúster; `postgres` es solo la base administrativa de ejemplo.

- Solo lectura de configuración y catálogo; no lee ventas, variables de entorno, contraseñas, argumentos de procesos ni configuraciones completas de Nginx.
- PostgreSQL usa socket local, usuario del sistema `postgres`, modo de solo lectura y límites de espera. No consume una URL remota ni usa las variables PG existentes del terminal.
- No ejecuta migraciones, ALTER SYSTEM, reinicios, renovación de CA, backups, promociones ni restores.
- El código de salida 2 indica comprobaciones no disponibles; 0 solo indica inventario ejecutado, nunca aprobación de RPO cero.
- Las propiedades de systemd no prueban aislamiento efectivo por sí solas. Falta revisar configuración de red, firewall, Nginx, permisos de archivos, TLS y acceso directo a cada servicio.
- El inventario no prueba exposición pública: `ss` muestra escuchas locales; contrastar con firewall de Ubuntu/Hostinger y acceso exterior controlado.

## Qué significa PostgreSQL

`configuration_candidate=true` solo señala condiciones mínimas observadas en ese instante. No valida el número de acknowledgements de una política de quorum ni su dominio de fallo. Un replica con flush observado no demuestra que las transacciones de ventas hayan exigido ese flush.

Siempre se conservan en `false` las garantías `rpo_zero_verified` y `rto_one_hour_verified`: exigen evidencia adicional y un ensayo aislado. Hay que comprobar la configuración efectiva del usuario/base/conexión de la aplicación, el almacenamiento remoto, la independencia del destino, la durabilidad de archivos y eventos de seguridad, el aislamiento del escritor anterior y la recuperación completa. Una conexión a la base desde otra PC no es una réplica.

`remote_write` se rechaza como candidato de persistencia durable remota; `on` sin `synchronous_standby_names` tampoco basta. No cambiar estos parámetros automáticamente ni habilitar sincronía sin un destino operativo: puede bloquear las confirmaciones.

Referencia: [PostgreSQL: synchronous_commit](https://www.postgresql.org/docs/current/runtime-config-wal.html#GUC-SYNCHRONOUS-COMMIT), [replicación síncrona](https://www.postgresql.org/docs/current/warm-standby.html#SYNCHRONOUS-REPLICATION). Comprobar también la documentación de la versión mayor real del VPS.

## Verificación local

```sh
python3 -m unittest discover -s ops/readiness -p 'test_*.py'
```

Las pruebas no inician servicios ni se conectan a producción. Comprueban rechazo de falsos positivos y protección de la salida ante errores.

## Recoger el informe desde tu terminal habitual

El acceso de Codex no está autenticado todavía. Puedes recoger el informe con tu conexión habitual de OpenSSH, introduciendo cualquier contraseña únicamente en tu terminal. Estos comandos copian solo las dos herramientas a una carpeta privada y consultan la configuración; no publican la aplicación ni ejecutan migraciones.

Desde PowerShell, con la identidad del servidor ya verificada en tu registro conocido:

```powershell
ssh -o StrictHostKeyChecking=yes root@76.13.81.60 'umask 077; mkdir -p /root/imbasac-readiness'
scp -o StrictHostKeyChecking=yes C:/Users/Paul/Desktop/IMBASAC/imbasac-system-backend/ops/readiness/inventory.py C:/Users/Paul/Desktop/IMBASAC/imbasac-system-backend/ops/readiness/postgres-readonly.sql root@76.13.81.60:/root/imbasac-readiness/
ssh -o StrictHostKeyChecking=yes root@76.13.81.60 'python3 /root/imbasac-readiness/inventory.py --pg-database postgres --pg-port 5432'
```

Si no hay una identidad verificada o se rechaza la lectura de known_hosts, detenerse y resolver el registro desde una vía confiable, por ejemplo comparando la huella con la consola de Hostinger. No cambiar StrictHostKeyChecking a no. El código no puede verificar por sí mismo una huella obtenida de la misma conexión que se está intentando establecer. No compartir contraseñas, claves privadas ni configuraciones completas para resolverlo.

El diagnóstico no incluye aún la unidad del JAR: agregar el nombre real con `--service` cuando se identifique. Si PostgreSQL usa otro puerto, cambiarlo tras consultar los clústeres reales; un fallo de conexión es un resultado pendiente, no ausencia de réplica demostrada.

## Primera conexión: identidad ED25519 aún no registrada

El mensaje `No ED25519 host key is known` con `StrictHostKeyChecking=yes` indica que falta registrar una identidad verificada. Este paso se realiza antes de copiar las herramientas.

1. Abrir la consola web del VPS desde el panel autenticado de Hostinger y obtener la huella pública del servidor:

```sh
ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256
```

2. En PowerShell de Windows, iniciar una conexión que pregunte por la identidad. Se usa un archivo dedicado fuera del repositorio para no depender de permisos del known_hosts anterior ni modificarlo:

```powershell
ssh -o UserKnownHostsFile=C:/Users/Paul/.codex/imbasac_known_hosts -o HostKeyAlgorithms=ssh-ed25519 -o StrictHostKeyChecking=ask root@76.13.81.60 "install -d -m 700 /root/imbasac-readiness"
```

Comparar el valor completo `SHA256:...` mostrado por Windows con el obtenido en la consola de Hostinger. Escribir `yes` únicamente si coinciden exactamente; después introducir la contraseña solo en la terminal si corresponde. Si difieren o aparece un cambio de clave, detenerse y revisar, sin borrar registros ni desactivar la comprobación.

3. En los comandos scp/ssh de recogida, agregar `-o UserKnownHostsFile=C:/Users/Paul/.codex/imbasac_known_hosts` y conservar `-o StrictHostKeyChecking=yes`. La carpeta remota ya fue creada por el paso anterior. Esta identidad identifica al servidor SSH; no es un certificado de cliente del ERP.

Fuente: [OpenSSH](https://man.openbsd.org/ssh.1). Una clave obtenida con [ssh-keyscan](https://man.openbsd.org/ssh-keyscan.1) necesita verificación por una vía independiente antes de confiar en ella.

## Continuación directa por consola de Hostinger

El usuario eligió continuar con consultas manuales en la consola web de Hostinger. El bloque de solo lectura de `console-checks.sh` identifica el servicio, UFW, procesos de puertos locales, sitios habilitados y parámetros/reglas seleccionados de PostgreSQL 18/main. Se puede copiar su contenido sin transferir archivos ni configurar SSH de Windows. No implica publicar, exigir certificados, restringir accesos o reiniciar servicios.

## Resumen acotado de rutas Nginx

`nginx-routing-summary.sh` consulta la configuración expandida y emite solo directivas seleccionadas. Omite valores de headers y redacta credenciales URL y argumentos query. El filtro se comprobó con credenciales sintéticas, sin leer configuración del VPS. No recarga Nginx ni modifica servicios.

Es un resumen inicial por líneas, no un parser completo: directivas en línea, rewrites, variables, módulos o contextos omitidos requieren revisión adicional. Una ausencia en la salida no acredita que SUNAT sea inaccesible públicamente. No compartir un volcado completo de nginx -T, archivos .env o claves privadas para completar esa revisión.
