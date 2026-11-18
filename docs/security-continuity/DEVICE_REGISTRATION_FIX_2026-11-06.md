# Registrar una PC antes de configurar la CA

Corrección local del 6 de noviembre de 2026. El registro administrativo de una computadora estaba condicionado por `security.device-certificates.enabled`; con esa propiedad apagada, la interfaz impedía completar el primer paso aunque el control de acceso estuviera habilitado.

## Resultado

El registro utiliza ahora `security.access-control.enabled`, independientemente de la CA. La respuesta del estado de certificados añade `registrationEnabled`; `enabled` conserva su significado de operaciones de certificados. Se mantiene el endpoint existente `POST /security/device-certificates/devices` y los permisos `VIEW_SECURITY_CONTROL` + `MANAGE_SECURITY_DEVICES`.

Con control habilitado y certificados apagados:

1. **Preparar computadora** abre el formulario de nombre, Windows/macOS y motivo.
2. Al confirmar, el servidor guarda la PC en `PENDING` y el cambio administrativo en su historial.
3. La ficha muestra su identificador y el siguiente paso. El contador del inventario se actualiza después de confirmar el registro.
4. El frontend no consulta certificados, solicitudes ni políticas mientras ese servicio está deshabilitado. Tampoco los muestra como si se hubieran consultado y estuvieran vacíos.
5. La emisión, importación, registro de CSR, aprobación de hardware y política de certificado conservan sus requisitos y comprobaciones. Registrar la PC no aprueba hardware, crea certificados, asigna clientes ni habilita el acceso.

La autorización de consulta o administración sigue comprobándose en el backend. La bandera de certificados por sí sola no habilita el registro si el control está apagado. Se conserva compatibilidad con respuestas anteriores que todavía no incluyen `registrationEnabled`; no se asume registro disponible con un backend anterior que declare certificados desactivados.

## Uso local

Se necesita el backend actualizado y el frontend actualizado, las migraciones de seguridad ya aplicadas y `security.access-control.enabled=true` en el perfil que realmente se está ejecutando. La propiedad de certificados puede permanecer en `false` y el archivo de emisor puede estar sin configurar para este primer paso. No es necesario activar todos los flags ni ejecutar SQL nuevo por esta corrección.

Después de recompilar/reiniciar el backend y recargar el frontend, abrir **Equipos y bloqueos → Preparar computadora**. Debe aparecer el formulario aunque la emisión siga pendiente. Conservar el identificador para preparar el CSR en la computadora del cliente; la emisión real se configura después.

El código utiliza las tablas de inventario e historial de las migraciones de seguridad existentes; esta corrección no instala la CA ni aplica esas migraciones automáticamente.

## Verificación

- Backend: `DeviceCertificateWebTest`, `CertificatePublicInputTest`, `CertificatePolicyIntegrationTest` y `DeviceCertificatePostgresIntegrationTest`: 53 pruebas correctas, ninguna omitida. Incluyen el registro con emisor inexistente y certificados apagados, estado `PENDING`, ausencia de autorización/certificado, rechazo de móviles y protección por permiso.
- Frontend: `test:security-control`, 67 pruebas correctas. Incluyen el botón habilitado antes de configurar CA, formulario solicitado desde el botón principal, selección del registro creado, bloqueo del resto de operaciones y descarte de una respuesta tardía al cambiar de usuario.
- No se modificaron ventas, otros módulos comerciales, configuración productiva ni bases reales. La integración PostgreSQL utiliza un contenedor propio aislado y lo elimina al terminar.

Compilación de producción de Angular y empaquetado del JAR correctos. Se comprobó además que el JAR contiene las clases actualizadas de registro y estado. El ensayo de navegador reprodujo el caso concreto: inventario vacío, administración habilitada, certificados/CA apagados, apertura del formulario, guardado de la primera PC, contador actualizado a uno e identificador visible. No se consultaron los endpoints de certificados ni se emitió o aprobó una credencial. El escenario de navegador utiliza respuestas sintéticas y no constituye una emisión real ni una prueba de hardware.

Captura del caso: `C:/Users/Paul/.codex/visualizations/2026/10/22/01a16203-97fe-72f1-a832-636dcd932541/security-ui-20261106/11-registro-sin-ca.png`. El informe de navegador conserva también las comprobaciones anteriores de equipo, auditoría, permisos y temas.
