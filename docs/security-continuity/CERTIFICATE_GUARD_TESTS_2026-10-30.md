# Certificados y sesiones: integración local comprobada

El 30 de octubre de 2026 se ejecutó `DeviceCertificatePostgresIntegrationTest` con Java 21/Maven y PostgreSQL 18.6 aislado: **27 pruebas, cero errores, cero fallos y ninguna omitida**. Se ejecutaron además **3 pruebas de entrada pública PEM**, con parser real BC y rechazo de contenido añadido antes de JDBC. Complementa las [33 pruebas de cupos y strikes](ACCESS_CONTROL_IMPLEMENTATION_2026-10-30.md), las [28 pruebas de descarga privada](PRIVATE_SUNAT_FILES_2026-10-30.md) y las [15 pruebas de entrega de revocaciones a la CA](CA_REVOCATION_OUTBOX_2026-10-30.md).

Se crean esquemas aleatorios por caso, con las tablas mínimas de usuarios, roles y permisos. Se aplican en orden las migraciones `20261029_device_access_foundation.sql`, `20261029_access_audit.sql`, `20261030_access_control.sql`, `20261030_device_certificates.sql` y `20261030_ca_revocation_outbox.sql`. Las conexiones de cada caso usan un pool acotado y se cierran antes de eliminar su esquema. No se cargan perfiles Spring Boot ni conexiones de producción.

Los certificados de prueba se construyen con las bibliotecas estándar/BC ya utilizadas por la aplicación. Las claves privadas existen solo en memoria; únicamente la CA pública de prueba se escribe en un directorio temporal de JUnit. Los certificados y las claves no pertenecen a ningún cliente real.

## Casos cubiertos

1. Migración repetible y permisos definidos sin concesiones automáticas; CLIENTE continúa en política de hash inicial.
2. Módulo de certificados apagado con una ruta de CA inexistente: no intenta leerla ni habilita registro de equipos.
3. CSR mal formado, RSA débil e incorrectamente firmado: sin solicitud de enrolamiento creada.
4. Importación exige enrolamiento de la misma clave pública y el UUID de la PC firmado en SAN.
5. CA ajena, SAN ausente, EKU incorrecto y clave RSA fuera del perfil: rechazados.
6. CSR P-256 con ECDSA SHA-256 y certificado firmado por CA RSA: enrolamiento, importación, aprobación, asignación y sesión correctos.
7. Curva P-384 fuera del perfil inicial: rechazada tanto en CSR como en certificado.
8. Importar un certificado no aprueba hardware ni autoriza por sí solo a un usuario.
9. Certificado propio/ajeno determinado por la asignación individual, sin extenderla a otros usuarios de la PC.
10. Transporte no autenticado o sin certificado: no produce una PC presentada válida.
11. Revocación de certificado: impide presentación, sesión existente y refresh; repetición administrativa conserva revocación.
12. Sesión exacta, usuario, dispositivo y huella deben coincidir; otro certificado de la misma PC no hereda esa sesión.
13. Retirada concurrente de asignación antes de registrar sesión: no crea una sesión autorizada.
14. Retirar y volver a asignar la misma PC no rehabilita las credenciales antiguas.
15. Retirar y volver a aprobar hardware no rehabilita sesiones antiguas.
16. Desbloqueo administrativo y vencimiento del bloqueo cambian el ciclo/época; las sesiones previas continúan inválidas.
17. Cambios de contraseña, enabled y rol invalidan las sesiones previas, aunque se vuelva a habilitar la cuenta o restaurar el rol.
18. La captura privada previa a autenticar, si queda desactualizada por cambio de contraseña/rol, no permite registrar sesión; el DTO User de prueba no contiene contraseña.
19. Una sesión creada exenta no adquiere un vínculo de PC posterior y un cambio de política invalida su época.
20. Registro de sesiones o estado de certificados indisponible: error de indisponibilidad, sin autorización por defecto.
21. GeoIP queda como snapshot del evento; una actualización posterior de la misma correlación no reescribe su ciudad/versión y no crea strikes.
22. Emisión administrativa mediante un firmante sustituido en pruebas: importa y permite descargar únicamente el certificado público; el reintento conserva la misma identidad sin aprobar hardware ni asignar usuarios automáticamente.
23. Un firmante que devuelve certificado de otra PC o está indisponible no consigue registrar una identidad autorizada.
24. El adaptador de firma apagado no inicia procesos externos ni toma rutas privadas del cliente.
25. Un fallo en la inserción de la cola revierte la revocación local completa y no comunica un éxito parcial.
26. Un certificado anterior sin PEM queda revocado en el ERP y en revisión administrativa para la CA. Repetir una revocación normal mantiene una sola solicitud durable.
27. Un CSR público `NEW CERTIFICATE REQUEST` se procesa con firma real y permite enrolar/importar su certificado, igual que `CERTIFICATE REQUEST`.

`CertificatePublicInputTest` rechaza certificados con clave privada/texto añadido y CSR de ambos formatos con contenido externo antes de consultar JDBC; también verifica con BC la firma y fin de entrada del formato NEW. Las validaciones ancladas no aceptan bloques PEM concatenados.

## Límites de estas pruebas

La evidencia de transporte TLS se simula mediante el adaptador `TrustedClientResolver`; estas pruebas de persistencia no acreditan un handshake real entre navegador, Nginx y Ubuntu. Las comprobaciones del adaptador y la configuración Nginx se ejecutan por separado. Los casos de emisión sustituyen el firmante: no invocan la CA real ni acreditan la instalación de su servicio en Ubuntu.

La aprobación de hardware de la fixture es un acto administrativo simulado. No demuestra TPM, Secure Enclave, resistencia de copia, compatibilidad del navegador ni instalación en Windows/Mac. Admitir el perfil P-256 evita excluir por diseño las claves de ese algoritmo; su uso real en Mac sigue necesitando homologación. La API documentada por Apple para Secure Enclave utiliza P-256. [Documentación de Apple](https://developer.apple.com/documentation/security/protecting-keys-with-the-secure-enclave).

Estos resultados no activan flags, aplican migraciones en el VPS, firman certificados reales ni demuestran continuidad de la última venta ante pérdida del servidor. Tampoco una prueba de época de sesión sustituye la conciliación del diario de revocaciones vigente después de restaurar una copia antigua.
