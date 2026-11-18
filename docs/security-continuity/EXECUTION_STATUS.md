# Ejecución de certificados y continuidad — entrega inicial

**Estado vigente al 30 de octubre:** ver [informe de implementación local](IMPLEMENTATION_REPORT_2026-10-30.md), [checklist actualizado](REMAINING_WORK_CHECKLIST_2026-10-29.md) y [acciones del operador](OPERATOR_ACTIONS_2026-10-30.md). Los apartados fechados anteriores conservan la historia; sus pendientes de CA, mTLS, administración y SSE fueron desarrollados localmente después. Ninguna migración ni activación se realizó en producción.

Fecha: 23 de octubre de 2026. Estado: código local comprobado; plan completo en curso, sin publicación ni cambios de producción. No se ha emitido una CA ni activado mTLS.

## Resultado de esta entrega

### Sesiones del backend

- JWT de acceso con emisor, audiencia, propósito, tipo, vencimiento, jti y referencia de sesión exacta. El principal conserva el username usado por los controladores actuales.
- La referencia pública `sid` se deriva mediante SHA-256 con separación de dominio de la credencial UUID de refresh. Nunca se publica esa credencial como claim. Redis conserva la referencia derivada, no la credencial de refresh.
- Autenticación exige que coincidan ambos índices de la sesión vigente y el usuario esté habilitado. Un token de un login anterior no recupera acceso al iniciar otro login.
- Reemplazo, lectura/renovación y eliminación de sesiones atómicos en Redis. Logout antiguo no elimina la sesión nueva y refresh antiguo no extiende sus índices. Ante indisponibilidad del repositorio no se concede autenticación.
- Refresh comprueba nuevamente existencia y estado habilitado del usuario y no genera token si la sesión ya fue reemplazada/eliminada.
- Se retiraron los mensajes del parser que podían incluir material de autenticación. El identificador de refresh tampoco aparece en los errores de sesión nueva.
- Los objetos User dejan de serializar el campo password. Las actualizaciones siguen aceptando el contrato existente.

### Frontend coordinado

- Interceptor limitado al origen y ruta de la API configurada. Un recurso externo o un prefijo parecido no recibe automáticamente el Bearer ni provoca refresh/logout.
- Renovaciones concurrentes comparten solicitud por sesión. Los errores comerciales posteriores a renovar no eliminan una sesión válida.
- Respuestas tardías de refresh/logout no reemplazan ni borran las credenciales del siguiente usuario.
- La edición de usuarios deja de leer el hash y de imprimir contraseña/hash; envía null para conservar contraseña, compatible con COALESCE/NULLIF del repositorio actual.
- Nuevo comando `npm run test:security`.

### Preparación de continuidad

- Inventario Ubuntu y diagnóstico SQL acotado en `ops/readiness`. No vuelca secretos ni altera servicios/base. Tiene límites de espera y conserva como no verificadas las garantías RPO/RTO.
- Se revisó y conservó la idempotencia de ventas ya existente. No se creó una segunda tabla/journal ni se alteraron los SQL pendientes del usuario.
- Las pruebas existentes de idempotencia y reasignación de reservas se ejecutaron en PostgreSQL desechable, incluyendo concurrencia, rollback y respuesta perdida.

## Evidencia local ejecutada

| Comprobación | Resultado |
| --- | --- |
| Java 21 / Maven instalado, compilación | Correcta |
| JsonWebTokenUtilsTest | 4 correctas |
| SecurityAuthFilterTest | 6 correctas |
| SessionSecurityTest | 5 correctas |
| UserPasswordSerializationTest | 1 correcta |
| RedisSessionRepositoryIntegrationTest en Redis 7.4 aislado | 6 correctas, sin omisiones |
| CreateIdempotentSaleV2ServiceTest existente | 9 correctas |
| PostgresSaleCreateIdempotencyIntegrationTest existente en PostgreSQL 16.15 aislado | 9 correctas, sin omisiones |
| PostgresStockReservationReassignmentIntegrationTest existente en PostgreSQL 16.15 aislado | 16 correctas, sin omisiones |
| Idempotencia y reasignación de reservas repetidas en PostgreSQL 18.6 aislado | 25 correctas, sin omisiones |
| Diagnóstico y consultas acotadas de pg_settings/pg_hba/replicación en PostgreSQL 18.6 aislado | Correctos; no son resultados del VPS |
| Frontend: test:security | 16 correctas en ChromeHeadless |
| Frontend: test:stock existente | 69 correctas en ChromeHeadless |
| Frontend: build de producción | Correcta |
| Python: comprobador de inventario | 6 correctas |
| SQL de diagnóstico sobre PostgreSQL 16.15 aislado | Correcto; detectó primario sin réplica |

No se lanzó @SpringBootTest ni se usaron perfiles con conexiones de producción. Los servicios de prueba no acreditan la versión/configuración del VPS. El wrapper Maven sigue incompleto; se usó Maven 3.9.10 instalado.

## Compatibilidad y publicación pendiente

1. Esta entrega cambia el formato JWT y el espacio Redis a `auth:v2:*`. Los tokens y refresh antiguos no se migran: hay que iniciar sesión de nuevo. El contrato JSON login/refresh/logout conserva sus nombres y el UUID de refresh.
2. Validar previamente en un entorno de ensayo el login real de cada rol, edición de usuario, renovación, logout, SSE, caja y reintento de una venta pendiente. No probar pagos reales para ensayar autenticación.
3. Publicar frontend y backend coordinados fuera de 07:00–21:00 America/Lima, con intervención máxima de una hora, preservando el journal/idempotencia y avisando del nuevo login. No limpiar localStorage completo, tablas de ventas ni todo Redis.
4. No combinar instancias antiguas y nuevas del JAR: el formato de sesión cambió. No volver a un JAR vulnerable como rollback habitual. Preparar un mínimo de retorno que conserve esta corrección; un problema ajeno debe revertirse sobre ese mínimo. No restaurar una base antigua para deshacer código.
5. Esta entrega no incorpora ninguna migración nueva de PostgreSQL. Los scripts de idempotencia/reservas encontrados siguen siendo manuales y su aplicación en producción no se ha comprobado.
6. Antes de publicar hay que confirmar respaldo recuperable, topología, accesos y configuración real. No hay aún una entrega desplegada.

## Límites abiertos, sin garantía implícita

- Redis sigue siendo la autoridad transitoria de sesiones; faltan sesiones durables en PostgreSQL, revocación durable, versiones de seguridad, diario externo y reconciliación al restaurar. Una copia restaurada de Redis no debe aceptarse como prueba de vigencia.
- El UUID de refresh sigue siendo el contrato heredado, con TTL deslizante y sin rotación/cookie. Faltan credenciales opacas de mayor entropía, rotación/reutilización, vencimiento absoluto, cookie HttpOnly/Secure/SameSite, CSRF y bootstrap coordinado. Los tokens siguen en localStorage en esta entrega.
- La comprobación enabled bloquea mientras el usuario está deshabilitado; aún falta revocación durable al cambiar contraseña, rol, permisos o enabled, incluyendo carreras entre PostgreSQL y Redis. No se declara resuelto el ciclo completo de baja/reactivación.
- No está resuelta toda respuesta HTTP tardía, sincronización entre pestañas, caché, archivos descargados ni los formularios de cada módulo en PC compartida. Aquí se corrigieron específicamente refresh/logout.
- SSE mantiene temporalmente token en query exclusivamente para GET `/community-notifications/stream`. Debe migrarse con el contrato de sesión y retirarse la excepción; el proxy tampoco debe registrar esos argumentos. Un SSE ya abierto aún necesita cierre/revalidación al revocar o deshabilitar.
- El token local heredado CLIENTE no constituye vínculo físico. La entrega inicial no incorporó políticas por rol; la base local del 29 de octubre, descrita debajo, prepara ese control y el registro usuario–PC. Siguen pendientes la autorización efectiva por dispositivo, validación del certificado y prueba de posesión en TLS.
- Faltan CA emisora aislada, raíz fuera del VPS, enrolamiento con prueba verificable de hardware, renovación, revocación y protección del salto Nginx–backend.
- No se han homologado Windows/TPM, Mac/Secure Enclave ni instaladores. Móviles siguen fuera de la primera etapa; un PFX no satisface el vínculo a una sola computadora.
- No se afirma RPO cero ni recuperación en una hora. No hay destino externo definido, réplica durable comprobada, copia base/WAL externa, ensayo de pérdida del primario, fencing ni reconciliación de archivos e integraciones. No se ha implementado/verificado outbox completo de efectos externos.

## Dependencias concretas encontradas

**Acceso (histórico, actualizado debajo):** el usuario confirmó OpenSSH y `ssh root@76.13.81.60`. El intento de esta sesión con validación estricta de identidad no pudo completarse: known_hosts no es legible y no hay clave privada en la carpeta predeterminada ni agente disponible. No se desactivó la validación, no se cambiaron permisos locales ni se enviaron credenciales. Hace falta un canal autenticado y la identidad del servidor verificada por una vía confiable. No introducir contraseñas o claves privadas en el chat/repositorio.

**Inventario por consola:** recibido el 23 de octubre de 2026; ver [inventario del VPS](VPS_INVENTORY_2026-10-23.md). Se confirmó PostgreSQL 18.1, unidad pos-backend.service bajo posapp/www-data y escuchas locales; Apache/SUNAT solo loopback 8093. Se recibieron parámetros PostgreSQL y reglas HBA; UFW inactivo, regla IPv4 amplia, sin réplica observada ni archivado WAL nativo. La API SUNAT debe permanecer exclusivamente detrás del backend. La vía elegida para continuar es la consola web de Hostinger: la falta de SSH automatizado no bloquea las consultas manuales. Quedan firewall Hostinger/otros filtros, configuración completa de rutas Nginx, esquema y respaldos; no se ha demostrado exposición pública efectiva ni persistencia externa.

**Continuidad:** el usuario confirmó que no tiene aún destino externo; se conecta a la base desde otra computadora. Esa conexión es acceso remoto, no otra copia durable. Seleccionar un destino independiente y el régimen de almacenamiento antes de configurar sincronía. No se contrató servicio alguno.

**Hardware:** hacen falta un equipo Windows y un Mac representativos de los clientes y una prueba real de generación/atestación y uso por los navegadores objetivo. No habilitar exigencia de certificado antes de tener enrolamiento recuperable y soporte operativo.

## Siguientes puertas del plan

| Fase | Estado y salida requerida |
| --- | --- |
| F0 inventario vigente | Parcial: inventario básico del VPS recibido por consola; servicio y parámetros PostgreSQL revisados; rutas Nginx, firewall Hostinger/otros filtros, exposición efectiva, esquema y respaldos pendientes |
| F1 continuidad | Parcial: idempotencia local probada; destino externo, backups/restauración y fallo controlado pendientes |
| F2 prueba de equipo físico | Pendiente de hardware y mecanismo de atestación homologado |
| F3 diseño definitivo | Cerrar con el inventario y resultados de F1/F2; conservar PC compartida, una PC activa por CLIENTE y tipo personal futuro |
| F4 seguridad backend | Bases de sesión endurecidas y política por rol preparada localmente, desactivada; PKI, autorización efectiva usuario–PC, sesiones durables y diario pendientes |
| F5 confirmación | Flujo existente comprobado en pruebas locales; durabilidad real e integraciones pendientes |
| F6 infraestructura | Sin activación; exige inventario y recuperación verificables |
| F7 frontend | Correcciones de sesión/contraseña locales; falta UI de enrolamiento/administración y nuevo contrato de sesión |
| F8–F10 aceptación/piloto/expansión | Pendientes; sin certificado obligatorio para clientes todavía |

La autorización de ejecutar el plan permite continuar el trabajo acordado cuando se satisfagan sus dependencias; no hace falta volver a pedir autorización para repetir estas comprobaciones locales. Las garantías se registrarán solo con evidencia real.

## Revisión posterior de entrada Nginx y SUNAT

Se recibió el primer resumen de rutas: /api/ hacia Java local 8091, PHP/SUNAT vía un vhost con allow loopback/deny all y un sitio pos que declara proxy al backend en HTTP 80. También existen alias para XML/CDR utilizados por el backend para generar enlaces. Quedan por confirmar retornos/controles omitidos por el filtro y las reglas del firewall de Hostinger. La preparación contempla todas las entradas y la continuidad de las descargas; no se publicaron cambios. Ver VPS_INVENTORY_2026-10-23.md.

## Preparación HTTP del 29 de octubre de 2026

El usuario confirmó uso exclusivo del dominio para el ERP. Se recibieron retornos 403 de API SUNAT, redirección HTTP 301 del dominio y proxy HTTP activo declarado en pos. Se preparó un candidato local para sustituir solo pos por rechazo predeterminado IPv4/IPv6, conservando los sitios explícitos del dominio y localhost. Validación local: nginx -t y diez casos de enrutamiento correctos en Nginx 1.24 Alpine aislado. El alcance y las validaciones pendientes con la configuración completa del VPS se detallan en NGINX_HTTP_CHANGE_2026-10-29.md. No se modificó producción y la regla de API SUNAT privada sigue vigente.

## Base de política y dispositivos del 29 de octubre de 2026

Se añadió un módulo local de política por rol con modos PASSWORD_ONLY, LEGACY_BROWSER_HASH y CERTIFICATE_REQUIRED. Está desactivado por defecto y conserva el acceso actual. Con el módulo activado, login, renovación y nuevas peticiones autenticadas comprueban la política; CERTIFICATE_REQUIRED rechaza mientras no exista el canal seguro. Nunca sustituye un certificado requerido por el hash.

Esta entrega posterior sí agrega un script manual de PostgreSQL, todavía sin aplicar al VPS: registro de dispositivos Windows/Mac, vínculo de varios usuarios a una PC con una única asignación activa por usuario, metadatos públicos de certificados y política por rol. No hay todavía enrolamiento, CA ni autenticación real mediante certificado.

Validación de esta entrega: 36 pruebas unitarias/web de backend y siete de integración correctas en PostgreSQL 18.6 desechable; frontend con 28 pruebas de seguridad y 69 existentes de stock correctas, más compilación de producción. No se arrancó la aplicación con perfiles reales ni se modificó producción. Ver [detalle, límites y secuencia pendiente](DEVICE_POLICY_FOUNDATION_2026-10-29.md).

## Dashboard de accesos: alcance explícito

Se incorporó el requisito del propietario de consultar quién ingresó y a qué hora por día. El plan ya contemplaba auditoría y sesiones; se concretó [el alcance del dashboard de accesos](ACCESS_DASHBOARD_SCOPE.md), con resumen diario, historial, intentos rechazados, sesiones, contexto de PC/certificado cuando se verifique y cambios administrativos. Esta pantalla y la captura durable que necesita todavía no están implementadas. Puede avanzarse con autenticación actual antes de la CA y deberá verificarse antes del piloto. La retención, permisos y semántica de presencia/cierre quedan expresamente definidas como tareas, sin atribuir a Redis un historial que no conserva.

## Dashboard de accesos implementado localmente

Entrega posterior del 29 de octubre: se implementaron captura, registro de sesiones/eventos PostgreSQL, resumen Lima, consultas paginadas, CSV y revocación exacta, con tres permisos independientes. Angular incorpora Seguridad · Accesos, filtros, detalle, sesiones, confirmación y limpieza al cambiar de persona. La historia distingue concesión del servidor de validación declarada del navegador; no demuestra presencia real ni equipo físico.

El estado “pendiente” del apartado anterior describe la definición inicial; ver [implementación, pruebas, habilitación y límites actuales](ACCESS_DASHBOARD_IMPLEMENTATION_2026-10-29.md). La migración sigue siendo manual y sin aplicar al VPS, la propiedad está desactivada por defecto y no se publicó. Antes de activar faltan ensayo integrado, recuperación comprobada, retención/mantenimiento, permisos concretos y validación del proxy; la CA y captura de sus eventos siguen pendientes.

## Entrega local integrada del 30 de octubre

Se implementaron cupos de PC con excepciones administrativas, tres strikes sin vencimiento, bloqueo por meses configurable, desbloqueo con nuevo ciclo e historial, sesión durable vinculada a época y autorización concreta. Se prepararon enrolamiento CSR, firma en CA separada del proceso Java, importación/descarga pública, homologación manual explícita, revocación ERP con outbox durable y mantenimiento periódico de CRL. La homologación manual no prueba atestación física.

El canal interno exige TLS cliente y pin/EKU/URI del proxy antes de aceptar IP o certificado reenviado. GeoIP usa una base MMDB local opcional y captura la estimación histórica. SSE ya usa Bearer en cabecera y revalida la sesión abierta. XML/CDR tienen descarga autenticada por ERP y candidato Nginx para cerrar sus alias públicos; Apache/SUNAT permanece exclusivo del backend.

Backend y frontend se comprobaron en laboratorio aislado, con pruebas reales de Tomcat mTLS, Nginx, PostgreSQL, CA/OpenSSL, sudo y restauración de datos sintéticos. El [informe vigente](IMPLEMENTATION_REPORT_2026-10-30.md) registra suites, artefactos y límites. Todas las propiedades nuevas están desactivadas por defecto. La firma, la auditoría y la autorización requieren primero migraciones/configuración y permisos revisados; no se activan por compilar.

Siguen pendientes hardware real Windows/Mac y resistencia a copia, ensayo con esquema/configuración reales, copia real fuera del VPS, piloto y publicación nocturna. Tokens/refresh heredados aún requieren trabajo posterior de cookie/CSRF/rotación y retención/purga no está automatizada. No se garantiza recuperación de la última venta ante pérdida total del VPS ni una hora de recuperación real.
