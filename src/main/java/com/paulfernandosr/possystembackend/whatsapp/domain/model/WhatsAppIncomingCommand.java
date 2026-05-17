package com.paulfernandosr.possystembackend.whatsapp.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.util.Set;

/**
 * Normaliza entradas de WhatsApp para no depender de si el usuario escribió texto,
 * tocó botón o seleccionó una lista.
 */
public record WhatsAppIncomingCommand(
        String type,
        String rawText,
        String normalizedText,
        String payloadId,
        String title
) {
    private static final Set<String> GREETING_OR_RESET = Set.of(
            "hola", "ola", "holaa", "holaaa", "buenas", "buen dia", "buenos dias",
            "inicio", "menu", "menú", "empezar", "empecemos", "empecemos desde cero",
            "desde cero", "nuevo", "nuevo pedido", "reiniciar", "reset"
    );

    public static WhatsAppIncomingCommand fromMessage(JsonNode message) {
        String messageType = text(message.path("type"));

        if ("interactive".equalsIgnoreCase(messageType)) {
            JsonNode interactive = message.path("interactive");
            String interactiveType = text(interactive.path("type"));

            if ("button_reply".equalsIgnoreCase(interactiveType)) {
                JsonNode button = interactive.path("button_reply");
                String id = text(button.path("id"));
                String title = text(button.path("title"));
                return new WhatsAppIncomingCommand("BUTTON", firstNonBlank(title, id), normalize(firstNonBlank(title, id)), id, title);
            }

            if ("list_reply".equalsIgnoreCase(interactiveType)) {
                JsonNode list = interactive.path("list_reply");
                String id = text(list.path("id"));
                String title = text(list.path("title"));
                return new WhatsAppIncomingCommand("LIST", firstNonBlank(title, id), normalize(firstNonBlank(title, id)), id, title);
            }
        }

        String body = text(message.path("text").path("body"));
        if ((body == null || body.isBlank()) && "image".equalsIgnoreCase(messageType)) {
            body = text(message.path("image").path("caption"));
        }
        if ((body == null || body.isBlank()) && "document".equalsIgnoreCase(messageType)) {
            body = text(message.path("document").path("caption"));
            if (body == null || body.isBlank()) {
                body = text(message.path("document").path("filename"));
            }
        }
        return fromText(body);
    }

    public static WhatsAppIncomingCommand fromText(String text) {
        return new WhatsAppIncomingCommand("TEXT", text, normalize(text), null, text);
    }

    /** Reinicia o muestra menú solo con saludos/comandos de inicio reales. */
    public boolean isGreetingOrReset() {
        if (normalizedText == null || normalizedText.isBlank()) return false;
        if (payloadIs("MENU")) return true;
        if (GREETING_OR_RESET.contains(normalizedText)) return true;
        return normalizedText.matches("^(hola|ola|buenas)(\\s+.*)?$");
    }

    public boolean isSearchMenu() {
        return payloadIs("MENU_SEARCH") || equalsAny("buscar producto", "buscar", "producto");
    }

    public boolean isProformaMenu() {
        return payloadIs("MENU_PROFORMA") || equalsAny("proforma", "cotizacion", "cotización", "cotizar", "generar proforma");
    }

    public boolean isAdvisor() {
        return payloadIs("MENU_ADVISOR") || equalsAny("asesor", "humano", "vendedor", "atencion", "atención");
    }

    public boolean isYes() {
        return payloadIs("ADD_MORE_YES") || equalsAny("si", "sí", "s", "yes", "ok", "dale", "agregar", "agregar otro", "otro", "mas", "más");
    }

    public boolean isNo() {
        return payloadIs("ADD_MORE_NO") || equalsAny("no", "n", "generar", "generar proforma", "actualizar", "actualizar proforma", "guardar", "guardar cambios", "terminar pedido", "finalizar", "listo");
    }

    public boolean isCancel() {
        return payloadIs("CANCEL") || equalsAny("cancelar", "cancela", "salir", "anular", "limpiar");
    }

    public boolean isThanks() {
        return equalsAny("gracias", "ok gracias", "muchas gracias", "thank you");
    }

    public boolean isDniOption() {
        return payloadIs("DOC_DNI") || equalsAny("dni");
    }

    public boolean isRucOption() {
        return payloadIs("DOC_RUC") || equalsAny("ruc");
    }

    public boolean isNameOnlyOption() {
        return payloadIs("DOC_NAME") || equalsAny("nombre", "solo nombre", "con nombre");
    }

    public boolean isOmitCustomerIdentity() {
        return payloadIs("DOC_OMIT") || equalsAny("omitir", "sin datos", "sin documento", "no identificar", "visitante");
    }

    public boolean isSkipName() {
        return payloadIs("SKIP_NAME") || payloadIs("DOC_OMIT") || equalsAny("omitir", "sin nombre", "no", "n", "-", "ninguno");
    }

    public boolean isConfirmBatch() {
        return payloadIs("BATCH_CONFIRM") || equalsAny("confirmar", "confirmo", "si confirmo", "sí confirmo", "confirmar lista", "agregar lista", "agregar al pedido");
    }

    public boolean isEditBatchQuantities() {
        return payloadIs("BATCH_EDIT_QTY") || equalsAny("editar cantidades", "cambiar cantidades", "cantidades", "modificar cantidades");
    }

    public boolean isProductSelection() {
        if (payloadId != null && payloadId.toUpperCase().startsWith("PRODUCT_")) return true;
        return normalizedText != null && normalizedText.matches("^[0-9]{1,2}$");
    }

    public Integer numericSelection() {
        String source = firstNonBlank(payloadId, normalizedText);
        if (source == null || source.isBlank()) return null;
        String upper = source.toUpperCase();
        if (upper.startsWith("PRODUCT_")) {
            source = upper.substring("PRODUCT_".length());
        }
        String digits = source.replaceAll("^.*?(\\d+).*$", "$1");
        if (digits == null || digits.isBlank() || !digits.matches("\\d+")) return null;
        try {
            return Integer.parseInt(digits);
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean isBatchQuantitySelection() {
        if (payloadId == null) return false;
        String upper = payloadId.toUpperCase();
        return upper.startsWith("BQTY_") || upper.equals("BATCH_SKIP") || upper.equals("BATCH_QTY_OTHER");
    }

    public boolean isBatchQuantityOther() {
        return payloadIs("BATCH_QTY_OTHER");
    }

    public java.math.BigDecimal batchQuantityValue() {
        if (payloadId == null) return null;
        String upper = payloadId.toUpperCase();
        if (upper.equals("BATCH_SKIP") || upper.equals("BQTY_0")) {
            return java.math.BigDecimal.ZERO;
        }
        if (upper.startsWith("BQTY_")) {
            String raw = upper.substring("BQTY_".length());
            try { return new java.math.BigDecimal(raw.replace('_', '.')); } catch (Exception ignored) { return null; }
        }
        return null;
    }

    public boolean hasProductSearchText() {
        if (normalizedText == null || normalizedText.length() < 2) return false;
        if (isGreetingOrReset() || isYes() || isNo() || isCancel() || isThanks() || isAdvisor()) return false;
        if (isSearchMenu() || isProformaMenu() || isDniOption() || isRucOption() || isNameOnlyOption() || isOmitCustomerIdentity() || isSkipName()) return false;
        if (normalizedText.matches("^[0-9]{1,3}$")) return false;
        return true;
    }

    public boolean looksLikeDocumentNumber() {
        String digits = businessText().replaceAll("\\D", "");
        return digits.length() == 8 || digits.length() == 11;
    }

    public String businessText() {
        return rawText == null ? "" : rawText.trim();
    }

    private boolean payloadIs(String value) {
        return payloadId != null && payloadId.equalsIgnoreCase(value);
    }

    private boolean equalsAny(String... terms) {
        if (normalizedText == null) return false;
        for (String term : terms) {
            if (normalizedText.equals(normalize(term))) return true;
        }
        return false;
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String n = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .trim();
        n = n.replaceAll("[¡!¿?.,;:]+", " ").replaceAll("\\s+", " ").trim();
        return n;
    }
}
