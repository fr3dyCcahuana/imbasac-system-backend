# Certificados y control de acceso IMBASAC: entrega local del 30 de octubre de 2026

La implementación está preparada y probada localmente en ambos repositorios. **No se ha publicado, aplicado SQL ni cambiado el VPS.** Las propiedades nuevas permanecen desactivadas por defecto. Este informe sustituye las descripciones de pendientes iniciales para los módulos que enumera; no declara terminado todo el plan de continuidad.

La siguiente intervención del propietario está detallada en [acciones del operador](OPERATOR_ACTIONS_2026-10-30.md). El [checklist vigente](REMAINING_WORK_CHECKLIST_2026-10-29.md) distingue preparación local, aceptación real y desarrollo pendiente. No hay que volver a decidir las reglas de negocio ya acordadas.

## Resultado por capa

| Capa | Implementado y comprobado localmente | Aceptación que falta |
| --- | --- | --- |
| Usuario–PC | Una PC por defecto, cupos excepcionales con permiso/motivo, varios clientes por PC, asignación/retiro e historial; concurrencia protegida en PostgreSQL | Usuarios reales del piloto y prueba con sus perfiles de sistema operativo |
| Strikes | Tres por propiedad; sin vencimiento hasta el umbral; bloqueo inicial de un mes editable, desbloqueo anticipado, nuevo ciclo e historial | Piloto y revisión de falsos positivos con evidencia real |
| Certificado | CSR público firmado, perfil RSA fuerte o P-256, SAN UUID, enrolamiento, emisión/importación, descarga pública y revocación | Clave ligada a hardware y uso/copia en Windows/Mac reales |
| CA | Raíz offline y emisora bajo identidad separada; puente restringido de firma, idempotencia, revocación, cola durable y publicación periódica de CRL | Custodia, instalación y permisos reales; prueba del servicio en Ubuntu |
| Nginx–Java | mTLS interno, pin SHA-256, EKU y URI del proxy; sólo entonces se aceptan IP y certificado reenviados | Integración con configuración completa y certificados reales del VPS |
| Auditoría y ubicación | Accesos/sesiones, resumen Lima, CSV, revocación exacta y ubicación histórica mediante MMDB local opcional | Permisos administrativos reales, archivo MMDB/licencia si se desea y política de retención |
| Angular | `#/security/access` y `#/security/control`, permisos por acción y descarte de datos/respuestas al cambiar persona | Ensayo conjunto con backend, navegadores y usuarios reales |
| SSE | Bearer en cabecera, conexión sólo hacia ERP, cancelación por contexto, revalidación periódica y antes de enviar | Conexión a través del Nginx real y comportamiento de redes de clientes |
| SUNAT | XML/CDR por API ERP autenticada; permisos comerciales y referencia del documento; candidato para cerrar alias públicos | Validar rutas/referencias históricas y permisos antes de retirar alias |
| Publicación | Preflight acotado, respaldo cifrado, checksum, restauración PostgreSQL aislada y procedimiento de retorno | Copia real fuera del VPS, ensayo integrado, tiempos medidos y ventana nocturna |

## Comportamiento de acceso

1. La política se resuelve por rol. CLIENTE conserva inicialmente su hash heredado; el administrador cambia expresamente el rol a `CERTIFICATE_REQUIRED` después de aceptar el canal y las PC. Los roles exentos siguen autenticándose conforme a su política. No hay fallback automático a hash cuando el certificado es obligatorio.
2. Nginx solicita opcionalmente certificado cliente durante TLS para permitir la convivencia de roles. Un certificado presentado inválido puede ser rechazado antes de conocer al usuario. La obligación concreta, el usuario y la autorización usuario–PC se deciden en backend.
3. En una política requerida se comprueban proxy confiable, certificado vigente, emisor exacto, perfil/SAN, registro no revocado, equipo activo, homologación administrativa y autorización individual. Importar un certificado no aprueba hardware ni autoriza automáticamente a personas.
4. La PC no autorizada se rechaza desde el primer intento. Los strikes sólo se atribuyen después de verificar credenciales y disponer de evidencia de transporte confiable de equipo no autorizado o ausencia de certificado. Contraseña incorrecta, fallo técnico de certificado propio o ciudad distinta sola no consumen este contador.
5. El umbral inicial es 3 (`security.access-control.strike-threshold`). No hay vencimiento mensual de strikes. La duración inicial del bloqueo es un mes calendario desde su inicio en America/Lima, configurable de 1 a 120 meses en pantalla; una modificación no reescribe bloqueos existentes. La deduplicación/cooldown inicial de 60 segundos evita contar reintentos automáticos equivalentes como incidentes nuevos.
6. El bloqueo afecta al usuario. No bloquea automáticamente a otros clientes de la misma PC. Al vencer o desbloquear se abre otro ciclo conservando historial. Sesiones anteriores no reviven por desbloqueo, reactivación de usuario, reasignación o nueva aprobación del hardware.
7. La sesión durable registra época, autorización concreta y huella del certificado. Las nuevas peticiones, refresh y SSE comprueban vigencia; contraseña, enabled, rol o política pueden invalidar la época. Esto no cancela retroactivamente una venta ya confirmada ni cierra caja.

Las excepciones permiten autorizar PC adicionales; cada una tiene identidad y certificado propios. No autorizan copiar una credencial. El contrato Redis heredado de sesión vigente por usuario continúa: una excepción de cupo no cambia por sí sola ese contrato ni promete sesiones paralelas del mismo cliente. Certificado personal y móviles siguen fuera de la primera etapa.

La política obligatoria se activa por **rol completo**. Esta entrega no tiene selección parcial de usuarios dentro de CLIENTE: el piloto de obligatoriedad se ensaya primero en entorno aislado. Antes de cambiar CLIENTE en producción, deben estar preparados todos los usuarios afectados, sus PC y soporte. Si se requiere un piloto productivo obligatorio para sólo algunas cuentas del mismo rol, hace falta incorporar y probar un alcance administrativo explícito; no cambiar el rol de clientes ni dejar sin certificado al resto como atajo.

## Claves, homologación y revocación

El ERP almacena CSR y certificados **públicos**. Se rechaza PEM con claves privadas, texto añadido o bloques múltiples; se aceptan los formatos públicos `CERTIFICATE REQUEST` y `NEW CERTIFICATE REQUEST`. No se genera un PFX para repartirlo entre PC. La clave de la raíz se custodia offline fuera del VPS, la emisora está separada del proceso público y Java sólo invoca un ejecutable fijo mediante sudo restringido.

Se preparó generación con proveedor TPM en Windows, sin fallback silencioso a software. En Mac hay diagnóstico y candidato de Keychain; **todavía no demuestra clave no copiable ni atestación remota**. El perfil P-256 permite el algoritmo compatible, pero no prueba Secure Enclave. La aprobación administrativa requiere evidencia y nunca sustituye esa comprobación. Si la prueba real falla, corresponde resolver la integración estándar en desarrollo; no pedir al operador que apruebe una garantía inexistente.

La revocación local y su inserción en outbox son una transacción: el ERP deja de aceptar el certificado de inmediato al confirmar esa operación. Un trabajador separado entrega la revocación a la CA con lease, idempotencia y reintentos limitados; un fallo persistente o certificado antiguo sin PEM queda en revisión. No se simula éxito de CA. La publicación de CRL está preparada diariamente a las 22:15 Lima, con validación/recarga y retorno; requiere instalar y habilitar sus servicios durante aceptación. Por ello revocación ERP, índice CA y CRL de Nginx tienen tiempos distintos. La CRL raíz offline exige renovación bajo custodia antes de expirar.

Guías: [PKI](../../ops/pki/README.md), [hardware](../../ops/hardware/README.md), [cola de revocación](CA_REVOCATION_OUTBOX_2026-10-30.md).

## Datos y permisos

Las seis migraciones son manuales y se comparan con el esquema efectivo antes de ensayar/aplicar:

1. [20261029_device_access_foundation.sql](../../src/main/resources/db/scripts/20261029_device_access_foundation.sql).
2. [20261029_access_audit.sql](../../src/main/resources/db/scripts/20261029_access_audit.sql).
3. [20261030_access_control.sql](../../src/main/resources/db/scripts/20261030_access_control.sql).
4. [20261030_device_certificates.sql](../../src/main/resources/db/scripts/20261030_device_certificates.sql).
5. [20261030_ca_revocation_outbox.sql](../../src/main/resources/db/scripts/20261030_ca_revocation_outbox.sql).
6. [20261030_private_sunat_files.sql](../../src/main/resources/db/scripts/20261030_private_sunat_files.sql).

No ejecutar el historial completo ni reaplicar foundation después de cupos: su índice original de una PC fue sustituido. Los SQL no conceden permisos automáticamente a usuarios ni activan propiedades. Revisar catálogo y relaciones reales antes de concederlos a las cuentas seleccionadas:

| Permiso | Uso |
| --- | --- |
| `VIEW_SECURITY_AUDIT` | Consultar dashboard e información de ubicación del evento |
| `EXPORT_SECURITY_AUDIT` | Exportar con permiso de consulta adicional |
| `REVOKE_SECURITY_SESSIONS` | Revocar sesión concreta con consulta adicional |
| `VIEW_SECURITY_CONTROL` | Consultar control de PC, certificados y sanciones |
| `MANAGE_SECURITY_DEVICES` | Registrar PC/CSR, importar/revocar certificados y administrar autorizaciones/cupos |
| `MANAGE_SECURITY_BLOCKS` | Cambiar meses de bloqueo y desbloquear con motivo |
| `MANAGE_SECURITY_HARDWARE` | Aprobar/retirar homologación con evidencia |
| `MANAGE_SECURITY_POLICIES` | Cambiar la política por rol |
| `ISSUE_SECURITY_CERTIFICATES` | Emitir mediante CA, con administración de dispositivos adicional |
| `VIEW_SUNAT_DOCUMENTS` | Descargar XML/CDR, más el permiso comercial y referencia exigidos |

No se han escogido ni elevado cuentas reales. La autorización comercial de XML/CDR respeta los permisos existentes de ventas/contratos; no inventa una propiedad individual que el modelo no tiene. La auditoría no se utiliza como contador de sanciones.

## Configuración y secuencia de activación

Los valores por defecto son `false` para `security.device-policy.enabled`, `security.access-audit.enabled`, `security.access-control.enabled`, `security.trusted-proxy.enabled`, `security.device-certificates.enabled`, `security.ca-signer.enabled`, `security.geoip.enabled` y `security.sunat-files.enabled`. No están habilitados por este informe.

La [plantilla externa](../../ops/nginx/application-security-template.yml) requiere almacenes TLS previamente creados: aunque sus flags estén apagados, activa TLS interno. No se carga en la primera entrega compatible sin preparar esos almacenes. Usar la [secuencia Nginx](../../ops/nginx/README-security.md) y [entrega nocturna](../../ops/deploy/README.md), conservando el dominio, `/api/`, rutas Angular con hash y Apache 8093 privado.

Antes de publicar los botones nuevos XML/CDR, deben estar operativos el endpoint privado, sus rutas reales y permisos. Luego se cierran todos los alias públicos `/sunat-files`. No se ofrece fallback público ni se considera operativa una entrega de esos botones con el backend de archivos desactivado. La API PHP SUNAT no se expone al frontend. Los PDF conservan su flujo anterior y deben comprobarse durante el ensayo.

Para GeoIP se aporta una base MMDB de ciudad con licencia válida y mantenimiento definido; no se abrió cuenta ni se descargó un producto en nombre del propietario. Con el flag apagado no se necesita archivo. Si falta información, se muestra desconocida sin sancionar. Si se activa con archivo inexistente/inválido, se rechaza la configuración al arrancar.

## Verificación ejecutada

| Grupo | Resultado |
| --- | --- |
| Backend: 17 suites seleccionadas de sesión/JWT, política, auditoría, control, certificados web, archivos, outbox, SSE y transporte | 145 correctas, sin fallos ni omisiones |
| Backend: entrada pública PEM y parser real BC | 3 correctas; rechazo antes de JDBC y CSR con ambos encabezados |
| Backend: compilación y empaquetado con Java 21/Maven | Correctos; JAR generado sin arrancar perfiles de producción |
| PostgreSQL 18.6 aislado: cupos/strikes | 15 correctas |
| PostgreSQL aislado: archivos privados | 4 correctas |
| PostgreSQL 18.6 aislado: certificados, CSR, ACL, sesiones, épocas, ubicación y revocación atómica | 27 correctas |
| PostgreSQL 18.6 aislado: trabajador de outbox | 11 correctas |
| Angular: control administrativo | 54 correctas, ChromeHeadless |
| Angular: auditoría / SSE / archivos privados / seguridad de sesión | 15 / 23 / 19 / 30 correctas, ChromeHeadless |
| Angular: compilación final de producción | Correcta; salida en `dist/imbasac-system-frontend/browser` |
| Ops: PKI, puente, CRL, instalación sudo real, respaldo, restore PostgreSQL y Nginx TLS real | 43 casos distintos aprobados entre Windows/Linux y ensayos aislados; los casos específicos de Linux no se atribuyen a Windows |

El transporte incluyó Tomcat real con mTLS y Nginx 1.24 real en pruebas separadas; no sustituye el ensayo navegador–Nginx–Spring Boot completo con configuración del VPS. La firma del servicio Java se sustituyó en las pruebas de aplicación; el helper OpenSSL/sudo se ensayó realmente aparte. Los datos y claves de pruebas son sintéticos/temporales. Se eliminaron los contenedores propios y se conservaron los servicios locales ajenos.

No se ejecutó la suite completa indiscriminada ni se arrancó un perfil conectado a producción. Las pruebas comerciales previas de stock/idempotencia no se presentan como nueva aceptación integral de esta entrega. Documentación ampliada: [control de acceso](ACCESS_CONTROL_IMPLEMENTATION_2026-10-30.md), [certificados/sesiones](CERTIFICATE_GUARD_TESTS_2026-10-30.md), [XML/CDR](PRIVATE_SUNAT_FILES_2026-10-30.md) e [interfaz](../../../imbasac-system-frontend/docs/security-continuity/SECURITY_CONTROL_UI_2026-10-30.md).

## Paquete de revisión local

La carpeta `target/delivery/imbasac-security-20261030-local-01` contiene el JAR compilado, frontend comprimido, herramientas/migraciones/documentación y manifiesto SHA-256. Es un **candidato privado sin publicar**. El manifiesto identifica los commits base y archivos modificados; ambos repositorios tenían cambios ajenos previos que se conservaron. La compilación incorpora el estado completo de cada workspace, no sólo los archivos de seguridad; no implica aceptación de todos esos cambios comerciales.

No contiene claves de clientes ni una CA productiva creada. Tratar el JAR y el paquete como privados: conservan la configuración heredada de la aplicación y la preparación operativa. No colocar el paquete completo en la carpeta pública del frontend. Verificar checksums antes de copiar. La configuración externa, certificados y archivos MMDB reales se preparan fuera de Git.

## Límites y trabajo que sigue abierto

- La exclusividad física Windows/Mac no está probada. Mac puede exigir ingeniería adicional de enrolamiento/atestación estándar según su hardware y navegador; no está resuelta con una casilla de aprobación.
- Falta ensayo con copia privada/esquema/configuración reales, permisos y rutas históricas de comprobantes, recuperación de aplicación/archivos, prueba completa de negocio y tiempo de retorno medido. La ventana máxima de una hora aún no está demostrada.
- El refresh y almacenamiento de tokens mantienen su contrato heredado; cookie HttpOnly/CSRF, rotación/reutilización y bootstrap entre pestañas no se han incorporado. Tampoco se afirma aislamiento perfecto de todos los formularios/módulos ajenos a esta entrega.
- Retención/purga, límites integrales de fuerza bruta/origen/cuenta, observabilidad productiva y matriz completa de autorización de otros archivos/integraciones requieren trabajo adicional. El cooldown de strikes no sustituye rate limiting.
- GeoIP está implementado como detalle histórico; filtros geográficos avanzados y alertas de ciudad necesitan ampliación si se requieren. No hay sanción automática por geografía.
- WhatsApp sigue para evaluación posterior, sin mensajes, compra ni proveedor activado.
- La copia de cierre protege el punto de corte de una publicación; no conserva automáticamente la última venta del día siguiente ante pérdida total del VPS. Persistencia durable independiente, sincronía, recuperación/fencing y reconciliación siguen pendientes. No se afirma RPO cero.

El siguiente paso práctico es la selección y prueba de PC reales y el preflight/copia privados por consola. Con esos resultados se resuelven incompatibilidades y se ensaya el piloto antes de habilitar certificados obligatorios o sanciones en producción.
