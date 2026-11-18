# Entrega durable de revocaciones a la CA

Código local preparado. El ERP registra la revocación del certificado y la solicitud a la CA en la misma transacción de PostgreSQL. El rechazo por el registro ERP es inmediato después de confirmar esa transacción; la llamada externa a la CA ocurre después, sin hacer esperar al login, una venta ni esa transacción.

Aplicar manualmente `20261030_ca_revocation_outbox.sql` después de `20261030_device_certificates.sql`. No cambia certificados existentes ni habilita procesos por sí sola. El trabajador exige `security.ca-signer.enabled=true` y un adaptador de firma configurado; su valor inicial es false.

## Estados y reintentos

- PENDING: entrega pendiente; todavía no se afirma que la CA confirmó la revocación.
- COMPLETED: el adaptador confirmó la operación de revocación. No equivale a que ya se publicó o recargó una CRL; esa publicación tiene su propio temporizador/procedimiento.
- NEEDS_REVIEW: se agotaron seis intentos, no se puede acreditar un resultado después de perder un lease, o falta el certificado público necesario. Requiere conciliación del operador. No se vuelve a emitir un certificado para resolver esta situación.

La clave de trabajo es el identificador del certificado, con inserción idempotente. Las filas contienen el certificado público o null únicamente en NEEDS_REVIEW; no contienen clave privada, PFX, contraseña, token, rutas de servicios ni salida del firmante.

El planificador despierta cada minuto y despacha como máximo un trabajo a un hilo exclusivo, sin acumular cola ni bloquear el planificador que revalida SSE y ejecuta otros trabajos. Una transacción corta toma un lease de cinco minutos mediante FOR UPDATE SKIP LOCKED y un token de fencing. La llamada externa se realiza fuera de esa transacción. La confirmación o actualización exige el mismo token: un trabajador antiguo no sobrescribe el resultado de otro que recuperó su lease vencido.

Las fallas se reintentan con espera exponencial, inicialmente un minuto y con techo de una hora. Se detienen al sexto intento fallido. Un proceso interrumpido puede recuperarse tras su lease mediante la operación de revocación idempotente; si ya agotó el límite pasa a revisión. Los códigos de error son genéricos y nunca incluyen mensajes de SQL, salida de procesos, secretos o rutas.

`pendingCount()` cuenta trabajos no resueltos, incluidos los que requieren revisión; `needsReviewCount()` distingue estos últimos para las comprobaciones operativas. No existe un endpoint público para manipular leases, marcar éxito o reabrir estos trabajos.

Si falta el PEM de un registro anterior, se mantiene la revocación ERP y se registra NEEDS_REVIEW/CERTIFICATE_PEM_UNAVAILABLE. Si la inserción durable falla, se revierte la transacción completa y la operación administrativa informa indisponibilidad; no se muestra un éxito inexistente.

## Comprobaciones ejecutadas

El 30 de octubre de 2026 pasaron **41 pruebas** de la selección final: 4 unitarias del trabajador, 11 con PostgreSQL 18.6 para la cola y 26 de certificados/sesiones, sin fallos, errores ni casos omitidos. Las pruebas verifican:

- Modo apagado y firmante sin configurar no consultan ni procesan la cola.
- El método de revocación predeterminado de un adaptador antiguo falla expresamente, en lugar de confirmar falsamente.
- Inserción y migración repetibles, éxito confirmado, reintentos acotados y estado de revisión.
- Recuperación de lease vencido, agotamiento de lease incierto y fencing de un trabajador que falla tarde después de otro éxito.
- Dos trabajadores no consumen el mismo lease vigente; el planificador no espera al firmante ni acumula trabajo repetido.
- Inserción unida a rollback del llamador y tolerancia a indisponibilidad de PostgreSQL sin lanzar errores a otro flujo.
- Revocación real del servicio ERP junto con inserción de cola: si la inserción falla, no queda una revocación parcial. Un certificado sin PEM se revoca localmente y queda en revisión.

El adaptador externo se sustituye por dobles de prueba; estos resultados no confirman una revocación en una CA de producción ni su CRL. Un ensayo inicial encontró una conversión de parámetros de CASE, corregida con tipos PostgreSQL explícitos. Una ejecución posterior encontró agotamiento de memoria nativa de Windows; la selección final completa pasó con límites de memoria del proceso de pruebas, sin cambiar procesos del usuario. El contenedor aislado propio se retiró al finalizar.
