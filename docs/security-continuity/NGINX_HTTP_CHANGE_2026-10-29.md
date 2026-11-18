# Entrada HTTP del ERP — preparación del 29 de octubre de 2026

Estado: candidato local, sin aplicar al VPS. El usuario confirmó que utiliza el ERP exclusivamente por https://imbasac.cloud. Su acceso administrativo por consola/SSH es una vía distinta que debe conservarse.

## Evidencia recibida

- El sitio imbasac.cloud bloquea /API_SUNAT y /API_SUNAT_GUIAS, con y sin barra final, mediante return 403.
- El sitio HTTP del dominio redirige mediante return 301; el resumen recibido no incluye el destino completo.
- El sitio php-internal-localhost.conf conserva allow 127.0.0.1, allow ::1 y deny all en los proxies a Apache 8093; el resto retorna 404. La escucha 80 no está limitada a loopback, por lo que deben conservarse esas restricciones mientras se revisan dependencias.
- El sitio pos declara listen 80 y proxy_pass al backend 127.0.0.1:8091, sin retorno previo en los controles mostrados. Es una entrada HTTP adicional que debe retirarse antes de confiar en una protección exclusiva de HTTPS.
- Las rutas XML/CDR usan alias y limit_except GET HEAD { deny all; }. Esa directiva restringe métodos, no acredita autorización del usuario o propiedad del documento. No se muestran auth_request, auth_basic ni internal en esos bloques; falta revisar posibles controles omitidos. No se ensayaron descargas de documentos reales.

## Cambio concreto preparado

Archivo: ops/nginx/candidates/pos-deny-unknown-hosts.conf.

Sustituye el sitio pos por un servidor HTTP predeterminado IPv4/IPv6 que devuelve 403. No tiene proxy a Java ni acceso al directorio antiguo de imágenes. Los nombres explícitos imbasac.cloud/www y localhost/127.0.0.1 se siguen resolviendo mediante sus sitios correspondientes.

La decisión del usuario permite preparar el cierre de entrada por IP/nombres desconocidos. Antes de aplicarlo, confirmar consumidores técnicos e integraciones; usar el ERP por dominio no demuestra por sí solo la ausencia de clientes automáticos por IP.

No editar el sitio HTTPS ni los bloqueos SUNAT para este cambio. No activar certificados, modificar PostgreSQL, cambiar firewall o tocar SSH como parte de este candidato.

## Publicación y retorno pendientes

1. Revisar la configuración completa vigente y comprobar que no exista otro default_server IPv4/IPv6 en el puerto 80. Guardar copia exacta de sites-available/pos, su enlace habilitado y la configuración expandida en un directorio privado; no compartir secretos.
2. Comprobar con solicitudes sin credenciales la redirección pública y los consumidores afectados. Verificar el destino canónico de la redirección, ACME/renovación, materiales/imágenes y dependencias internas. No emitir documentos ni ventas reales como prueba.
3. Preparar retorno compatible y verificar respaldo recuperable según el alcance del mantenimiento.
4. Fuera de 07:00–21:00 America/Lima, con intervención máxima de una hora, sustituir solo el contenido del sitio pos. No crear un sitio adicional con un default_server duplicado ni borrar los demás sitios.
5. Ejecutar nginx -t en el VPS antes de recargar. Una validación local no reemplaza esa prueba con la configuración real. Si falla, restaurar el archivo sin recargar.
6. Realizar recarga de Nginx y comprobar dominio, rechazo por IP/nombres desconocidos, consola/SSH e integración interna. La propuesta no requiere reiniciar el VPS, Java, Apache, Redis o PostgreSQL.
7. Si falla una comprobación, restaurar el sitio guardado y validar antes de recargar. Ese retorno conserva temporalmente la entrada HTTP anterior y no cumple la protección final de certificados: no activar la nueva exigencia hasta resolverla. No restaurar una base antigua para revertir este archivo.

## Comprobación local

ops/nginx/tests/verify_http_candidate.py prepara vhosts sintéticos con el candidato y ejecuta nginx -t y diez casos de selección HTTP en un contenedor desechable Nginx 1.24. Solo publica en loopback, no contacta al VPS ni a SUNAT y retira el contenedor al terminar.

Resultado ejecutado el 29 de octubre de 2026: nginx -t correcto y diez casos HTTP correctos en Nginx 1.24 Alpine desechable. El contenedor fue retirado al finalizar. No es una validación del paquete Ubuntu ni de la configuración completa del VPS.

La prueba cubre enrutamiento HTTP del candidato; no acredita TLS/mTLS, el archivo HTTPS real, IPv6 desde Internet, firewall de Hostinger, identidad del equipo ni autorización de XML/CDR.

## Pendientes independientes

- Reglas del firewall de Hostinger y otros filtros: aún no recibidas.
- Proteger las descargas XML/CDR con sesión, permisos y recurso autorizado, conservando ventas, contratos y notas de crédito. Preparar la migración de enlaces antes de retirar los alias.
- Implementar y homologar CA, enrolamiento, vínculo físico y política por rol antes de activar certificados.
- Mantener separada la validación de recuperación de la última venta y destino externo durable.
