# Descarga privada de XML/CDR

Código local preparado para reemplazar la descarga directa mediante alias públicos de Nginx. No se expone PHP/Apache ni se accede por HTTP a su puerto. La activación y cierre de alias se entregan juntos con el frontend y permisos; no representa un cambio aplicado en el VPS.

## Contrato

`GET /security/sunat-files/{xml|cdr}/{filename}`, relativo a la API existente. Requiere autenticación y las validaciones vigentes de sesión/certificado; además exige `VIEW_SUNAT_DOCUMENTS` y un permiso de lectura acorde con las referencias almacenadas en el ERP:

- `sale` o `credit_note`: `MANAGE_SALES_LIST`.
- `contract_sunat_draft`: `MANAGE_CONTRACTS_LIST`.

Las consultas usan los campos `sunat_xml_path`/`sunat_cdr_path` de las tablas reales revisadas, con selección de columna desde código fijo y parámetros SQL. Se comprueba el sufijo interno exacto `/API_SUNAT/files/facturacion_electronica/FIRMA/{filename}` o `.../CDR/{filename}`, según el tipo. Que un archivo exista físicamente no basta; debe estar referenciado por un recurso del módulo que el usuario pueda leer.

Estos son permisos de lectura globales de los módulos actuales. **No se presenta como implementado un vínculo de propiedad cliente–comprobante**, porque la consulta y pantalla de historial revisadas no aplican ese vínculo. No conceder estos permisos globales a clientes externos suponiendo que solo mostrarán sus propios comprobantes. El nuevo permiso no se concede automáticamente.

El nombre se limita a 200 caracteres ASCII, comienza por letra/número, solo admite letras/números, guion, guion bajo y punto, y termina en `.xml`. Se rechazan `..`, barras, codificación `%`, controles, query, fragmentos y nombres arbitrarios de ruta. La solicitud no elige directorios, tablas, columnas ni URLs de servicios.

## Configuración

```yaml
security:
  sunat-files:
    enabled: false
    xml-directory: /var/www/apache/API_SUNAT/files/facturacion_electronica/FIRMA
    cdr-directory: /var/www/apache/API_SUNAT/files/facturacion_electronica/CDR
```

El modo apagado devuelve indisponibilidad a un usuario con los permisos necesarios, sin recurrir a la URL pública anterior. Preparar el permiso mediante `20261030_private_sunat_files.sql`, asignarlo expresamente a los operadores adecuados y verificar lectura de los directorios por el usuario del servicio antes de activar.

Después de comprobar permisos y referencias se resuelve la base real, se exige un archivo regular directamente dentro de esa base y se rechazan enlaces simbólicos. La apertura usa `NOFOLLOW_LINKS`, además de comprobar los atributos sin seguir enlaces. No se permite que un cambio del enlace entre comprobación y apertura derive en lectura fuera de la base. La lectura tiene límite de 16 MiB, incluyendo archivos que crezcan mientras se leen.

Las bases configuradas y sus directorios antecesores son configuración administrativa de confianza: el proceso que genera XML debe poder escribir los documentos, pero no sustituir los directorios base o sus antecesores por enlaces. Comprobar esos propietarios/permisos en Ubuntu forma parte de la preparación de publicación; no se garantiza resistencia frente a un administrador del sistema que altere arbitrariamente el árbol de archivos.

La respuesta es un adjunto XML con `Cache-Control: no-store`, `X-Content-Type-Options: nosniff` y política de contenido restrictiva. Los errores no incluyen rutas locales ni mensajes de SQL. El servidor entrega los bytes del documento; no analiza XML ni resuelve entidades externas.

## Frontend y publicación coordinada

Las URL de `SunatFilePublicUrlService` permanecen como referencias de compatibilidad. Los seis enlaces XML/CDR de la pantalla `ventas-historial` deben usar descarga autenticada de Blob mediante la API propia, sin navegar a la URL pública, pasar JWT en query ni enviar Bearer a un origen externo. La respuesta tardía de una identidad anterior no debe descargar su archivo en la sesión nueva.

El candidato Nginx debe denegar las rutas públicas antiguas cuando el backend, frontend, directorios y permisos estén listos. Cerrar los alias por separado sin actualizar los consumidores rompería las descargas; conservarlos públicos como fallback dejaría la protección incompleta. La API PHP sigue accesible únicamente desde el backend en su canal privado.

## Comprobaciones

Las pruebas del servicio/HTTP cubren permisos antes del archivo, modo apagado, nombres inválidos y codificados, archivo sin referencia, separación ventas/contratos, selección de directorio, tamaño máximo, errores privados y encabezados de adjunto. La prueba de enlaces simbólicos utiliza uno real cuando el sistema permite crearlo; en Windows sin ese privilegio comprueba la rama de atributos `NOFOLLOW_LINKS` mediante sustitución del sistema de archivos. Esa variante no homologa por sí sola el sistema de archivos Ubuntu de producción.

Las pruebas PostgreSQL verifican referencias de ventas, notas de crédito y contratos, tipo correcto, parámetros frente a metacaracteres y migración repetible sin concesión de permisos. Se ejecutan sobre esquemas aleatorios de una base aislada. Los resultados reales se registran al cerrar la tarea.

Resultado del 30 de octubre de 2026: **28 pruebas correctas**, 24 de servicio/HTTP y 4 con PostgreSQL 18.6 aislado, ninguna omitida. Se ejecutaron junto con las 24 pruebas de certificados/sesiones: selección final de 52 casos correcta. No se contactó producción ni Apache/SUNAT.
