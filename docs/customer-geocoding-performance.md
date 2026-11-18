# Rendimiento de propuestas de ubicación

Endpoint: `POST /customers/{customerId}/addresses/{addressId}/geolocation/suggest`.

## Trabajo evitado

La búsqueda consulta Nominatim de forma síncrona. La configuración revisada limita
las consultas externas a cuatro por minuto, compartidas con el proceso automático:
son al menos 15 segundos entre inicios de consultas. La primera consulta libre
no tiene esa espera, pero sí depende de la red y del proveedor.

Antes de la optimización se repetía también una consulta HTTP exitosa sin
resultados, usando `max-retries`, antes de pasar a la siguiente variante. Con la
cuota actual, cada repetición podía añadir una espera de 15 segundos.

Ahora:

- Se reutiliza el cliente HTTP y sus conexiones.
- Se guardan las respuestas de cada consulta externa en una caché en memoria con
  vencimiento y límite de entradas. Las respuestas vacías también se guardan.
- Solicitudes simultáneas de la misma consulta comparten el trabajo en curso.
- Los reintentos se reservan para errores de red, HTTP 429 y HTTP 5xx. Una
  respuesta correcta vacía o rechazada por ubicación avanza a otra variante sin
  repetirla. Si el proveedor falla definitivamente, se devuelve HTTP 502 sin
  repetir el fallo para todas las variantes de la dirección.

La caché guarda resultados del proveedor, no fichas de clientes. Siempre se vuelve
a consultar la dirección solicitada y se valida su UBIGEO vigente. La clave incluye
la consulta, URL del proveedor, país, idioma y límites geográficos. Cambiar el texto
de la dirección produce una consulta distinta. Las propuestas siguen necesitando
confirmación; buscar no escribe coordenadas.

## Configuración

Valores por defecto de `app.customer-map.geocoder`:

```yaml
cache-ttl-seconds: 86400
empty-cache-ttl-seconds: 900
cache-max-entries: 1000
```

Un TTL igual a cero desactiva la caché de ese tipo de resultado; una capacidad
igual a cero desactiva la caché completa. Reiniciar el backend vacía la caché.
No se requiere Redis, migraciones SQL ni cambios de frontend.

La cuota existente permanece intacta. La
[política de Nominatim](https://operations.osmfoundation.org/policies/nominatim/)
exige caché, limita el uso general a una petición por segundo y restringe procesos
periódicos a cuatro por minuto. Aumentar esa propiedad sin revisar el proveedor y
el proceso automático no es una optimización apropiada.

## Medición y pruebas

Las respuestas exitosas incluyen `Server-Timing: geolocation;dur=...` con el tiempo
en milisegundos dentro del servicio de backend. Permite compararlo con el tiempo
total de la petición en el navegador. No incluye filtros de autenticación ni el
tránsito por red y proxy. El nivel DEBUG de `CustomerMapService` permite distinguir
aciertos de caché, espera por cuota y duración de la consulta externa; esos
mensajes no incluyen el texto de la dirección.

Verificación local con Java 21 y Maven instalado si el wrapper está incompleto:

```powershell
mvn -q "-Dtest=CustomerMapGeocoderPerformanceTest,CustomerMapServiceTest,CustomerMapZoomConfigurationTest" test
```

El ensayo usa un servidor HTTP en `127.0.0.1`, con una respuesta retrasada 2900 ms.
Comprueba que repetir la propuesta no repite esa consulta. También cubre respuestas
vacías, invalidación por dirección/proveedor, vencimiento, capacidad, concurrencia,
reintentos, conservación de la cuota y validación del UBIGEO actual.

Las pruebas no contactan Nominatim ni PostgreSQL. No establecen cuánto tardará la
primera búsqueda en producción, ni miden el DNS, TLS o la carga real del proveedor.
