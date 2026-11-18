# Certificado obligatorio por cuenta, para todos los roles

Actualización del 12 de noviembre: el código incorpora además conteo diario y duración de bloqueo en días. Para publicar la versión actual es necesaria la migración `20261112_daily_access_strikes.sql`; consultar `DAILY_STRIKES_AND_CERTIFICATE_YEAR_2026-11-12.md`. La ampliación de roles descrita aquí, por sí sola, no necesitaba nuevas tablas.

El requisito individual puede activarse para CLIENTE, ADMINISTRATOR, CASHIER, WHOLESALER y WAREHOUSE. No se activa automáticamente por publicar el JAR, registrar una PC o vincularla a un usuario.

## Decisión de acceso

Con `security.device-policy.enabled`, `security.device-policy.individual-enabled` y `security.access-control.enabled` habilitados, la cuenta con `security_user_access_control.certificate_required=true` utiliza `CERTIFICATE_REQUIRED`, cualquiera sea su rol. No hay retorno al hash ni a contraseña sola por ausencia del certificado. Además de credenciales correctas, necesita certificado TLS verificado, vigente y registrado, hardware aprobado y autorización individual para esa PC.

Una cuenta sin esa marca hereda `security_role_device_policy.mode`. Quitar la marca individual no exime a una cuenta si su rol exige certificado. El hash de compatibilidad sigue limitado a CLIENTE; no se incorpora a otros roles.

La activación administrativa exige permiso `VIEW_SECURITY_CONTROL` y `MANAGE_SECURITY_POLICIES`, cuenta habilitada y al menos una PC autorizada, aprobada y con certificado vigente. La operación conserva actor y motivo, y rota la época de sesiones únicamente para la cuenta modificada. No cambia la política del rol ni las cuentas que comparten la PC.

## Operación desde el ERP

1. Preparar la PC, instalar y comprobar su certificado, y autorizar a la cuenta para esa PC.
2. Ir a Seguridad → Equipos y bloqueos → Usuarios y permisos.
3. Buscar la cuenta concreta, por ejemplo `admin`, y abrir su ficha.
4. Pulsar Cambiar validación individual → Exigir certificado a esta cuenta.
5. Registrar el motivo, revisar la cuenta mostrada y confirmar. Sus sesiones anteriores dejan de ser válidas.
6. Iniciar sesión desde la PC autorizada. Comprobar también el rechazo desde una PC sin certificado utilizando credenciales de prueba correctas.

No usar Cambiar política del rol para incorporar una sola cuenta: esa operación sigue teniendo alcance sobre todo el rol.

## Strikes

Una contraseña incorrecta o un estado de cuenta rechazado no generan strike de equipo. Con credenciales válidas y certificado obligatorio, un intento desde una PC no autorizada o sin certificado se rechaza y genera el strike individual correspondiente. Se conserva la deduplicación existente por intento e incidente; las repeticiones dentro del intervalo configurado no multiplican la sanción. Un certificado propio vencido/revocado y una incidencia técnica no se tratan como prueba de otra PC. Una ciudad distinta por sí sola tampoco genera strike.

## Publicación

Requiere publicar backend y frontend actualizados. No requiere nuevas tablas, migraciones SQL, cambios de Nginx, otra CA ni reinstalar certificados existentes. La columna individual y las propiedades ya existen en la entrega del 11 de noviembre. Publicar la actualización no modifica ninguna marca individual ni política productiva.

## Validación

La regresión con PostgreSQL aislado recorre los cinco roles: activación selectiva, credenciales incorrectas sin strike, credenciales correctas sin certificado con strike, acceso con certificado y PC autorizados, rechazo de certificado ajeno, conservación de la otra cuenta del mismo rol y de la PC compartida, invalidación de sesiones de la cuenta modificada y herencia de una política de rol que exige certificado. Las pruebas de autorización HTTP conservan los permisos de lectura y modificación separados.
