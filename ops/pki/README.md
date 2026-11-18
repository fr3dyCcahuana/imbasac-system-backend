# CA y canal TLS preparados para IMBASAC

Preparación local del 30 de octubre de 2026. No hay CA productiva creada, certificado real emitido ni política activada por estos archivos.

## Diseño y límites

- Emisión mediante **OpenSSL 3 `ca`**, sin servicio web de firma ni acceso del JAR a la clave. Se prepara un puente local `sign-request` con comando fijo para que el backend solicite firma después de validar permiso, solicitud y UUID. El firmante corre con otra identidad y devuelve sólo el certificado público; la clave y contraseña CA nunca se entregan al JAR. La firma manual queda como mantenimiento/ensayo, no como labor del operador para cada certificado.
- Raíz de dispositivos fuera de línea y fuera del VPS. Emisora en el mismo VPS bajo usuario dedicado `imbasac-ca`, directorio privado fuera del ERP, sin puerto público ni grupo `www-data`/`posapp`. La aplicación no es la CA y no necesita sus claves.
- El certificado público raíz puede copiarse al VPS; **su clave, contraseña y base de CA raíz nunca**. Una bandera de software y el rechazo del hostname conocido ayudan a evitar errores, pero no prueban desconexión física ni reemplazan la custodia del operador.
- El firmante acepta CSR PKCS#10 con prueba de posesión RSA3072/4096 y SHA256/384/512, o EC P-256 nombrada (`prime256v1`/`secp256r1`) con ECDSA SHA256. Rechaza otras curvas y parámetros EC explícitos. La clave pública se inspecciona con OpenSSL por separado de nombres/atributos del CSR. No copia extensiones/subject del solicitante. Impone `CA:false`, `digitalSignature`, `clientAuth` y SAN `urn:imbasac:device:<uuid>` construido por el operador a partir de un registro aprobado del ERP. La emisora permanece RSA; puede firmar certificados con clave pública EC usando el formato X.509 estándar.
- Vigencias: raíz 10 años, emisora 5 años, dispositivo 1 año (365 días por defecto; 1..365 permitido para pruebas explícitas), CRL emisora 7 días y CRL raíz 180 días. El firmante del ERP emite certificados nuevos por 365 días. Un certificado ya emitido conserva su vencimiento: para cambiarlo hay que renovar, no editar su registro en PostgreSQL. Nunca emitir más allá del emisor; renovar con anticipación. Emitir no significa activar hardware. Publicar revocación sin esperar esos plazos ante compromiso.
- OpenSSL CA tiene una base de seriales/revocaciones que requiere copia consistente de TODO el directorio; no basta con copiar la clave. El lock exclusivo impide operadores simultáneos. Si un proceso muere, revisar proceso/estado/backup antes de retirar manualmente un lock; no reintentar borrándolo automáticamente.

## Preparación de la raíz: sólo estación offline

Usar Python >=3.11 y OpenSSL 3. En Windows, establecer `OPENSSL` a una instalación verificada si no está en PATH. Las contraseñas se introducen localmente en archivos privados fuera del repositorio; no en argumentos, chat, Git ni variables de entorno. En POSIX se exige permiso 0600 o más restrictivo; en Windows comprobar ACL del volumen cifrado. Los directorios de destino tienen que ser nuevos y su padre ya existir.

Ejemplo POSIX en estación de custodia, con ruta real a los scripts:

```sh
umask 077
python3 ops/pki/pki.py init-root --directory /media/CA-OFFLINE/imbasac-root --passphrase-file /media/CA-OFFLINE/root.pass --confirm-offline-workstation
```

Antes: comprobar que el disco no es el VPS, que no se sincroniza a nube y que la contraseña es fuerte/independiente. Mantener dos copias cifradas recuperables en ubicaciones distintas, con custodia de contraseña separada. Registrar huella pública por canal independiente. No cargar certificados raíz como confianza HTTPS universal de los clientes salvo necesidad verificada; el navegador debe seguir validando el HTTPS público normal de `imbasac.cloud`.

## Emisora: usuario dedicado en el VPS, sin aplicación detenida

Ejemplos de rutas previstas; crear usuario/permisos y probarlos en el ensayo antes de ejecutar. **No son órdenes para pegar sin revisar**; no se han creado usuarios ni directorios en el VPS.

```sh
python3 ops/pki/pki.py init-issuer --directory /var/lib/imbasac-ca/device-issuer --passphrase-file /var/lib/imbasac-ca/private/issuer.pass
```

Se obtiene `issuer.csr.pem`. Transferir únicamente ese CSR a la estación offline. Allí, después de comprobar la huella de la solicitud y el contexto:

```sh
python3 ops/pki/pki.py sign-issuer --directory /media/CA-OFFLINE/imbasac-root --csr /media/TRANSFER/issuer.csr.pem --output /media/TRANSFER/issuer.crt.pem --passphrase-file /media/CA-OFFLINE/root.pass --confirm-offline-workstation
python3 ops/pki/pki.py crl --directory /media/CA-OFFLINE/imbasac-root --output /media/TRANSFER/root-20261030.crl.pem --passphrase-file /media/CA-OFFLINE/root.pass
```

Volver al VPS con **certificado público emisora, certificado público raíz y CRL raíz**, verificar sus huellas por canal independiente e instalar:

```sh
python3 ops/pki/pki.py install-issuer --directory /var/lib/imbasac-ca/device-issuer --certificate /var/lib/imbasac-ca/inbox/issuer.crt.pem --root-certificate /var/lib/imbasac-ca/inbox/root.crt.pem
```

No se instala automáticamente confianza ni cambia Nginx. Se necesita calendario de actualización de la CRL raíz offline antes de que caduque; no dejar expirar una CRL de cadena. Revocar/reemplazar emisora ante compromiso exige ceremonia offline y renovación de todas sus credenciales.

## Solicitud, firma e importación pública

1. Administrador registra PC y solicitud en ERP; revisa cliente, cupo/excepción y evidencia. El UUID procede de ERP, no de un campo libre del CSR.
2. Generar clave/CSR **en la PC** con el candidato de `../hardware/`; enviar sólo CSR. Revisar prueba de firma/hardware y navegador. Un CSR válido prueba posesión al generarlo, no protección de hardware.
3. Una vez instalado el puente de firma de la sección siguiente, la pantalla/API administrativa puede pedir firma e importar el resultado con el enrolamiento validado por backend. Para mantenimiento/ensayo manual bajo operador CA dedicado, reemplazar UUID por el registro real aprobado:

```sh
python3 ops/pki/pki.py issue-device --directory /var/lib/imbasac-ca/device-issuer --csr /var/lib/imbasac-ca/inbox/device.csr.pem --device-id 11111111-1111-4111-8111-111111111111 --output /var/lib/imbasac-ca/outbox/device-11111111.crt.pem --passphrase-file /var/lib/imbasac-ca/private/issuer.pass --days 365
```

4. Revisar SAN, emisor, serial, huella y vigencia. Importar **sólo el certificado público** en ERP y entregarlo a la misma PC donde se generó la clave. `*.metadata.json` es evidencia auxiliar, nunca permiso de activación ni atestación.
5. Verificar conexión mTLS real, coincidencia de clave pública, autorización usuario–PC, homologación y aprobación explícita antes de activar. No cambiar automáticamente un equipo pendiente a autorizado por importar PEM o presentar un certificado cualquiera.
6. Mantener temporalmente la validación previa para roles que aún no se migran; un rol ya marcado obligatorio nunca cae a hash ante un fallo.

## Puente de firma para backend sin claves CA

`sign-request` recibe exclusivamente JSON por stdin, hasta 20 000 bytes. Emisión conserva el contrato `requestId` (UUID enrolamiento), `deviceId` y `csrPem`. Revocación usa `operation:"revoke"`, `requestId` (UUID del registro del certificado), `deviceId` y `certificatePem` público. No admite argumentos, archivos, rutas, algoritmos ni comandos proporcionados por el solicitante. `requestId` es idempotente: la misma emisión devuelve el mismo certificado; cambiar CSR, operación o equipo conservando ID devuelve conflicto. La salida de éxito es sólo `{"certificatePem":"..."}` para emitir o `{"revoked":true}` para revocar; errores emiten un código fijo a stderr, sin PEM, secretos ni rutas.

La configuración de producción es fija: `/etc/imbasac/ca-signer.json`, con `issuerDirectory`, `passphraseFile` y `outputDirectory`. Es `root:imbasac-ca` **0640**: root la administra, sólo CA puede leerla y el ERP no pertenece a ese grupo. Un archivo root-owned 0600 sería ilegible para el firmante. Emisora, contraseña y resultados se custodian bajo CA con 0700/0600. Wrapper y helper son root-owned, fuera de carpetas escribibles por ERP/CA. Python arranca en modo aislado `-I` y el wrapper limpia entorno y fija `/usr/bin/openssl`; ignora `OPENSSL`/`PYTHONPATH` del solicitante.

Instalación inicial preparada, **sólo en consola Ubuntu autorizada después de revisar paquete/checksum**:

```sh
sudo sh ops/pki/install_signer.sh
```

No sobreescribe instalaciones previas. Crea identidad CA sin login, rutas privadas, wrapper/helper, configuración y una regla sudoers precisa, validada con `visudo`. No crea claves ni instala CA, cambia Nginx, migra SQL ni activa flags. Preservar los permisos durante actualizaciones; la instalación existente exige una entrega de actualización revisada.

Después de la ceremonia de raíz/emisora, ubicar la contraseña emisora en `/var/lib/imbasac-ca/private/issuer.pass`, 0600 propiedad `imbasac-ca`, y conservar la emisora y su base bajo `/var/lib/imbasac-ca/device-issuer`, 0700 de CA. Configuración y rutas son las del ejemplo versionado; no adaptar caminos desde el cuerpo HTTP. No agregar `posapp` a `imbasac-ca`.

La única invocación delegada es:

```sh
sudo -n -u imbasac-ca /usr/local/lib/imbasac-ca/sign-request
```

El backend usa lista fija de argumentos y deadline/IO limitado; no shell ni propiedades con comandos arbitrarios. El usuario `posapp` no recibe permiso de shell ni de ejecutar `pki.py` libremente. **`NoNewPrivileges=yes` en el servicio del ERP impide el cambio de identidad de sudo**: este puente requiere conservarlo desactivado o sustituir el mecanismo por un servicio local separado revisado. No endurecer esa propiedad a ciegas.

La delegación permite a la aplicación solicitar certificados con perfil restringido; no protege contra un compromiso total del proceso autorizado que abuse de esa capacidad. Conserva las claves emisoras fuera de su lectura y deja estado independiente para investigación. El control administrativo de ERP, revisión física y autorización usuario–PC siguen siendo obligatorios.

Para revocar, el wrapper comprueba certificado único sin clave privada, SAN del UUID solicitado y firma de **la emisora exacta**, no sólo una CA con mismo nombre/raíz. Compara el DER con `newcerts/<serial>.pem` y el registro del índice OpenSSL antes de modificarlo con `openssl ca -revoke`. Un índice que ya indica `R` devuelve éxito estable; una respuesta perdida se concilia contra ese estado. Esto también admite revocar credenciales vencidas sin volverlas utilizables. La aplicación solicita esa operación desde su outbox durable; no se necesita consola por cada revocación administrativa normal. El cierre ERP y la publicación de CRL tienen tiempos diferentes.

Estado de solicitudes en `/var/lib/imbasac-ca/requests`: antes de firmar se persiste identidad/CSR y marca de inicio. Un resultado ya escrito se recupera incluso si se perdió la respuesta. Si hubo corte y no puede acreditarse el certificado final, `SIGN_STATE_UNCERTAIN` cierra el acceso y **no firma de nuevo a ciegas**; revisar lock, proceso y base OpenSSL/`newcerts` para reconciliar con la solicitud antes de retirar una marca. No borrar estado/locks para forzar reintento. Copiar todo el estado CA y solicitudes consistentemente; fsync local no acredita persistencia fuera del VPS.

| Exit | Código | Acción |
|---|---|---|
| 2 | `SIGN_REQUEST_INVALID` | Corregir solicitud, sin emitir |
| 3 | `SIGN_REQUEST_CONFLICT` | No reutilizar ID para otro CSR/equipo |
| 4 | `SIGN_BUSY` | Reintentar la misma solicitud más tarde |
| 5 | `SIGN_STATE_UNCERTAIN` | Conservar evidencia y reconciliar |
| 6 | `SIGN_CONFIG_INVALID` | Revisar instalación/permisos, sin exponer detalles al usuario |
| 7 | `SIGN_FAILED` | Revisar fallo operativo y conservar ID/estado |

Se ensayó el instalador y el comando sudo real dentro de un contenedor desechable: lectura de clave por app y ejecución de shell/argumentos rechazadas; entorno malicioso ignorado; firma y repetición estables aprobadas. Esto no demuestra instalación en el VPS. Referencia: [sudoers, argumentos y entorno](https://www.sudo.ws/docs/man/1.9.14/sudoers.man.pdf).

## Revocación y CRL

La restricción durable del ERP y la invalidación de sesiones se aplican según su contrato al confirmar la revocación en base. La outbox solicita después el cambio idempotente del índice OpenSSL mediante el wrapper separado. **El éxito de la pantalla no significa que Nginx ya cargó una CRL nueva**: la publicación periódica es defensa adicional. Revocar un usuario no revoca el certificado de una PC compartida con otros clientes. Los comandos siguientes son mantenimiento/ensayo; no una tarea manual necesaria por cada revocación normal de la pantalla.

```sh
python3 ops/pki/pki.py revoke --directory /var/lib/imbasac-ca/device-issuer --certificate /var/lib/imbasac-ca/outbox/device-11111111.crt.pem --passphrase-file /var/lib/imbasac-ca/private/issuer.pass
python3 ops/pki/pki.py crl --directory /var/lib/imbasac-ca/device-issuer --output /var/lib/imbasac-ca/outbox/issuer-20261030.crl.pem --passphrase-file /var/lib/imbasac-ca/private/issuer.pass
```

`publish_crl.py` verifica las dos firmas y que resten al menos 24 horas, crea un bundle raíz+emisora y sustituye la copia pública de forma atómica. Primera instalación se ensaya junto con Nginx, sin `--reload-nginx`. Actualizaciones posteriores pueden usarlo con `--reload-nginx`: ejecuta `nginx -t`, recarga y conserva la copia anterior si falla. No mantiene desactivada la validación como reparación. Después comprobar logs/servicio y huellas/seriales desde conexión nueva; si hubo fallo de recarga y los workers ya cambiaron, revisar el estado efectivo antes de repetir. No hay publicación automática ni timer habilitado.

## Actualización diaria de CRL: preparada y desactivada

`install_crl_maintenance.sh` instala helpers, configuración y las tres unidades de `systemd/`; **no ejecuta daemon-reload, inicia servicios ni habilita el timer**. Ejecutar desde el paquete revisado en consola autorizada después de instalar el puente y preparar las CA:

```sh
sudo sh ops/pki/install_crl_maintenance.sh
```

Configuración fija `/etc/imbasac/crl-publication.json`, root:imbasac-ca 0640. Rutas predeterminadas: emisora/contraseña privadas anteriores, spool `/var/lib/imbasac-ca/crl-spool` 0700 CA y archivos públicos root-owned bajo `/etc/imbasac/pki/public`: `device-root.crt.pem`, `device-root.crl.pem` (recibida de la estación offline), `device-issuer.crt.pem`, `device-crls.pem`. Copiar sólo material público a esa carpeta, con 0644 y sin permisos de escritura para ERP/CA. Ningún archivo contiene la clave privada raíz. El instalador no sobreescribe archivos existentes ni inventa esos certificados.

- `imbasac-device-crl-refresh.service` ejecuta `crl-refresh` como **imbasac-ca**. Genera CRL con OpenSSL y lock, verifica su firma/vigencia y sustituye atómicamente el spool firmado. No puede publicar en Nginx.
- `imbasac-device-crl-publish.service` ejecuta `crl-publish` como **root**, después y dependiendo del éxito del refresco. Toma snapshots privados de archivos públicos/spool antes de validar para evitar publicar bytes diferentes de los comprobados. Verifica cadena, firmas, `lastUpdate` utilizable y al menos 24 horas restantes en ambas CRL; publica bundle atómico, `nginx -t` y recarga. Cada llamada Nginx/systemctl tiene 15 s y la unidad 60 s. Un error/timeout conserva la copia anterior y deja fallo visible; no desactiva revocación.
- `imbasac-device-crl-publish.timer` programa diariamente **22:15 America/Lima**, con hasta cinco minutos de distribución. `Persistent=false` evita ejecutar una recuperación de calendario durante horario comercial tras un arranque; revisar fallos/días omitidos antes de que expire la CRL. Unidades protegidas y sin credenciales raíz en el JAR. Revisar las rutas reales de logs para `nginx -t` antes de aplicar `ProtectSystem=strict` del publicador.

Primera vez: comprobar permisos/material público, generar spool y publicar primer bundle sin recargar mediante el helper manual, validar configuración y ensayar todos los roles. Sólo cuando corresponda activar en la ventana autorizada:

```sh
sudo systemctl daemon-reload
sudo systemctl start imbasac-device-crl-publish.service
sudo systemctl status imbasac-device-crl-refresh.service imbasac-device-crl-publish.service --no-pager
sudo systemctl enable --now imbasac-device-crl-publish.timer
```

Esos comandos están preparados; **no fueron ejecutados en el VPS**. Para inspección: `systemctl list-timers --all`, `systemctl --failed` y `journalctl -u imbasac-device-crl-refresh -u imbasac-device-crl-publish`. Los helpers emiten `CRL_REFRESH_OK/FAILED` o `CRL_PUBLISH_OK/FAILED`, no secretos. Instalar vigilancia de fallos y vigencias; no hay avisos WhatsApp habilitados. La raíz offline todavía requiere renovar su CRL antes de sus 180 días, con margen y transferencia verificada; el timer no puede hacerlo sin su clave, que debe permanecer fuera.

La latencia de CRL tras una revocación normal es aproximadamente hasta el siguiente ciclo diario, además de reintentos pendientes de outbox. El backend debe seguir denegando por su registro vigente en cada petición protegida y sesión según el contrato; no esperar a la CRL para cerrar acceso. Reconciliación de CA ante desastre, estado incierto o compromiso sigue siendo intervención operativa extraordinaria.

## Canal interno Nginx–backend: CA diferente

`internal_tls.py --directory <directorio-nuevo-privado> --passphrase-file <secreto-0600> --prepare-java-stores` prepara CA exclusiva del canal interno, clave cifrada CA y hojas de 180 días. El flag de almacenes requiere Java 21 `keytool` en PATH (o variable `KEYTOOL` con ruta al ejecutable):

- Backend: `backend.crt.pem` + `backend.key.pem`, `serverAuth`, SAN DNS `localhost`, IP `127.0.0.1` y `::1`.
- Nginx: `nginx-client-chain.pem` + `nginx-client.key`, `clientAuth`, URI `urn:imbasac:service:nginx`; huella SHA256 en `public-metadata.json` para el pin del backend. `nginx.crt.pem` conserva sólo la hoja pública.
- `internal-ca.crt.pem` es confianza exclusiva del canal privado. No usar esta CA como CA de dispositivos, ni instalar la DeviceCA en el truststore de cliente del servidor interno.
- `root.crt` es copia pública de la CA interna con el nombre que espera el fragmento Nginx. `backend.p12` contiene identidad backend y `proxy-trust.p12` contiene sólo la CA interna, coincidiendo con `ops/nginx/application-security-template.yml`. La contraseña inicial de ambos almacenes se lee del archivo privado (no se imprime); configurar `IMBASAC_BACKEND_KEYSTORE_PASSWORD` y `IMBASAC_PROXY_TRUSTSTORE_PASSWORD` fuera de Git con ese valor y permisos restrictivos. Se puede cambiar posteriormente con procedimiento de rotación.

Las hojas sin contraseña permiten inicio desatendido: proteger por permisos y propietario exactos, separar clave Nginx de la del backend. La CA interna cifrada queda fuera del JAR; la aplicación nunca requiere su clave. Preferir PEM soportado por Spring Boot 3.4 o preparar almacén privado fuera del repositorio, sin passwords en configuración versionada. Nginx verifica upstream/hostname `localhost`; backend exige certificado cliente y pin+URI configurados. Preparar rotación con superposición de dos pins y ensayo antes del vencimiento. No reutilizar el certificado público Let's Encrypt ni el de un dispositivo.

## Verificación y fuentes

`python3 -m unittest discover -s ops/pki -p 'test_*.py' -v` usa claves de software desechables en carpeta temporal externa. Verifica perfiles, ataques por extensiones CSR, rechazo de claves débiles/curvas ajenas, serialización, no sobrescritura, emisión/revocación idempotentes, CRL vencida/futura/firma ajena, permisos, timeout de recarga y separación del canal interno. El ensayo de instalación/sudo requiere opt-in y contenedor desechable. No demuestra TPM, Secure Enclave, compatibilidad de navegador, ejecución real de systemd o recuperación CA productiva.

Referencias primarias: [OpenSSL CA y límites de concurrencia](https://docs.openssl.org/3.0/man1/openssl-ca/), [systemd timers](https://freedesktop.org/software/systemd/man/latest/systemd.timer.html), [Microsoft certreq](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/certreq_1), [atestación TPM](https://learn.microsoft.com/en-us/windows-server/identity/ad-ds/manage/component-updates/tpm-key-attestation), [CSR en Keychain](https://developer.apple.com/help/account/certificates/create-a-certificate-signing-request), [Managed Device Attestation de Apple](https://support.apple.com/guide/deployment/managed-device-attestation-dep28afbde6a/web).
