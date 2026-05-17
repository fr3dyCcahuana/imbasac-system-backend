# QA del flujo WhatsApp → Proforma IMBASAC

Este documento evita corregir el bot por síntomas. Cada cambio del agente debe pasar esta matriz antes de probar con un número real de WhatsApp.

## Regla de oro

- Antes de crear la proforma, el resumen puede salir del carrito temporal.
- Después de crear o actualizar una proforma, el resumen debe salir de la proforma real (`ProformaV2Response` o detalle consultado), no del carrito temporal.
- Si existe `lastProformaId` y la proforma sigue pendiente, el bot debe actualizar esa proforma; no debe crear otra por error.

## Activar simulador local

Solo en desarrollo o QA:

```properties
whatsapp.debug.simulation-enabled=true
whatsapp.debug.simulation-wa-prefix=SIM_
```

Con `waId` que empieza por `SIM_`, `SendWhatsAppMessageService` guarda los mensajes salientes en BD pero no llama a Meta.

> Importante: el simulador sí usa la BD actual. Si el flujo genera o actualiza proformas, lo hará en tu ambiente local/QA.

## Endpoint

```http
POST /whatsapp/debug/simulate-flow
Content-Type: application/json
```

### Caso base RUC + proforma

```json
{
  "waId": "SIM_QA_RUC_001",
  "profileName": "Fredy QA",
  "resetBefore": true,
  "messages": [
    "hola",
    "proforma",
    "DK151092",
    "2",
    "generar proforma",
    "ruc",
    "20101036813"
  ]
}
```

Validar:

- `finalState = PROFORMA_CREATED`.
- `lastProformaId != null`.
- El último mensaje saliente debe decir `Documento: RUC 20101036813`.
- El último mensaje saliente debe decir el cliente resuelto.
- El resumen debe venir de la proforma generada.

### Caso edición post-proforma

Usa el mismo `waId` del caso anterior y no reinicies:

```json
{
  "waId": "SIM_QA_RUC_001",
  "profileName": "Fredy QA",
  "resetBefore": false,
  "messages": [
    "agrega este codigo KTR03011468",
    "2",
    "actualizar"
  ]
}
```

Validar:

- `lastProformaId` debe mantenerse igual al del caso base.
- El carrito abierto debe estar asociado a `openCartProformaId`.
- El último mensaje debe decir `Actualicé tu proforma`.
- El documento no debe perderse; debe seguir mostrando `RUC 20101036813`.
- El resumen debe reflejar los ítems reales de la proforma actualizada.

## Matriz mínima obligatoria

| Código | Flujo | Entrada principal | Resultado esperado |
|---|---|---|---|
| QA-01 | Inicio limpio | `hola` | Muestra menú principal y estado `IDLE` |
| QA-02 | Producto exacto | `DK151092` | Encuentra producto por SKU/código aunque el texto sea natural |
| QA-03 | Frase con código | `agrega este codigo DK151092` | Extrae `DK151092` y lo encuentra |
| QA-04 | Cantidad válida | `2` | Agrega producto al carrito y pasa a `WAITING_ADD_MORE` |
| QA-05 | Generar con RUC | `ruc` + `20101036813` | Crea proforma con `customerId`, RUC, razón social y dirección |
| QA-06 | Generar con DNI | `dni` + 8 dígitos | Crea proforma con cliente natural |
| QA-07 | Venta diaria | `omitir` / sin documento | Crea proforma como venta diaria o cliente de chat según regla vigente |
| QA-08 | Producto no encontrado | código inexistente | No crea ítem y pide búsqueda más corta/código/marca/modelo |
| QA-09 | Sin stock | producto exacto sin stock | No agrega producto; ofrece buscar similar o asesor |
| QA-10 | Editar post-proforma | agregar producto luego de creada | Actualiza la misma proforma pendiente |
| QA-11 | Repetir producto | mismo SKU varias veces | Cantidad final debe fusionarse según regla del backend |
| QA-12 | Documento persistente | edición post-proforma | El mensaje final no debe mostrar `Documento: No indicado` si la proforma ya tiene documento |
| QA-13 | Resumen real | editar post-proforma | El resumen final debe coincidir con el detalle real de la proforma |
| QA-14 | Cancelar | `cancelar` | Cancela carrito abierto y vuelve a menú |
| QA-15 | Reiniciar | `hola` después de flujo activo | Cancela flujo temporal y vuelve a menú |

## Criterio para aprobar cambios

Un cambio se considera estable cuando pasan, como mínimo:

1. QA-01 a QA-05.
2. QA-10 a QA-13.
3. Una prueba real por WhatsApp después de pasar el simulador.

## Lectura rápida del response del simulador

- `steps[]`: muestra estado antes/después por cada mensaje.
- `openCartItems[]`: muestra el carrito temporal actual.
- `outboundMessages[]`: muestra todos los mensajes que el bot habría enviado.
- `lastProformaId`: confirma si se creó o actualizó una proforma.

