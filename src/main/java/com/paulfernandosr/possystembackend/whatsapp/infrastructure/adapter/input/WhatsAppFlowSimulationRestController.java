package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.whatsapp.application.InboundWhatsAppAutomationService;
import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppCartRepository;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppContactRepository;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppConversationRepository;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppMessageRepository;
import com.paulfernandosr.possystembackend.whatsapp.application.WhatsAppIntegrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Endpoint de QA local para probar el flujo conversacional sin enviar mensajes reales a Meta.
 *
 * Para activarlo en desarrollo:
 * whatsapp.debug.simulation-enabled=true
 *
 * Reglas de seguridad:
 * - Solo debería usarse localmente o en ambiente QA.
 * - Los waId generados empiezan con SIM_, y SendWhatsAppMessageService evita llamar a Meta para esos waId.
 * - Sí puede crear/actualizar carritos y proformas en la BD del ambiente donde se ejecute.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/whatsapp/debug", "/api/whatsapp/debug"})
public class WhatsAppFlowSimulationRestController {
    private final WhatsAppContactRepository contactRepository;
    private final WhatsAppConversationRepository conversationRepository;
    private final WhatsAppCartRepository cartRepository;
    private final WhatsAppMessageRepository messageRepository;
    private final InboundWhatsAppAutomationService automationService;
    private final WhatsAppIntegrationProperties properties;

    @PostMapping("/simulate-flow")
    public ResponseEntity<SimulationResponse> simulateFlow(@RequestBody SimulationRequest request) {
        if (!properties.getDebug().isSimulationEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Simulación WhatsApp desactivada. Activa whatsapp.debug.simulation-enabled=true en ambiente local/QA."
            );
        }

        SimulationRequest safeRequest = request == null ? new SimulationRequest(null, null, null, List.of()) : request;
        List<String> messages = safeRequest.messages() == null ? List.of() : safeRequest.messages();
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Debes enviar al menos un mensaje en messages[].");
        }

        String waId = firstNonBlank(safeRequest.waId(), "SIM_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        String profileName = firstNonBlank(safeRequest.profileName(), "Cliente Simulado");

        WhatsAppContact contact = contactRepository.upsertByWaId(waId, waId, profileName);
        WhatsAppConversation conversation = conversationRepository.findOrCreateOpenConversation(contact);

        if (Boolean.TRUE.equals(safeRequest.resetBefore())) {
            cartRepository.cancelOpenCarts(conversation.getId());
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.IDLE);
            conversationRepository.updateStatus(conversation.getId(), WhatsAppEnums.ConversationStatus.OPEN);
            conversation = conversationRepository.findById(conversation.getId()).orElse(conversation);
        }

        Long lastMessageIdBeforeSimulation = messageRepository.findMaxIdByConversation(conversation.getId());
        if (lastMessageIdBeforeSimulation == null) {
            lastMessageIdBeforeSimulation = 0L;
        }

        List<StepSnapshot> steps = new ArrayList<>();
        for (String message : messages) {
            WhatsAppEnums.ConversationState beforeState = conversation.getConversationState();
            Long beforeProformaId = conversation.getLastProformaId();

            automationService.handleIncomingText(conversation, message);

            conversation = conversationRepository.findById(conversation.getId()).orElse(conversation);
            WhatsAppCart cart = cartRepository.findOpenByConversationId(conversation.getId()).orElse(null);
            List<WhatsAppCartItem> cartItems = cart == null ? List.of() : cartRepository.findItems(cart.getId());

            steps.add(new StepSnapshot(
                    message,
                    beforeState,
                    conversation.getConversationState(),
                    beforeProformaId,
                    conversation.getLastProformaId(),
                    cart == null ? null : cart.getId(),
                    cart == null ? null : cart.getProformaId(),
                    cart == null ? null : cart.getCustomerDocumentType(),
                    cart == null ? null : cart.getCustomerDocumentNumber(),
                    cart == null ? null : cart.getCustomerName(),
                    cartItems.stream().map(CartItemSnapshot::from).toList()
            ));
        }

        List<MessageSnapshot> outbound = messageRepository
                .findByConversationAfterId(conversation.getId(), lastMessageIdBeforeSimulation, 200)
                .stream()
                .filter(message -> message.getDirection() == WhatsAppEnums.MessageDirection.OUTBOUND)
                .map(MessageSnapshot::from)
                .toList();

        WhatsAppCart openCart = cartRepository.findOpenByConversationId(conversation.getId()).orElse(null);
        List<CartItemSnapshot> finalItems = openCart == null
                ? List.of()
                : cartRepository.findItems(openCart.getId()).stream().map(CartItemSnapshot::from).toList();

        return ResponseEntity.ok(new SimulationResponse(
                conversation.getId(),
                conversation.getWaId(),
                conversation.getConversationState(),
                conversation.getStatus(),
                conversation.getLastProformaId(),
                openCart == null ? null : openCart.getId(),
                openCart == null ? null : openCart.getProformaId(),
                finalItems,
                steps,
                outbound
        ));
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    public record SimulationRequest(
            String waId,
            String profileName,
            Boolean resetBefore,
            List<String> messages
    ) {}

    public record SimulationResponse(
            Long conversationId,
            String waId,
            WhatsAppEnums.ConversationState finalState,
            WhatsAppEnums.ConversationStatus finalStatus,
            Long lastProformaId,
            Long openCartId,
            Long openCartProformaId,
            List<CartItemSnapshot> openCartItems,
            List<StepSnapshot> steps,
            List<MessageSnapshot> outboundMessages
    ) {}

    public record StepSnapshot(
            String inboundText,
            WhatsAppEnums.ConversationState stateBefore,
            WhatsAppEnums.ConversationState stateAfter,
            Long proformaIdBefore,
            Long proformaIdAfter,
            Long openCartId,
            Long openCartProformaId,
            String cartCustomerDocumentType,
            String cartCustomerDocumentNumber,
            String cartCustomerName,
            List<CartItemSnapshot> cartItems
    ) {}

    public record CartItemSnapshot(
            Long id,
            Long productId,
            String productCode,
            String productName,
            String quantity,
            String unitPrice,
            String priceList
    ) {
        static CartItemSnapshot from(WhatsAppCartItem item) {
            return new CartItemSnapshot(
                    item.getId(),
                    item.getProductId(),
                    item.getProductCode(),
                    item.getProductName(),
                    item.getQuantity() == null ? null : item.getQuantity().stripTrailingZeros().toPlainString(),
                    item.getUnitPrice() == null ? null : item.getUnitPrice().stripTrailingZeros().toPlainString(),
                    item.getPriceList()
            );
        }
    }

    public record MessageSnapshot(
            Long id,
            WhatsAppEnums.MessageType type,
            WhatsAppEnums.MessageStatus status,
            String textBody,
            OffsetDateTime messageAt
    ) {
        static MessageSnapshot from(WhatsAppMessage message) {
            return new MessageSnapshot(
                    message.getId(),
                    message.getType(),
                    message.getStatus(),
                    message.getTextBody(),
                    message.getMessageAt()
            );
        }
    }
}
