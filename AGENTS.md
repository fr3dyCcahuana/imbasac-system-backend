# Instrucciones del backend IMBASAC

## Alcance y contexto

Java 21, Spring Boot 3.4.5, Maven, Spring Security, PostgreSQL/JDBC y Redis en la base revisada. Frontend hermano: ../imbasac-system-frontend, Angular. Responder en español salvo indicación del usuario.

Respetar domain/application/infrastructure y los puertos/adaptadores existentes. No introducir JPA ni reorganizar módulos por una corrección puntual. Leer código, esquema y estado de Git actuales; conservar cambios ajenos.

La autorización de implementar localmente no implica publicar el JAR, ejecutar SQL en producción, acceder al VPS o contratar servicios. Si la tarea sí autoriza esas operaciones, continuar dentro del alcance y las condiciones verificadas sin pedir de nuevo la misma autorización.

## Contratos y seguridad

- Permisos, enabled, política y propiedad de datos se verifican en backend; el frontend no concede acceso.
- No confundir usuario, dispositivo, certificado, sede y Station: Station representa caja.
- Objetivo acordado: certificado configurable por rol, inicialmente CLIENTE externo. Una PC admite varios clientes autorizados; cada CLIENTE tiene una PC activa por defecto, con excepciones de cupo aprobadas expresamente por un administrador, con motivo e historial. Cada PC adicional utiliza su propio certificado.
- Strikes: inicialmente tres dentro del mismo día de Lima (00:00–23:59), umbral configurable por propiedad. Sin bloqueo vigente, el conteo se reinicia al cambiar el día sin borrar historial ni cerrar sesiones válidas. El tercer strike genera un bloqueo inicial de 24 horas; duración configurable en pantalla en días exactos de 24 horas, con desbloqueo administrativo. Un cambio de día no cancela un bloqueo vigente. Una sanción individual no bloquea la PC compartida. Ciudad estimada sola o contraseña incorrecta no consumen este contador de equipo no autorizado. Los certificados nuevos/renovados tienen vigencia de un año (365 días); un certificado emitido conserva su vencimiento firmado.
- Conservar preparado el tipo de certificado de usuario y dispositivo, sin habilitarlo por defecto. Móviles fuera del enrolamiento inicial.
- Distinguir objetivo de estado implementado; no dar por existentes mTLS, tablas o cookies solo porque figuren en el plan.
- Un JWT válido no basta si usuario/sesión/política/equipo fueron deshabilitados. Verificar sesión exacta y contratos de refresh al tocar autenticación.
- La obligación individual de certificado aplica a cualquier cuenta y rol, incluidos administradores. Refuerza la política del rol sin cambiarla ni volver automáticamente al hash. Quien no tenga obligación individual hereda su política de rol. La asociación usuario–PC no activa automáticamente esta obligación.
- Mantener compatible el principal/username que consumen los controladores si se cambia el sujeto JWT.
- No registrar contraseñas, JWT, refresh, cookies, claves de CA o secretos de servicios. La raíz CA no pertenece al JAR ni a PostgreSQL.

## API SUNAT privada

La API PHP de SUNAT en Apache 127.0.0.1:8093 es de uso exclusivo del backend. No exponer ese puerto ni sus operaciones en Angular, rutas públicas de Nginx o proxies genéricos. El navegador utiliza endpoints del ERP autorizados; descargas de comprobantes/XML/CDR requieren su control de permisos. Preservar esta frontera al implementar certificados y cambios de red.

## Datos comerciales y migraciones

Recuperar hasta la última venta confirmada es el requisito. Idempotencia y transacciones son necesarias, pero un único VPS y respaldos asíncronos no demuestran RPO cero ante su pérdida completa.

No repetir venta, pago o comprobante por una respuesta perdida. Mantener la misma clave de intención y conciliar el resultado. Logout/revocación no cierran caja ni borran movimientos.

Ya existen avances de idempotencia y reasignación de reservas: revisar CreateIdempotentSaleV2Service, repositorios, scripts y tests actuales antes de crear un mecanismo paralelo. Preservar restricciones, lock ordering, historial idempotente y correlativos.

src/main/resources/db/scripts contiene migraciones manuales. Archivo presente no significa script aplicado. Verificar esquema/destino y preparar cambios compatibles; no ejecutar todo el historial ni restaurar una base antigua para deshacer un JAR.

## Verificación

- Usar Maven con Java compatible y seleccionar pruebas que cubran el cambio. Preferir el wrapper si está completo; en la revisión del 23 de octubre existían sus scripts pero faltaba .mvn/wrapper/maven-wrapper.properties. Si sigue faltando, usar un Maven instalado compatible y declarar la limitación sin reparar el wrapper fuera del alcance.
- Revisar perfiles y @SpringBootTest antes de la suite completa; no conectar pruebas a producción.
- Integraciones de idempotencia/reservas pueden requerir variables SALE_IDEMPOTENCY_TEST_JDBC_URL o STOCK_RESERVATION_TEST_JDBC_URL. Leer cada test y usar base aislada, sin imprimir credenciales.
- Pruebas documentales no requieren iniciar servicios. No ejecutar ensayos destructivos de recuperación en producción.
- Informar qué se ejecutó, migraciones pendientes y garantías no demostradas. Una compilación no acredita aplicación SQL ni durabilidad remota.

## Skills de trabajo

Usar la pertinente, disponible en la carpeta personal de skills de Codex:

- imbasac-security: autenticación, certificados, sesiones, ACL y revocación.
- imbasac-deploy: JAR, Angular, Nginx, Ubuntu y retorno compatible.
- imbasac-postgres-continuity: transacciones, idempotencia, stock, integraciones y recuperación.

Cada skill tiene referencia de ambas aplicaciones. No activar un rediseño de PKI por una tarea comercial sin impacto en acceso. En otra máquina sin las skills, mantener estas reglas y no afirmar que están instaladas.

Plan detallado local: C:/Users/Paul/.codex/visualizations/2026/10/22/01a16203-97fe-72f1-a832-636dcd932541/plan-imbasac/PLAN_DE_TRABAJO_IMBASAC.md. Es una referencia de diseño, no un inventario vigente ni una autorización de ejecución.
