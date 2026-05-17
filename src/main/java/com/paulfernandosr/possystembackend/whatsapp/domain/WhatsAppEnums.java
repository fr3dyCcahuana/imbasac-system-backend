package com.paulfernandosr.possystembackend.whatsapp.domain;

public final class WhatsAppEnums {
    private WhatsAppEnums() {}

    public enum ConversationStatus { OPEN, PENDING_HUMAN, QUOTED, CLOSED, ARCHIVED }
    public enum MessageDirection { INBOUND, OUTBOUND }
    public enum MessageType { TEXT, TEMPLATE, IMAGE, DOCUMENT, AUDIO, VIDEO, INTERACTIVE, LOCATION, CONTACTS, UNKNOWN }
    public enum MessageStatus { QUEUED, SENT, ACCEPTED, DELIVERED, READ, FAILED, RECEIVED }
    public enum AutomationMode { BOT, HUMAN, PAUSED }

    /**
     * Estados del flujo conversacional.
     * IDLE: puede recibir búsqueda libre o comandos de menú.
     * WAITING_PRODUCT_QUERY: el bot pidió que escriban el producto.
     * WAITING_PRODUCT_SELECTION: el bot mostró lista y espera selección.
     * WAITING_QUANTITY: el bot espera cantidad para el producto seleccionado.
     * WAITING_ADD_MORE: producto agregado; espera agregar más, generar proforma o nueva búsqueda.
     * WAITING_BATCH_CONFIRM: el bot detectó una lista de códigos y espera confirmación.
     * WAITING_BATCH_QUANTITY: lista de códigos detectada; se pide cantidad producto por producto.
     * WAITING_BATCH_CUSTOM_QUANTITY: espera cantidad manual para el producto actual de la lista masiva.
     * WAITING_CUSTOMER_DOCUMENT: espera selección de identificación (DNI/RUC/NOMBRE/OMITIR).
     * WAITING_DNI_NUMBER: espera número de DNI.
     * WAITING_RUC_NUMBER: espera número de RUC.
     * WAITING_CUSTOMER_NAME: espera nombre opcional para auditoría.
     */
    public enum ConversationState {
        IDLE,
        WAITING_PRODUCT_QUERY,
        WAITING_PRODUCT_SELECTION,
        WAITING_QUANTITY,
        WAITING_ADD_MORE,
        WAITING_BATCH_CONFIRM,
        WAITING_BATCH_QUANTITY,
        WAITING_BATCH_CUSTOM_QUANTITY,
        WAITING_CUSTOMER_DOCUMENT,
        WAITING_DNI_NUMBER,
        WAITING_RUC_NUMBER,
        WAITING_CUSTOMER_NAME,
        GENERATING_PROFORMA,
        PROFORMA_CREATED
    }

    public enum CartStatus { OPEN, WAITING_CUSTOMER, READY_TO_PROFORMA, PROFORMA_CREATED, CANCELLED }
}
