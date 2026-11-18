# Checklist vigente de certificados, accesos y continuidad de IMBASAC

Estado al **30 de octubre de 2026**. Se conserva el nombre histórico del archivo para no romper enlaces. Este checklist reemplaza su diagnóstico anterior: varios módulos que entonces estaban pendientes ya están preparados y probados localmente.

**Leyenda:** `[x]` significa trabajo preparado o comprobado localmente, con el alcance indicado; nunca significa publicado. `[ ]` significa pendiente real de ingeniería, aceptación, operación o producción. Las propiedades nuevas permanecen desactivadas por defecto; no se han aplicado migraciones ni activado controles en el VPS.

Referencias: [informe de implementación y pruebas](IMPLEMENTATION_REPORT_2026-10-30.md), [reglas acordadas](ACCESS_RULES_2026-10-30.md) y [acciones que requieren al operador](OPERATOR_ACTIONS_2026-10-30.md). Las pruebas locales detalladas se documentan en el informe; una compilación no homologa hardware ni prueba recuperación productiva.

## 1. Reglas acordadas e implementadas localmente

- [x] Rechazar desde el primer intento un equipo no autorizado cuando el rol exige certificado. Los primeros strikes no conceden acceso.
- [x] Umbral inicial de **tres strikes**, configurable mediante `security.access-control.strike-threshold`.
- [x] Acumulación **sin vencimiento ni reinicio mensual** hasta el umbral. El mes de bloqueo es un plazo diferente.
- [x] Bloqueo inicial de **un mes**, configurable por administrador desde pantalla; desbloqueo anticipado con permiso, motivo y auditoría.
- [x] Nuevo ciclo después de expiración o desbloqueo, preservando historial y sin volver a sancionar por los mismos strikes.
- [x] Sanción individual del cliente, sin bloquear automáticamente la PC compartida ni a sus demás usuarios.
- [x] Una PC por defecto, excepciones administrativas de cupo y varios clientes autorizados por PC con login individual. Cada PC tiene identidad/certificado propios; la excepción no permite copiar una credencial.
- [x] Strikes de equipo sólo después de comprobar credenciales y contexto confiable; identificadores de intento y control de concurrencia. Ciudad distinta sola y contraseña incorrecta no consumen este contador.
- [x] Alcance inicial CLIENTE, Windows y Mac sujetos a homologación. Móviles y certificado personal siguen fuera de esta etapa.

Una ciudad aproximada no prueba otra PC: proveedores, VPN y viajes pueden cambiarla. [Limitaciones de GeoIP](https://support.maxmind.com/knowledge-base/articles/maxmind-geolocation-accuracy). Una PC no autorizada en la misma ciudad también se rechaza.

## 2. Software y herramientas ya preparados

- [x] Dashboard de accesos, sesiones, filtros, fechas Lima, exportación CSV y revocación exacta, con permisos separados y protección de contexto compartido. El historial agregado no es la autoridad del contador de strikes ni representa presencia exacta.
- [x] Pantallas y API de equipos, cupos/excepciones, vínculos, strikes, bloqueo/desbloqueo, certificados, solicitudes CSR, homologación explícita y políticas por rol.
- [x] Estado durable y transacciones para sanciones y cupos, con historial y comprobaciones de concurrencia en PostgreSQL aislado.
- [x] Identidad confiable del proxy mediante **mTLS interno, pin SHA256 y URI de servicio**; IP/encabezados sustituidos por Nginx, sin aceptar identidad de certificado enviada libremente por Angular.
- [x] Perfiles Nginx de certificado cliente opcional en TLS para convivir con roles exentos; la obligación y autorización individual se resuelven en backend. Candidato para cerrar la entrada HTTP alternativa, sin publicar.
- [x] Validación de certificado público, SAN UUID del dispositivo, CSR y coincidencia de clave; perfiles RSA3072/4096 y EC P-256 admitidos según comprobaciones de servidor. El certificado válido por sí solo no concede acceso a cualquier usuario.
- [x] Herramientas CA OpenSSL estándar, raíz offline, emisora separada en el mismo VPS y puente restringido de emisión/revocación. JAR y PostgreSQL no almacenan claves CA ni reciben la contraseña emisora.
- [x] Outbox durable de revocación hacia la CA, reintentos acotados, estados pendientes/de revisión y operación idempotente. La revocación ERP no espera a la publicación de CRL para denegar peticiones protegidas.
- [x] Helper de generación/publicación CRL, dos identidades de servicio y timer diario preparados **sin habilitar**. Verificación de cadena, firmas y vigencia, publicación atómica y retorno ante fallo/timeout. Que la pantalla confirme revocación no significa que Nginx ya cargó una CRL nueva.
- [x] Sesiones asociadas a estado durable, epoch y vínculo/huella exactos; controles locales de revocación y cambios restrictivos. Esto no acredita todavía toda la modernización de refresh/cookies descrita más abajo.
- [x] Flujo SSE de notificaciones de comunidad con **Bearer en encabezado**, sin token en URL, y revalidación de sesión, vencimiento y restricciones. Ensayos locales no sustituyen el comportamiento real del proxy/navegador.
- [x] GeoIP local mediante MMDB, desactivado y sin llamadas HTTP externas durante login; información estimada de país/región/ciudad, precisión y estado desconocido. Falta aportar datos licenciados reales si se decide activarlo.
- [x] Descargas XML/CDR por endpoint ERP autenticado, permiso `VIEW_SUNAT_DOCUMENTS` y comprobaciones comerciales correspondientes; integración del navegador y preparación para cerrar aliases públicos. Apache/SUNAT sigue reservado al backend.
- [x] Herramientas de preflight dirigido, respaldo GPG cifrado y restauración PostgreSQL aislada, junto con procedimientos nocturnos y retorno compatible. El ensayo realizado usa datos sintéticos.
- [x] Candidatos Windows TPM y diagnóstico Mac Keychain, con límites explícitos. No producen atestación remota ni demuestran automáticamente exclusividad física.

## 3. Pendientes para preparar el destino real

- [ ] Confirmar desde consola Hostinger versiones/artefactos realmente desplegados, perfil activo, nombre de base, rutas, permisos y esquema efectivo. Aprovechar el inventario recibido; no repetir una auditoría completa ni imprimir secretos.
- [ ] Verificar firewall Hostinger/Ubuntu, conexiones PostgreSQL legítimas, IPv4/IPv6, hosts alternativos, HTTPS efectivo y acceso administrativo de recuperación antes de restringir entradas.
- [ ] Ensayar las seis migraciones contra una copia protegida del esquema real y comprobar compatibilidad/locks/retorno. Orden: **foundation → audit → access control → device certificates → CA outbox → private SUNAT**:

  1. [20261029_device_access_foundation.sql](../../src/main/resources/db/scripts/20261029_device_access_foundation.sql).
  2. [20261029_access_audit.sql](../../src/main/resources/db/scripts/20261029_access_audit.sql).
  3. [20261030_access_control.sql](../../src/main/resources/db/scripts/20261030_access_control.sql).
  4. [20261030_device_certificates.sql](../../src/main/resources/db/scripts/20261030_device_certificates.sql).
  5. [20261030_ca_revocation_outbox.sql](../../src/main/resources/db/scripts/20261030_ca_revocation_outbox.sql).
  6. [20261030_private_sunat_files.sql](../../src/main/resources/db/scripts/20261030_private_sunat_files.sql).

- [ ] Verificar qué scripts ya están aplicados y ejecutar sólo la transición necesaria. **No reaplicar foundation después de cupos**: su índice original de una PC fue reemplazado para admitir excepciones. No ejecutar todo el historial SQL.
- [ ] Elegir cuentas administrativas/piloto y asignar permisos reales de consulta, exportación, equipos, homologación, política, emisión, desbloqueo y comprobantes. Los SQL no conceden automáticamente permisos a personas ni activan propiedades.
- [ ] Preparar perfil y secretos externos, identidades internas, pins/rotación y rutas reales de SUNAT. No copiar una plantilla de TLS como si ya existieran sus certificados/almacenes.

## 4. Hardware, custodia e instalación privada

- [ ] Ejecutar la prueba en Windows y Mac representativos, con navegadores y perfiles OS reales: creación local de clave, instalación pública, mTLS, protección contra copia y cambio de persona del ERP.
- [ ] Acreditar la protección mediante un mecanismo estándar verificable. PFX, CSR ordinario, flag de instalador o Keychain no prueban que la clave pertenezca exclusivamente a una PC física.
- [ ] Resolver la integración que revele la prueba de hardware. **Mac con clave no copiable no está resuelto**; si necesita ACME/gestión/atestación estándar y hardware compatible, queda trabajo de ingeniería, no una aprobación que deba inventar el operador. P-256 habilitado no demuestra Secure Enclave.
- [ ] Completar custodia real de raíz offline, copias cifradas, huellas independientes y ceremonia emisora. Mantener clave/contraseña/base raíz fuera del VPS.
- [ ] Instalar una vez el puente CA, configuración/permisos, servicios y confianza interna en la consola autorizada; probar identidad de servicio y denegación de lectura de claves por ERP.
- [ ] Probar en el VPS la ejecución real de CRL/systemd y habilitar el timer únicamente después de validar primer bundle y aceptación. Mantener renovación offline de CRL raíz y vigilancia de fallos/vigencias.
- [ ] Si se activa GeoIP, aportar MMDB de ciudad con licencia válida y definir actualización fuera de Git. Es opcional; `security.geoip.enabled=false` permite seguir sin ese archivo. No se crean cuentas/contratos en nombre del propietario.

La firma y revocación normales de cada certificado se pueden solicitar desde backend; no se deja una tarea SSH al operador por cada certificado. La homologación física y las incidencias extraordinarias de CA siguen requiriendo evidencia y revisión.

## 5. Aceptación real, recuperación y publicación

- [ ] Ensayar frontend y Spring Boot juntos con el esquema/configuración reales protegidos: roles obligados/exentos, política ausente, equipo ajeno, varios clientes por PC y excepciones concurrentes de cupo.
- [ ] Probar instalación/renovación/revocación, reintentos y estado incierto de firma/outbox; restricción después de cambiar usuario, contraseña, rol o política; bloqueo/desbloqueo y expiración sin revivir sesiones antiguas.
- [ ] Probar SSE detrás de Nginx, pestañas y cambio de persona, respuestas tardías, credenciales vencidas y certificados copiados. No confundir una prueba de clave software con homologación de hardware.
- [ ] Probar IP real, encabezados falsos, VPN, ciudad desconocida y archivo MMDB si se incorpora. Observar falsos positivos antes de activar sanciones para un piloto.
- [ ] Ensayar comprobantes XML/CDR con permisos/cuenta propios y ajenos, nombres/rutas inválidos y aliases públicos cerrados; conservar API SUNAT inaccesible desde navegador e IP alternativa.
- [ ] Ejecutar regresión comercial completa con datos de ensayo: ventas, pagos, caja, stock, correlativos, archivos, impresión y SUNAT simulado; conciliar respuestas perdidas sin duplicar ni emitir pagos/comprobantes fiscales reales en producción.
- [ ] Tomar respaldo real protegido, copiarlo fuera del VPS y comprobar restauración de PostgreSQL **más aplicación, configuración y archivos** en entorno aislado. La prueba sintética ya realizada no valida esa copia real.
- [ ] Medir preparación/corte/recuperación y retorno compatibles. Detener/drenar también jobs e integraciones; no restaurar una base antigua para deshacer un JAR ni borrar operaciones posteriores.
- [ ] Preparar paquetes finales, checksums, migraciones puntuales, configuración externa, monitoreo y versión de retorno que conserve las restricciones vigentes.
- [ ] Publicar por etapas fuera de **07:00–21:00 America/Lima**, con máximo una hora y decisión de retorno a los 40 minutos. Mantener el dominio `imbasac.cloud`.
- [ ] Ejecutar piloto con pocas cuentas CLIENTE y equipos homologados, revisar soporte/vencimientos/falsos positivos y ampliar después. Conservar validación previa para roles no migrados; un rol obligado a certificado no vuelve automáticamente al hash.
- [ ] Tener presente el alcance real: la obligatoriedad actual cambia el rol completo. Ensayar el piloto requerido en entorno aislado y preparar todos los CLIENTE afectados antes del cambio productivo. Si se requiere un piloto obligatorio productivo parcial dentro del mismo rol, desarrollar primero una selección administrativa explícita; no está implementada.
- [ ] Comprobar vigilancia de disco, respaldos, outbox pendiente, CA/CRL/HTTPS y capturas de auditoría; entregar procedimientos de soporte y recuperación.

## 6. Ingeniería que continúa pendiente

No todas las tareas restantes son acciones del operador. Además de lo que pueda exigir el piloto de hardware, queda trabajo técnico:

- [ ] Modernizar refresh: cookies HttpOnly/Secure, CSRF, rotación/reutilización, vencimiento absoluto y coordinación entre pestañas. El contrato heredado aún devuelve credenciales al cliente y utiliza almacenamiento del navegador; no marcarlo sustituido por el guard durable.
- [ ] Definir retención y construir purga acotada, capacidad, índices y alertas de la auditoría/estados auxiliares. No hay purga automática acreditada.
- [ ] Completar protección integral contra fuerza bruta por cuenta/origen y ataques distribuidos. El cooldown de incidentes y los strikes por PC no sustituyen límites de intentos de contraseña ni toda la protección contra bloqueo malicioso de víctimas.
- [ ] Completar la matriz de autorización de usuarios, roles y negocio, y revisar archivos/blobs/PDF/media/impresión/integraciones de los demás módulos. Haber protegido XML/CDR no acredita que todos los archivos del ERP estén resueltos.
- [ ] Revisar aislamiento de formularios/caches y respuestas tardías de los demás módulos en PC compartida; no extrapolar las correcciones de seguridad a toda la aplicación comercial.
- [ ] Verificar recuperación de restricciones y revocaciones vigentes después de restaurar, con evidencia independiente y reconciliación. No reabrir certificados autorizados sólo porque una copia antigua lo indica.

## 7. Continuidad de última venta: frente separado

- [ ] Elegir destino durable independiente para conservar hasta la última venta confirmada ante pérdida total del VPS. Aún no existe; una conexión remota a PostgreSQL no es otra copia.
- [ ] Completar persistencia síncrona verificada, copia base/WAL externa, protección de archivos/eventos críticos, aislamiento del escritor anterior y conciliación de operaciones inciertas.
- [ ] Ensayar pérdida del primario y recuperación completa, probar última venta confirmada y medir tiempos con el destino real disponible.

El respaldo nocturno protege el punto de corte y los datos históricos. No conserva automáticamente las ventas del día siguiente ante pérdida total del servidor. **RPO cero y recuperación completa en una hora no están acreditados**. Este frente no impide el desarrollo local de certificados y no debe presentarse como resuelto por pruebas de frontend o copias asíncronas.

## 8. Ampliaciones fuera de la primera activación

- [ ] Evaluar WhatsApp: módulo/agente existentes, proveedor/API oficial, cuenta/número, requisitos, costos y destinatarios autorizados. No se ha contratado ni enviado mensajes.
- [ ] Si se decide implementarlo, diseñar cola/outbox, reintentos, deduplicación, límites de frecuencia y entrega; WhatsApp no decide bloqueos ni condiciona login o ventas.
- [ ] Evaluar móviles y certificado personal en otra etapa; no habilitarlos automáticamente.

**Orden inmediato:** inventario dirigido y esquema real, prueba física en paralelo con custodia/instalación CA, permisos/configuración, aceptación y respaldo restaurado, y piloto/publicación nocturna. MMDB es opcional. Las tareas de ingeniería abiertas conservan su estado pendiente y no se trasladan al operador como si fueran simples comandos por ejecutar.
