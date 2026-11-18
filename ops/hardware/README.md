# Homologación de la PC física

Los candidatos preparados no acreditan por sí solos atestación. Generar un CSR, importarlo y navegar con mTLS comprueba un flujo, pero todavía no demuestra que una clave no se pueda copiar. No activar certificado obligatorio con la promesa de exclusividad física hasta completar evidencia de la plataforma.

## Windows

`New-ImbasacDeviceRequest.ps1` comprueba TPM presente/listo y prepara INF con Microsoft Platform Crypto Provider, RSA3072, SHA256 y exportación deshabilitada. Solo crea la clave con `-GenerateRequest`. Abrir Windows PowerShell como administrador en la PC, con la misma cuenta de Windows que usará el navegador. Si Windows solicita credenciales de otra cuenta, coordinar con el administrador de IMBASAC antes de generar la clave.

```powershell
.\New-ImbasacDeviceRequest.ps1 -DeviceId '11111111-1111-4111-8111-111111111111' -OutputDirectory 'C:\Users\Paul\ImbasacEnrollment\pc-piloto'
```

El UUID lo entrega el registro ERP; la carpeta debe ser nueva, fuera de repositorios y sincronización. Una segunda ejecución con `-GenerateRequest` necesita una carpeta nueva, o ejecutar `certreq -new` sobre el INF preparado después de revisarlo. El script requiere permisos para consultar TPM. RSA3072 no está soportado en todos los TPM; si falla, conservar diagnóstico sin rebajar automáticamente a software/RSA2048 ni marcar equipo aprobado.

Una vez firmado, `certreq -accept <certificado-público>` lo vincula a la clave del perfil. Antes, comprobar emisor/huella por canal independiente. No importar claves privadas proporcionadas por terceros. Verificar asociación y proveedor sin exportar claves ni publicar su inventario completo.

El candidato usa **CurrentUser**: varias personas del ERP pueden compartir el perfil Windows. Si usan cuentas Windows distintas, el acceso a la misma clave requiere otro diseño de almacén/ACL y pruebas; no declarar resuelto el acceso por PC sólo con este script. No instalar un certificado distinto por cada perfil y llamarlo automáticamente el mismo equipo.

## macOS

`mac-keychain-candidate.sh` sólo lee versión y arquitectura y muestra el flujo nativo de Keychain Assistant. El perfil de emisión admite RSA3072/4096 o EC P-256 nombrada (`prime256v1`/`secp256r1`) con ECDSA SHA256, según el proveedor estándar que se homologue. Guardar clave en ese Mac e importar sólo el certificado público firmado en el mismo llavero/perfil. El script no solicita contraseña del llavero ni extrae identidades. Permitir P-256 evita limitar el perfil criptográfico a RSA; **no** convierte un CSR de software/Keychain en evidencia de Secure Enclave.

Este flujo **no garantiza Secure Enclave ni no exportabilidad**. La vía estándar Apple para atestación de hardware requiere ACME/gestión de dispositivos, validación de Apple y hardware compatible. Esa integración no se ha instalado ni contratado. Mac Intel/Apple Silicon, perfiles compartidos y navegadores requieren prueba individual. Si no puede demostrarse el requisito físico, conservar Mac pendiente o resolver la integración estándar antes de activar; no sustituirla por un PFX o un hash de navegador.

## Evidencia mínima de piloto

| Comprobación | Resultado necesario |
|---|---|
| Modelo, versión OS, perfil y navegador | Identificados sin enviar inventario sensible al chat |
| Clave local y proveedor real | Evidencia independiente de la declaración del CSR |
| Prueba de atestación | Verificada por una vía estándar; un JSON del instalador no sirve |
| TLS con certificado | Conexión nueva desde navegador con cadena, EKU y serial correctos |
| Copia a PC B | Copiar sólo el certificado público no permite autenticarse; evaluar intentos de exportación sin divulgar claves |
| Usuario autorizado/ajeno | Autorizado entra; ajeno se rechaza aunque comparta la PC |
| Dos clientes autorizados | Ambos funcionan con login separado; logout no cierra caja ni hereda formularios |
| Otro perfil OS | Resultado probado o explícitamente fuera de lo homologado |
| Renovación/revocación | Rotación probada y certificado revocado rechazado en conexión nueva y sesión existente según contrato |
| Administrador aprueba hardware | Actor, fecha y referencia a evidencia externa; no autoaprobación por CSR |

Móviles fuera de esta etapa. La generación en un equipo del operador no homologa los equipos de los clientes. Referencias técnicas y límites se encuentran en [PKI](../pki/README.md).
