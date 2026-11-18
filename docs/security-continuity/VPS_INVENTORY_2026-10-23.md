# VPS IMBASAC — inventario recibido el 23 de octubre de 2026

Fuente: salida de consultas de solo lectura que el usuario ejecutó directamente en la consola del VPS. No supone acceso SSH automatizado de Codex ni acredita todavía configuración completa, capacidad bajo carga, backups o recuperación.

## Datos confirmados

| Elemento | Resultado observado |
| --- | --- |
| Host de la consola | srv1283651 |
| Sistema | Ubuntu 24.04.3 LTS (Noble) |
| Nginx | 1.24.0, paquete Ubuntu |
| Java del terminal | Oracle Java 21.0.9+7-LTS-338; falta comprobar ejecutable efectivo del servicio |
| PostgreSQL | 18.1 (Ubuntu 18.1-1.pgdg24.04+2), clúster 18/main, online, puerto 5432, propietario postgres |
| Backend | pos-backend.service, activo/running; User=posapp, Group=www-data, WorkingDirectory=/opt/pos-backend |
| Otros servicios | nginx, apache2, redis-server, postgresql@18-main, tailscaled y mtc-socks-failover activos |
| Disco / | 96 GB totales, 11 GB usados, 86 GB disponibles (12 % usado) |
| Memoria | 7.8 GiB total, 1.1 GiB usados, 6.6 GiB disponibles en esta captura |
| Swap | 0 B |

El estado de memoria/disco no demuestra margen suficiente bajo carga de ventas o firma. No se propone cambiar recursos, Java, PostgreSQL o swap a partir de esta captura.

## Escuchas observadas

- Nginx/servicio web por atribuir con PID: 80 y 443, IPv4/IPv6 generales.
- SSH por atribuir con PID: 22, IPv4/IPv6 generales.
- PostgreSQL: 5432 en 0.0.0.0 y ::. Escucha en todas las interfaces; falta comprobar firewall de Ubuntu/Hostinger, pg_hba y TLS antes de concluir alcance exterior o restringir acceso.
- Redis: 6379 en 127.0.0.1 y ::1.
- 8091 en ::ffff:127.0.0.1: confirmado proceso java, solo loopback en esta captura; falta confirmar rutas y protecciones de Nginx.
- 8093 en 127.0.0.1: confirmado apache2. El usuario identifica esta API como integración privada con SUNAT: acceso exclusivo desde backend, jamás exposición directa desde Angular ni por un proxy público genérico.
- 1080 en 127.0.0.1: falta atribuir proceso; existe servicio mtc-socks-failover activo.
- Puertos 49760 y 39886 sobre direcciones del rango privado de Tailscale: confirmar propietario y función antes de restringirlos.
- Resolved local en puerto 53. No se observó una escucha 8092; no demuestra por sí solo ausencia de una integración WhatsApp.

No cambiar rutas ni bloquear servicios auxiliares basándose únicamente en estos puertos. El usuario utiliza acceso remoto a PostgreSQL; definir su vía autorizada antes de modificarla.

## Revisión siguiente, solo lectura

1. Propietario, directorio y protecciones de pos-backend.service, sin imprimir Environment ni ExecStart con posibles secretos.
2. Firewall Ubuntu y reglas de Hostinger. UFW por sí solo no representa todos los filtros (nftables/iptables/proveedor/Tailscale).
3. Proceso que posee 8091/8093/5432.
4. Configuración efectiva de persistencia de PostgreSQL y conexiones de replicación. Consultar solo parámetros seleccionados y estadísticas, no contraseñas ni ventas.
5. Rutas/entrada HTTPS efectivas de Nginx, autenticación cliente y forma de proxy. Evitar volcados completos de configuración con secretos.
6. Esquema y aplicación de migraciones manuales, respaldo verificable, archivos persistentes y trabajos automáticos.

## Ejecución y horarios

La consola de Hostinger permite continuar estas consultas sin habilitar SSH de Windows ni ejecutar los archivos copiados. La huella SSH aceptada desde Windows no se considera verificada independientemente sin comparación con la consola autenticada.

Publicaciones/cambios que afectan producción: fuera de 07:00–21:00 America/Lima, máximo una hora de intervención acordada. Una actualización nocturna exige respaldo restaurable y retorno compatible. La protección continua de la última venta ante pérdida del VPS se valida por separado con un destino independiente; no se declara cumplida ni bloquea las pruebas locales de certificados.

## Validación por versión

El VPS usa PostgreSQL 18; las primeras integraciones locales se habían probado en 16.15. Se repitieron 9 pruebas de idempotencia y 16 de reasignación de reservas en PostgreSQL 18.6 aislado: 25 correctas, cero errores/fallos/omisiones. También se ejecutaron correctamente el diagnóstico SQL y las consultas acotadas de pg_settings, pg_stat_replication y pg_hba_file_rules. Estos resultados no son una auditoría del esquema real ni acreditan la versión menor del VPS; la salida posterior del VPS confirmó versión 18.1. Las pruebas en 18.6 cubren esa versión de ensayo, no equivalen a probar el VPS 18.1 ni su esquema real. El contenedor de prueba se retiró al terminar.

El siguiente bloque manual se conserva en `ops/readiness/console-checks.sh`; ejecutar su contenido en Ubuntu, no en PowerShell. Sus resultados fueron recibidos y se registran abajo; queda pendiente la inspección de rutas Nginx y firewall de Hostinger.

## Segunda salida de consola recibida

### Servicio backend

- Unidad: /etc/systemd/system/pos-backend.service; directorio /opt/pos-backend.
- Usuario posapp y grupo www-data; Restart=on-failure.
- PrivateTmp=no, ProtectSystem=no, NoNewPrivileges=no. No existe aún evidencia de estas tres restricciones systemd; preparar ajustes después de mapear documentos, temporales, navegador/impresión y dependencia de Apache. No activarlos indiscriminadamente porque podrían interrumpir integraciones.

### Integración SUNAT privada: requisito explícito

Flujo requerido: navegador → Nginx → backend autorizado → Apache 127.0.0.1:8093 → SUNAT. La CA y los certificados no deben convertir la API PHP en un servicio público.

- El puerto 8093 ya está limitado a loopback; falta revisar proxies, redirects, upstreams e includes para descartar publicación indirecta.
- Hay tres sitios habilitados: imbasac.cloud, php-internal-localhost.conf y pos. Sus nombres no prueban que sus rutas sean privadas.
- El application-prod.yml del repositorio usa http://127.0.0.1:8093/API_SUNAT/post.php y http://127.0.0.1:8093/API_SUNAT_GUIAS. Esto acredita el diseño del archivo local, no el perfil/artefacto efectivo del VPS.
- El acceso a comprobantes/XML/CDR para usuarios debe resolverse con permisos en el backend; no abrir toda la API de SUNAT para descargar un archivo.

### Red y autenticación PostgreSQL

- UFW informa inactive. No equivale a probar que no haya otros filtros nftables/iptables/Hostinger/Tailscale.
- PostgreSQL escucha en todas las interfaces IPv4 e IPv6. La regla IPv4 final host/all/all/0.0.0.0/0/md5 permite intentar autenticación desde cualquier origen IPv4 que alcance el servicio, para todos los usuarios y bases cubiertos. No es acceso sin contraseña.
- La regla es host, no hostssl: ssl=on habilita TLS pero esa regla no lo exige por sí sola. No se afirma que una conexión concreta esté usando TLS sin comprobarla.
- Una regla md5 puede negociar SCRAM si la credencial almacenada es SCRAM; no se leyeron hashes y no se concluye que las contraseñas estén almacenadas con MD5. Migrar/restringir tras comprobar clientes, acceso administrativo, conexión del ERP y vía remota requerida por el usuario.
- La salida de reglas no presenta errores. No hay regla externa IPv6 en lo mostrado; no equiparar escucha IPv6 con autenticación remota IPv6 permitida.

### Persistencia y continuidad

- fsync=on, full_page_writes=on, data_checksums=on, wal_level=replica.
- synchronous_commit=on, synchronous_standby_names vacío y cero filas en pg_stat_replication: no hay réplica conectada observada ni espera de confirmación remota configurada en esta sesión. wal_level=replica no significa que ya exista una réplica.
- archive_mode=off: archivado WAL nativo deshabilitado. No demuestra por sí solo ausencia de pg_dump, snapshots u otro mecanismo externo; falta inventariarlos y comprobar restauración.
- Estos parámetros protegen aspectos de persistencia local; no acreditan recuperar la última venta ante pérdida completa del VPS.

Referencias: [HBA PostgreSQL 18](https://www.postgresql.org/docs/18/auth-pg-hba-conf.html), [autenticación y transición SCRAM](https://www.postgresql.org/docs/18/auth-password.html), [synchronous_commit](https://www.postgresql.org/docs/18/runtime-config-wal.html#GUC-SYNCHRONOUS-COMMIT).

### Revisión local adicional de SUNAT

La búsqueda de `:8093`, `API_SUNAT` y `sunat-files` en código/configuración del frontend no encontró referencias directas. Esto no prueba por sí solo que las rutas de Nginx sean privadas ni que el artefacto desplegado coincida. Se prepara un resumen acotado de Nginx en `ops/readiness/nginx-routing-summary.sh`, cuya primera salida ya fue recibida; el detalle de controles está pendiente.

El filtro se verificó sobre configuración sintética: conserva host/puerto upstream y omite valores de Authorization, credenciales embebidas en URL y query. Es un resumen por líneas; no acredita ausencia de publicación indirecta. Las políticas permanentes de ambos AGENTS.md incorporan el requisito de API SUNAT exclusiva del backend.

## Resumen inicial de Nginx recibido (histórico; actualizado al final)

- Sitio HTTPS imbasac.cloud/www:443 con raíz /var/www/imbasac-front. /api/ apunta al backend http://127.0.0.1:8091/.
- Los bloques de /API_SUNAT y /API_SUNAT_GUIAS están declarados en el sitio público, pero la primera herramienta omitía return/auth_request/internal. No concluir que estén permitidos ni bloqueados sin leer sus acciones.
- /sunat-files/xml/... y /sunat-files/cdr/... tienen alias a FIRMA y CDR de /var/www/apache/API_SUNAT. Faltan controles de acceso completos y contexto de los deny all anidados. No concluir que todos los archivos estén públicos ni interpretar deny all como protección de toda la descarga sin comprobar el bloque real.
- php-internal-localhost.conf escucha en 80 IPv4/IPv6 generales, aunque se llame localhost. Define server_name localhost/127.0.0.1 y allow loopback/deny all en los bloques de API PHP. server_name no es una restricción de origen; conservar control de IP y evaluar binding solo local tras comprobar dependencias.
- pos declara listen 80, server_name _ y proxy de location / al backend. La primera salida no incluye return: falta comprobar si hay una acción previa que impida el proxy. El nombre _ no lo convierte por sí mismo en default ni en sitio privado. Debe cubrirse toda ruta efectiva al backend al activar certificados; un JWT nunca sustituye la prueba TLS cuando el rol exige certificado.
- nginx.conf global enumera TLSv1/TLSv1.1/TLSv1.2/TLSv1.3; el include de Let's Encrypt del sitio HTTPS enumera TLSv1.2/TLSv1.3. No declarar TLS obsoleto negociable en ese sitio solo por la línea global; verificar contexto e inclusión efectiva antes de ajustar.
- Aún pendiente firewall Hostinger y otros filtros. No se hicieron cambios ni se recargó Nginx.

### Dependencia real de descargas en el código

SunatFilePublicUrlService genera enlaces /sunat-files/xml y /sunat-files/cdr a partir de rutas internas, usados por SaleV2RestController y ContractRestController (ventas, emisión, contratos y notas de crédito). La ausencia de URLs SUNAT literales en Angular no demuestra ausencia de consumo: el backend entrega esos enlaces. Proteger o migrar las descargas de forma coordinada, preservando permisos e impresión/documentos; no cortar los alias sin sustituir el flujo autorizado.

### Preparación del cambio, sin activación

1. Mantener API SUNAT y GUIAS solo para backend. Verificar acciones de bloqueo en cada origen, host alternativo e include.
2. Consolidar las entradas públicas del ERP: HTTPS canónico y HTTP limitado a redirección/ACME según necesidades comprobadas. Ajustar pos y el vhost interno tras verificar dependencias reales; no borrar sitios indiscriminadamente.
3. Asegurar controles backend de usuario/sesión/rol/equipo en cada operación y descarga privada; no confiar solo en un encabezado que pueda enviar el cliente. Nginx y backend deben comprobar el salto autorizado.
4. Proteger XML/CDR y decidir endpoints autorizados o auth_request con permisos por recurso antes de retirar acceso directo. Mantener imágenes/materiales realmente públicos según política definida.
5. Probar en ensayo dominios/IP/Host alternativo, usuario requerido/exento, ausencia/certificado ajeno/revocado, descarga propia/ajena y funcionamiento backend→SUNAT. No emitir documentos reales como prueba.
6. Respaldo verificable y retorno compatible; cambios productivos fuera de 07:00–21:00 America/Lima, máximo una hora.

El bloque `ops/readiness/nginx-access-controls.sh` obtiene retornos y contexto básico omitidos en el primer filtro, sin imprimir valores de headers. Sigue siendo un resumen, no acreditación definitiva de aislamiento ni de autorización de documentos.

## Controles confirmados y decisión del usuario (actualización 29 de octubre de 2026)

- La segunda salida de Nginx confirma return 403 en /API_SUNAT y /API_SUNAT_GUIAS con y sin barra final del sitio público.
- El bloque HTTP del dominio devuelve 301. El sitio pos mantiene el proxy HTTP a Java sin return previo en lo mostrado.
- Los deny all de XML/CDR están dentro de limit_except GET HEAD; restringen otros métodos, no acreditan autorización por usuario/recurso para GET/HEAD. No se muestran auth_request, auth_basic ni internal en esos bloques. Mantener la revisión de posibles controles omitidos y migrar descargas de manera coordinada.
- El usuario confirmó uso del ERP exclusivamente mediante https://imbasac.cloud. Su uso de consola corresponde al acceso administrativo, que se conserva. Esto permite preparar el cierre por IP/nombres desconocidos; no prueba por sí solo cómo se conectan todas las integraciones automáticas.
- Se preparó ops/nginx/candidates/pos-deny-unknown-hosts.conf. Su comprobación local en Nginx 1.24 Alpine pasó nginx -t y diez casos HTTP; se retiró el contenedor de ensayo. No se aplicó al VPS. Ver NGINX_HTTP_CHANGE_2026-10-29.md para el alcance, pruebas y retorno.
- Sigue pendiente recibir reglas del firewall de Hostinger.
