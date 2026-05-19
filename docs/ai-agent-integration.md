# Integracion AI Agent

El backend Spring Boot sigue siendo el orquestador de negocio para WhatsApp, ERP/POS, stock, carrito y proformas.

El servicio `imbasac-ai-agent` se usa como cerebro conversacional:

```text
WhatsApp -> Spring Boot -> POST /api/v1/agent/plan -> AI Agent
                              <- intent + actions
Spring Boot ejecuta actions con sus servicios internos
```

Configuracion principal:

```yaml
whatsapp:
  ai-agent:
    enabled: true
    base-url: http://localhost:8010
    api-key: dev-secret
    plan-path: /api/v1/agent/plan
  sales:
    allow-proforma-generation: true
    proforma-station-id: 1
    proforma-created-by: 1
    proforma-series: P001
```

Acciones soportadas inicialmente por Spring Boot:

- `SEARCH_PRODUCTS`: busca productos usando el flujo actual de WhatsApp.
- `ASK_PRODUCT`: pide al cliente el producto faltante.
- `REQUEST_HUMAN`: deriva a asesor.
- `START_QUOTE` / `CAPTURE_CUSTOMER_DATA`: inicia el flujo de proforma actual.
- `SHOW_MENU`: muestra el menu principal.

Si el servicio IA no responde, Spring Boot conserva el flujo automatico anterior.

Cuando el carrito tiene productos y el cliente completa sus datos, Spring Boot intenta crear una proforma V2 con `CreateProformaV2UseCase`. Si la creacion falla, el flujo queda en `READY_TO_PROFORMA` para revision de asesor.
