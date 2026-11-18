# Acciones que requieren al operador: certificados y publicación

Estado al 30 de octubre de 2026. Esta lista distingue las acciones que requieren equipo real, acceso privado/custodia o consola de producción de la preparación que se puede ejecutar localmente. No se ha publicado ni activado una política en el VPS.

Las intervenciones humanas pendientes son: probar las PC reales, custodiar la raíz offline e instalar una vez los componentes privados, aportar MMDB sólo si se desea ubicación estimada, ejecutar las consultas/copia privada desde consola y realizar la publicación nocturna con el piloto. No se necesita que el operador programe, firme cada certificado, revoque cada certificado por SSH o regenere la CRL emisora diariamente. Las incidencias extraordinarias de CA con estado incierto sí requieren revisión, sin borrar pruebas para forzar un reintento.

## 1. Piloto Windows y Mac reales

- Elegir una PC Windows y una Mac, sus versiones/navegadores y si las personas comparten cuenta del sistema operativo o sólo del ERP. Las cuentas distintas de Windows/macOS requieren comprobar almacén y acceso a la clave; un login ERP compartiendo PC no resuelve automáticamente perfiles del sistema distintos.
- Ejecutar los candidatos de [hardware](../../ops/hardware/README.md) en las PC del piloto; conservar claves privadas allí. Entregar sólo CSR/reporte público y resultados de las pruebas, nunca PFX, claves ni contraseñas.
- Completar conexión mTLS y evidencia de hardware/copia con los certificados públicos del ensayo. TPM local o Keychain no equivale a atestación remota. La vía Mac de CSR/Keychain no demuestra exclusividad física; puede necesitar gestión/ACME estándar y hardware compatible antes de aprobarse. No se contrata ni instala ese servicio automáticamente.
- La condición de clave no copiable en Mac no se considera resuelta. Si la prueba de un equipo/proveedor no acredita el requisito, conservarlo pendiente y devolver el resultado al desarrollo para resolver la integración estándar; el operador no tiene que programarla ni aprobar una garantía que no se comprobó.

## 2. Custodia inicial de CA

- Disponer de estación/disco offline fuera del VPS y custodia de contraseña para la raíz, con copias cifradas recuperables. Este material no puede generarse correctamente dentro del repositorio ni darse por custodiado por el asistente.
- Ejecutar la ceremonia preparada en [PKI](../../ops/pki/README.md): raíz offline, sólo CSR de emisora hacia ella y sólo certificados públicos/CRL de regreso. La emisora se instala bajo identidad separada en el VPS; el JAR nunca posee la clave.
- Verificar huellas por canal independiente. Guardar calendario de renovación de certificados y CRL; la CRL raíz offline también debe actualizarse antes de expirar.
- Instalar una vez el [puente de firma restringido](../../ops/pki/README.md) desde el paquete revisado, con configuración/permisos verificados en consola. Después el backend puede solicitar emisión sin que el operador firme cada certificado ni entregue la clave CA al JAR. La aprobación física sigue siendo explícita y posterior.
- Instalar y ensayar las unidades de actualización CRL; habilitar el timer sólo al finalizar la aceptación. Refresca diariamente la CRL emisora y publica mediante otra identidad. El operador no debe regenerarla a mano cada día. La CRL raíz offline sí necesita renovación periódica con custodia física. Revocación ERP, índice CA y CRL cargada en Nginx son estados distintos; la pantalla no debe afirmar sincronía instantánea de todos ellos.

## 3. Ubicación por IP: archivo opcional

- Si se desea activar la ciudad estimada en esta entrega, el propietario debe aportar un archivo **MMDB de ciudad** obtenido con licencia/autorización válida y definir su actualización. No se ha creado cuenta, contratado ni descargado una base licenciada en su nombre.
- Instalar el archivo fuera de Git, en la ruta externa configurada —por ejemplo `/var/lib/imbasac/geoip/GeoLite2-City.mmdb`—, legible por el usuario del backend y sin credenciales del proveedor en Angular. Revisar la confianza de la IP antes de activar GeoIP.
- Es opcional: con `security.geoip.enabled=false` el sistema no necesita ese archivo. La ausencia de ciudad no debe bloquear ni sumar strikes; la decisión de equipo se apoya en certificado y autorización. Una ciudad estimada no prueba otra PC. Si se activa sin archivo válido, la configuración exige corregirlo antes de iniciar el servicio.

## 4. Comprobaciones privadas de la entrega

- Desde consola Hostinger, ejecutar el [preflight acotado](../../ops/deploy/README.md) con el nombre real de la base y aportar únicamente el informe saneado. El asistente no tiene identidad SSH autenticada ni acceso al panel/consola del operador.
- Corregir selección de rutas reales y generar una copia privada del esquema/datos necesaria para ensayar. No subir base, secretos ni configuraciones completas al chat. El ensayo se realiza en equipo controlado y puede usar datos saneados cuando corresponda.
- Elegir destino propio fuera del VPS para el respaldo cifrado, conservar contraseña separada, copiar y verificar checksum. No requiere contratar otro VPS. La prueba de restauración PostgreSQL aislada está preparada; la recuperación de aplicación/archivos necesita esa copia real para comprobarse.

Las **seis migraciones de esta entrega** se comparan con el esquema real y se ensayan en este orden; existir en el repositorio no significa que estén aplicadas:

1. [Base de equipos y política](../../src/main/resources/db/scripts/20261029_device_access_foundation.sql).
2. [Auditoría de accesos](../../src/main/resources/db/scripts/20261029_access_audit.sql).
3. [Cupos, strikes y bloqueos](../../src/main/resources/db/scripts/20261030_access_control.sql).
4. [Certificados, homologación y sesiones](../../src/main/resources/db/scripts/20261030_device_certificates.sql).
5. [Outbox de revocación CA](../../src/main/resources/db/scripts/20261030_ca_revocation_outbox.sql).
6. [Permiso de comprobantes SUNAT privados](../../src/main/resources/db/scripts/20261030_private_sunat_files.sql).

No ejecutar todo el historial SQL ni volver a ejecutar la base de equipos de 20261029 después de la migración de cupos: su índice original de una PC fue reemplazado para admitir excepciones administrativas. Reaplicarlo sobre una entrega posterior puede fallar o restituir una restricción anterior. Verificar cada versión/destino; las migraciones no conceden automáticamente permisos a personas ni activan flags. El propietario selecciona las cuentas administrativas y usuarios del piloto; los permisos reales se asignan conforme a esa decisión, sin concederlos a todos los CLIENTE.

## 5. Ventana productiva y decisión de acceso

- Definir una noche fuera de 07:00–21:00 America/Lima y ejecutar el procedimiento preparado sólo después de homologación y ensayo, con máximo una hora y decisión de retorno a los 40 minutos.
- El corte, pausa de escritores/jobs, respaldo, migraciones y recarga/servicios productivos requieren consola autorizada. No se envían contraseñas al asistente ni se desactiva verificación SSH para evitarlos.
- Aprobar equipos/políticas del piloto con evidencia, usuarios/cupos reales y permisos administrativos. Conservar el hash para roles todavía no migrados; no usarlo como fallback para un rol ya obligado a certificado.

La activación actual es por rol completo. El ensayo de obligatoriedad con pocas cuentas se realiza primero en entorno aislado. No cambiar CLIENTE a certificado en producción si quedan clientes afectados sin preparar. Un piloto obligatorio productivo sólo para algunas cuentas del mismo rol requiere desarrollo adicional de selección administrativa explícita; no se ha implementado y no se simula modificando roles comerciales.

## Independiente de esta entrega

Sigue pendiente elegir persistencia durable fuera del VPS para garantizar última venta confirmada ante pérdida total del servidor. Un backup nocturno no cubre las ventas del día siguiente. No impide programar/probar certificados localmente, ni debe informarse como garantía cumplida.

WhatsApp permanece como evaluación posterior; no se han enviado mensajes, contratado proveedores ni hecho depender acceso/ventas de ese canal.

## Evidencia de las herramientas locales

Las pruebas locales de PKI usan claves de software temporales y comprueban perfiles, SAN servidor, firma, no sobrescritura, serialización, revocación y CRL. Las de respaldo/restore comprueban selección segura, aislamiento y limpieza de recursos propios. Estas pruebas no homologan TPM/Mac ni aplican SQL en producción. Registrar resultados exactos de la entrega y fecha junto al informe principal; no inferir una restauración productiva a partir de un ensayo sintético.

Evidencia ejecutada localmente el 30 de octubre:

- 14 pruebas PKI con OpenSSL: aprobadas en Windows y Linux aislado; incluyen CSR P-256 válido y rechazo de P-384. El caso Windows también preparó PKCS12 y truststore mediante Java 21 `keytool`.
- 11 pruebas del puente de firma/revocación: aprobadas en Windows y Linux aislado, con firma real OpenSSL, repetición estable, rechazo de otro emisor/equipo/PEM privado y recuperación de respuesta perdida sin reemisión; índice CA revocado `R` comprobado.
- 8 pruebas de mantenimiento CRL en Linux aislado: vigencia/firma, raíz vencida o futura, emisor incorrecto, permisos, publicación atómica y retorno ante timeout de Nginx. No se ejecutaron servicios/timers en el VPS ni se acredita ejecución real de systemd por esos ensayos.
- 1 ensayo Linux aislado de instalación y sudo real: wrapper/configuración root-owned, app sin lectura de clave, sin shell ni argumentos arbitrarios, variables de entorno del solicitante ignoradas, firma y repetición aprobadas.
- 7 pruebas de herramientas de entrega: aprobadas en Linux aislado; GPG cifró/descifró un paquete sintético real y se verificó que el destino no conserva archivos de trabajo en claro. La comprobación de reloj exige sincronización efectiva, no sólo éxito del comando.
- 1 ensayo de `pg_dump`/`pg_restore` real con PostgreSQL 18: aprobado en contenedores nuevos, sin red ni puertos de host, con datos sintéticos. Eliminados exclusivamente sus contenedores/volúmenes.
- 1 integración real Nginx 1.24: aprobada con TLS navegador–Nginx, mTLS Nginx–servidor de prueba, pin y URI de servicio, sustitución de headers falsos, certificados de dispositivo RSA y EC P-256 válidos y rechazos de credencial de otra CA/acceso directo sin certificado interno. El servidor de prueba no sustituye el ensayo conjunto de Spring Boot y Angular.
- Sintaxis del candidato PowerShell: sin errores. No se generó una clave TPM real ni se ejecutó enrolamiento en Mac.
