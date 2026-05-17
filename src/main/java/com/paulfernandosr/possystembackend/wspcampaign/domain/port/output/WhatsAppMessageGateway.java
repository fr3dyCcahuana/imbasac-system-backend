package com.paulfernandosr.possystembackend.wspcampaign.domain.port.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppMessageSendResult;
import com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output.MetaWhatsAppMessageGateway.InteractiveButton;
import com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output.MetaWhatsAppMessageGateway.InteractiveListRow;

import java.util.List;

public interface WhatsAppMessageGateway {
    WhatsAppMessageSendResult sendText(String toWaId, String body);
    WhatsAppMessageSendResult sendTemplate(String toWaId, String templateName, String languageCode);
    WhatsAppMessageSendResult sendDocumentByLink(String toWaId, String documentUrl, String filename, String caption);
    WhatsAppMessageSendResult sendButtons(String toWaId, String body, List<InteractiveButton> buttons);
    WhatsAppMessageSendResult sendList(String toWaId, String body, String buttonText, String sectionTitle, List<InteractiveListRow> rows);

    /**
     * Envío genérico de una plantilla aprobada por Meta.
     *
     * Este método queda en el componente base de WhatsApp porque solo conoce el canal técnico
     * de envío. La lógica de campañas, destinatarios, métricas y estados debe vivir en
     * wspcampaign.
     */
    WhatsAppMessageSendResult sendTemplateWithImage(
            String toWaId,
            String templateName,
            String languageCode,
            String imageUrl,
            List<String> bodyParameters
    );

    /**
     * Compatibilidad temporal con el nombre anterior usado por campaña.
     * Preferir sendTemplateWithImage(...) para que el componente WhatsApp no tenga lógica de campaña.
     */
    default WhatsAppMessageSendResult sendMarketingTemplateWithImage(
            String toWaId,
            String templateName,
            String languageCode,
            String imageUrl,
            List<String> bodyParameters
    ) {
        return sendTemplateWithImage(toWaId, templateName, languageCode, imageUrl, bodyParameters);
    }
}
