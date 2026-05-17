package com.paulfernandosr.possystembackend.whatsapp.domain.port.output;

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
}
