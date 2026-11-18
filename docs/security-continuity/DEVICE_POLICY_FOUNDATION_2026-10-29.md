# Política por rol y registro de dispositivos: entrega local del 29 de octubre de 2026

Esta entrega prepara la convivencia entre el acceso actual y los certificados. No se ha publicado, no se ha aplicado SQL en el VPS y todavía no acepta certificados reales. Los cambios de mapas que estaban en ambos repositorios se conservaron.

## Comportamiento del backend

La propiedad `security.device-policy.enabled` tiene valor predeterminado `false`. No se modificaron los archivos application YAML existentes. Con la propiedad desactivada, login, renovación y filtro de peticiones no consultan las tablas nuevas ni cambian las condiciones anteriores de autenticación.

El endpoint autenticado `GET /security/device-policy/me` informa exclusivamente sobre la identidad de la sesión, con `Cache-Control: no-store`. Su respuesta tiene `mode`, `enforcementEnabled` y `certificateChannelReady`. El último campo es siempre `false` en esta entrega.

| Modo | Comportamiento actual |
| --- | --- |
| `PASSWORD_ONLY` | Conserva las credenciales, sesión, permisos y restricciones de negocio existentes; no exige hash ni certificado adicional. |
| `LEGACY_BROWSER_HASH` | Conserva la comprobación anterior de CLIENTE en Angular. No incorpora una comprobación de hardware ni un nuevo control del hash en el backend. Solo es una política válida para CLIENTE. |
| `CERTIFICATE_REQUIRED` | Rechaza con 403 y código `DEVICE_CERTIFICATE_CHANNEL_NOT_READY`. No existe todavía un canal que permita aceptar certificados. No vuelve automáticamente al hash. |

Con la propiedad activada en un laboratorio y la migración aplicada, el backend consulta una política explícita por ID y nombre actual del rol. La ausencia de política rechaza el acceso; los errores de lectura se comunican como 503. No se interpreta un error como una exención.

Se comprueba la política antes de crear una sesión, antes de extender una renovación y después de autenticar el JWT en cada nueva petición autenticada. La denegación impide alcanzar el controlador de negocio. El login evalúa la identidad que está ingresando; el logout permanece disponible para cerrar sesión aunque su rol ya no tenga acceso. Una conexión SSE ya abierta todavía requiere un mecanismo de cierre y revalidación separado.

Los encabezados enviados por el navegador, un PEM o un hash de localStorage no se aceptan como prueba TLS. El adaptador confiable Nginx–backend aún está pendiente.

## Preparación de PostgreSQL

Archivo manual: [20261029_device_access_foundation.sql](../../src/main/resources/db/scripts/20261029_device_access_foundation.sql).

| Tabla | Preparación incluida |
| --- | --- |
| `security_role_device_policy` | Política explícita por rol. La semilla conserva CLIENTE con hash y los otros cuatro roles conocidos sin requisito adicional. Reejecutar no sobrescribe una política existente. |
| `security_device` | Identificador estable de PC, nombre, plataforma Windows/Mac y estado. Un estado ACTIVE es metadato, no prueba de hardware. |
| `security_user_device_binding` | Historial de asignaciones. Un índice único parcial impide dos asignaciones activas para un usuario, incluso con solicitudes concurrentes. Varios usuarios sí pueden compartir el mismo dispositivo. |
| `security_device_certificate` | Metadatos públicos: dispositivo, tipo, titular cuando corresponda, emisor, serie, huella y vigencia/revocación. No almacena claves privadas, PFX ni claves de CA. |

El tipo `DEVICE` prepara un certificado de PC compartida con login individual. `USER_DEVICE` queda reservado para un certificado personal futuro; no está habilitado para autenticación. Las plataformas móviles no forman parte de este registro inicial.

Estas tablas aún no se utilizan para autorizar una PC en ejecución. Faltan los servicios y endpoints de enrolamiento, aprobación, asignación, traslado, renovación y revocación, con sus transacciones y auditoría. El índice demuestra la regla sobre el registro, no que hoy se pueda identificar una computadora física.

El script agrega tablas e índices y establece un límite de espera de bloqueo de tres segundos. Debe revisarse contra el esquema real y ensayarse antes de cualquier ejecución en producción. `CREATE IF NOT EXISTS` no verifica que una tabla previa tenga la estructura esperada. No es un mecanismo automático de migración ni una instrucción para ejecutar todos los scripts históricos.

## Comportamiento del frontend

Después del login y de recuperar `/users/me`, Angular consulta la política del usuario. Con el módulo desactivado conserva el hash actual de CLIENTE y el comportamiento de los otros roles conocidos. No borra las claves de terminal existentes.

Solo un 404 del endpoint permite la compatibilidad con un backend anterior que no lo implemente. Los errores 401, 403, 500, 503, respuestas inválidas o roles desconocidos no permiten omitir la validación. Un backend antiguo podría devolver otro código para una ruta desconocida; por eso el orden previsto sigue siendo backend primero y frontend después.

Los rechazos tienen mensajes de acceso específicos. Una respuesta tardía del login o de la política de una persona anterior no restablece su identidad después del logout ni reemplaza la identidad del siguiente usuario. Esto no resuelve por sí solo el aislamiento de todos los formularios, pestañas y respuestas de cada módulo.

El logout conserva el journal de ventas, las claves idempotentes y el hash de terminal. No cierra caja ni modifica operaciones confirmadas. No se añadieron rutas de navegación o administración en esta entrega.

## Evidencia ejecutada

| Comprobación | Resultado |
| --- | --- |
| Compilación del backend con Java 21 y Maven instalado | Correcta |
| `DeviceAccessPolicyServiceTest` | 9 correctas: desactivado, requerido/exento, ausencia de política, usuario inválido y fallo de base |
| `DevicePolicyWebTest` | 6 correctas: denegación, encabezados falsos, 503, identidad propia, no caché y registro único del filtro |
| `DevicePolicySecurityChainTest` | 4 correctas con la cadena real de SecurityConfig: orden JWT/política, compatibilidad, anónimo y logout disponible |
| `SessionSecurityTest`, `SecurityAuthFilterTest`, `JsonWebTokenUtilsTest` | 17 correctas, incluyendo login denegado sin nueva sesión y renovación denegada sin extensión |
| `PostgresDeviceAccessFoundationIntegrationTest`, PostgreSQL 18.6 desechable | 7 correctas, sin omisiones: semilla, reejecución, PC compartida, reasignación y concurrencia, restricciones de certificados y móviles |
| Frontend `npm run test:security` | 28 correctas en ChromeHeadless |
| Frontend `npm run test:stock` | 69 pruebas existentes correctas en ChromeHeadless |
| Frontend `npm run build` | Compilación de producción correcta |

Las 36 pruebas de backend unitarias/web y las siete de PostgreSQL se ejecutaron sin arrancar la aplicación completa, sin perfiles reales y sin conectarse al VPS. PostgreSQL 18.6 de laboratorio no acredita la versión menor 18.1 ni el esquema de producción. Las pruebas de frontend no demuestran RPO cero.

## Secuencia que queda para avanzar

1. Ensayar generación y uso de clave protegida en Windows y Mac representativos, con los navegadores objetivo. Definir qué equipos pueden cumplir el requisito de una PC física y el procedimiento cuando no puedan. Un PFX exportable y el hash actual no satisfacen esa garantía.
2. Cerrar la selección de CA y preparar su emisora como servicio aislado en el VPS, con raíz fuera de línea. El backend orquesta la emisión; no se convierte por ello en un almacén de la clave raíz. Completar respaldo y recuperación de la PKI.
3. Implementar enrolamiento, aprobación, vínculo usuario–PC, traslados y revocación durable, incluyendo los permisos administrativos y auditoría. No activar `USER_DEVICE` ni móviles por tener columnas preparadas.
4. Preparar y probar mTLS en Nginx y el salto autenticado hacia el backend, con rechazo de encabezados inyectados, entradas alternativas y certificados inválidos/revocados. La política por rol debe permitir a los roles exentos seguir entrando; no imponer mTLS global antes de resolver esa convivencia.
5. Añadir la verificación real de posesión, cadena, vigencia, revocación, dispositivo y asignación del usuario. Solo esa integración podrá cambiar el resultado de `CERTIFICATE_REQUIRED` a un acceso válido.
6. Completar las pantallas de administración y soporte, aceptación con PC compartida y piloto de CLIENTE. Ensayar el JAR nuevo con el módulo desactivado, luego el frontend; una vuelta a código anterior debe conservar un endpoint compatible y las correcciones de sesión. Después de activar certificados no usar un retorno al hash como solución automática de incidentes.

La preparación de continuidad avanza por su propio frente: aún falta un destino durable independiente, respaldos/restauración probados y una estrategia verificada para recuperar la última venta confirmada. No se ha prometido ese resultado con un único VPS.

La configuración Nginx, el firewall, los servicios, PostgreSQL y los datos de producción no se modificaron. SUNAT permanece exclusivamente del lado del backend; no se añadió acceso desde Angular al puerto 8093 ni un proxy público hacia esa API. Cualquier publicación requiere su preparación y comprobaciones, fuera de 07:00–21:00 America/Lima, con la ventana máxima acordada de una hora.
