package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.model.WhatsAppIncomingCommand;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.*;
import com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output.MetaWhatsAppMessageGateway.InteractiveButton;
import com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output.MetaWhatsAppMessageGateway.InteractiveListRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboundWhatsAppAutomationService {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat STOCK_FORMAT = new DecimalFormat("#,##0.###");

    private final WhatsAppIntegrationProperties properties;
    private final SendWhatsAppMessageService sendMessageService;
    private final SearchWhatsAppProductsService searchProductsService;
    private final WhatsAppConversationRepository conversationRepository;
    private final WhatsAppProductSuggestionRepository suggestionRepository;
    private final WhatsAppCartRepository cartRepository;

    public void handleIncomingText(WhatsAppConversation conversation, String text) {
        handleIncomingCommand(conversation, WhatsAppIncomingCommand.fromText(text));
    }

    public void handleIncomingCommand(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        if (!properties.isAutoReplyEnabled() || cmd == null) return;

        try {
            if (cmd.isCancel()) {
                resetFlow(conversation, "Listo, cancelé el flujo actual. Podemos empezar nuevamente.");
                return;
            }

            if (cmd.isGreetingOrReset()) {
                resetConversationOnly(conversation);
                sendMainMenu(conversation.getId(), "Empecemos desde cero 👋 ¿Qué deseas hacer?");
                return;
            }

            if (cmd.isAdvisor()) {
                conversationRepository.updateStatus(conversation.getId(), WhatsAppEnums.ConversationStatus.PENDING_HUMAN);
                sendTextQuietly(conversation.getId(), "Te derivaré con un asesor. Tu conversación quedó registrada para seguimiento.");
                return;
            }

            WhatsAppEnums.ConversationState state = conversation.getConversationState() == null
                    ? WhatsAppEnums.ConversationState.IDLE
                    : conversation.getConversationState();

            switch (state) {
                case WAITING_PRODUCT_QUERY -> handleProductQuery(conversation, cmd);
                case WAITING_PRODUCT_SELECTION -> handleProductSelection(conversation, cmd);
                case WAITING_QUANTITY -> handleQuantity(conversation, cmd);
                case WAITING_ADD_MORE -> handleAddMore(conversation, cmd);
                case WAITING_CUSTOMER_DOCUMENT -> handleCustomerDocument(conversation, cmd);
                case WAITING_CUSTOMER_NAME -> handleCustomerName(conversation, cmd);
                case GENERATING_PROFORMA, PROFORMA_CREATED -> handlePostProforma(conversation, cmd);
                default -> handleIdle(conversation, cmd);
            }
        } catch (Exception exception) {
            log.error("No se pudo procesar automatización WhatsApp. conversationId={}, error={}", conversation.getId(), exception.getMessage(), exception);
            sendTextQuietly(conversation.getId(), "Tu mensaje fue recibido ✅. Un asesor validará la información para continuar.");
        }
    }

    private void handleIdle(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        if (cmd.isSearchMenu()) {
            askForProduct(conversation, "Perfecto. Escribe el nombre o código del producto. Ejemplo: foco led, casco, cadena 428.");
            return;
        }

        if (cmd.isProformaMenu()) {
            handleProformaRequest(conversation);
            return;
        }

        if (cmd.isThanks()) {
            sendMainMenu(conversation.getId(), "Con gusto. ¿Deseas buscar productos, generar una proforma o hablar con un asesor?");
            return;
        }

        if (cmd.hasProductSearchText()) {
            searchAndOfferProducts(conversation, cmd.businessText());
            return;
        }

        sendMainMenu(conversation.getId(), "No entendí bien tu mensaje. Puedes elegir una opción o escribir el producto que buscas.");
    }

    private void handleProductQuery(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        if (cmd.isSearchMenu()) {
            askForProduct(conversation, "Escribe el producto que buscas. Ejemplo: foco led, casco, cadena 428.");
            return;
        }
        if (cmd.isProformaMenu()) {
            handleProformaRequest(conversation);
            return;
        }
        if (cmd.hasProductSearchText()) {
            searchAndOfferProducts(conversation, cmd.businessText());
            return;
        }
        sendTextQuietly(conversation.getId(), "Escribe el nombre o código del producto. Ejemplo: casco, foco, cadena.");
    }

    private void handleProformaRequest(WhatsAppConversation conversation) {
        WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversation.getId());
        List<WhatsAppCartItem> items = cartRepository.findItems(cart.getId());
        if (items.isEmpty()) {
            askForProduct(conversation, "Para generar una proforma primero debemos agregar productos. Escribe el producto que buscas.");
            return;
        }
        askForCustomerDocument(conversation, cart);
    }

    private void askForProduct(WhatsAppConversation conversation, String message) {
        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_PRODUCT_QUERY);
        sendTextQuietly(conversation.getId(), message);
    }

    private void searchAndOfferProducts(WhatsAppConversation conversation, String text) {
        String query = text == null ? "" : text.trim();
        if (query.length() < 2) {
            askForProduct(conversation, "Escribe una palabra un poco más específica. Ejemplo: foco, casco, cadena.");
            return;
        }

        List<WhatsAppProductSearchResult> products = searchProductsService.searchForBot(query);

        if (products.isEmpty()) {
            Optional<WhatsAppProductSearchResult> exact = searchProductsService.findExactForBot(query);
            if (exact.isPresent() && safeStock(exact.get()).compareTo(BigDecimal.ONE) < 0) {
                conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_PRODUCT_QUERY);
                sendButtonsQuietly(conversation.getId(),
                        "Encontré el producto " + exact.get().getSku() + " - " + shortLine(exact.get().getName(), 80) + ", pero no tiene stock disponible.\n¿Quieres que busque alternativas similares?",
                        List.of(
                                new InteractiveButton("MENU_SEARCH", "Buscar similar"),
                                new InteractiveButton("MENU_ADVISOR", "Asesor"),
                                new InteractiveButton("CANCEL", "Cancelar")
                        ));
                return;
            }
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_PRODUCT_QUERY);
            sendTextQuietly(conversation.getId(), "No encontré productos disponibles con: " + query + ".\nPrueba con una palabra más corta, código, marca o modelo. Ejemplo: foco, casco, cadena.");
            return;
        }

        int max = Math.min(products.size(), properties.getSales().safeMaxProductsResponse());
        List<WhatsAppProductSearchResult> limited = products.stream().limit(max).toList();
        suggestionRepository.replaceSuggestions(conversation.getId(), limited, query);
        cartRepository.findOrCreateOpenCart(conversation.getId());

        if (limited.size() == 1) {
            selectSuggestionByPosition(conversation, 1);
            WhatsAppProductSearchResult only = limited.get(0);
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_QUANTITY);
            sendTextQuietly(conversation.getId(), "Encontré este producto disponible:\n" + productLine(1, only) + "\n\n¿Cuántas unidades deseas? Ejemplo: 2");
            return;
        }

        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_PRODUCT_SELECTION);
        AtomicInteger counter = new AtomicInteger(1);
        List<InteractiveListRow> rows = limited.stream()
                .map(product -> {
                    int pos = counter.getAndIncrement();
                    return new InteractiveListRow(
                            "PRODUCT_" + pos,
                            shortTitle(pos + ". " + nullSafe(product.getSku())),
                            shortDescription(product.getName() + " | S/ " + formatMoney(product.getSelectedPrice()) + " | Stock " + formatStock(product.getStockQuantity()))
                    );
                })
                .toList();

        sendListQuietly(conversation.getId(),
                "Encontré " + limited.size() + " productos disponibles con precio " + properties.getSales().normalizedDefaultPriceList() + ".\nToca 'Ver productos' y selecciona uno, o responde con el número. Ejemplo: 1",
                "Ver productos",
                "Productos disponibles",
                rows);
    }

    private void handleProductSelection(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        if (cmd.isSearchMenu()) {
            askForProduct(conversation, "Escribe el producto que quieres buscar.");
            return;
        }
        if (cmd.isProformaMenu()) {
            handleProformaRequest(conversation);
            return;
        }
        if (cmd.isProductSelection()) {
            Integer position = cmd.numericSelection();
            if (position == null) {
                sendTextQuietly(conversation.getId(), "Selecciona un producto de la lista o responde con el número. Ejemplo: 1");
                return;
            }
            Optional<WhatsAppProductSuggestion> suggestionOpt = selectSuggestionByPosition(conversation, position);
            if (suggestionOpt.isEmpty()) {
                sendTextQuietly(conversation.getId(), "No encontré esa opción. Responde con un número válido de la lista.");
                return;
            }
            WhatsAppProductSuggestion suggestion = suggestionOpt.get();
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_QUANTITY);
            sendTextQuietly(conversation.getId(),
                    "Seleccionaste: " + suggestion.getProductName() + "\n" +
                            "Precio: S/ " + formatMoney(suggestion.getUnitPrice()) + " | Stock: " + formatStock(suggestion.getStockQuantity()) + "\n\n" +
                            "¿Cuántas unidades deseas? Ejemplo: 2");
            return;
        }
        if (cmd.hasProductSearchText()) {
            searchAndOfferProducts(conversation, cmd.businessText());
            return;
        }
        sendTextQuietly(conversation.getId(), "Selecciona un producto de la lista o responde con el número. Ejemplo: 1");
    }

    private Optional<WhatsAppProductSuggestion> selectSuggestionByPosition(WhatsAppConversation conversation, int position) {
        Optional<WhatsAppProductSuggestion> suggestionOpt = suggestionRepository.findLatestByPosition(conversation.getId(), position);
        suggestionOpt.ifPresent(suggestion -> {
            WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversation.getId());
            cartRepository.setCurrentProductSuggestion(cart.getId(), suggestion.getId());
        });
        return suggestionOpt;
    }

    private void handleQuantity(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        BigDecimal quantity = parseQuantity(cmd.businessText());
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            sendTextQuietly(conversation.getId(), "Cantidad inválida. Escribe solo el número. Ejemplo: 2");
            return;
        }

        WhatsAppCart cart = cartRepository.findOpenByConversationId(conversation.getId())
                .orElseGet(() -> cartRepository.findOrCreateOpenCart(conversation.getId()));

        if (cart.getCurrentProductSuggestionId() == null) {
            askForProduct(conversation, "No tengo un producto seleccionado. Escribe nuevamente el producto que buscas.");
            return;
        }

        WhatsAppProductSuggestion suggestion = suggestionRepository.findById(cart.getCurrentProductSuggestionId())
                .orElseThrow(() -> new IllegalStateException("Sugerencia de producto no encontrada."));

        if (suggestion.getStockQuantity() != null && quantity.compareTo(suggestion.getStockQuantity()) > 0) {
            sendTextQuietly(conversation.getId(), "Solo tenemos " + formatStock(suggestion.getStockQuantity()) + " unidades disponibles. Escribe una cantidad menor o igual.");
            return;
        }

        cartRepository.addItem(WhatsAppCartItem.builder()
                .cartId(cart.getId())
                .productId(suggestion.getProductId())
                .productCode(suggestion.getProductCode())
                .productName(suggestion.getProductName())
                .quantity(quantity)
                .unitPrice(suggestion.getUnitPrice())
                .priceList(suggestion.getPriceList())
                .build());

        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_ADD_MORE);
        sendButtonsQuietly(conversation.getId(),
                "Agregué " + formatStock(quantity) + " x " + suggestion.getProductName() + ".\n\n¿Qué deseas hacer ahora?",
                List.of(
                        new InteractiveButton("ADD_MORE_YES", "Agregar otro"),
                        new InteractiveButton("ADD_MORE_NO", "Generar proforma"),
                        new InteractiveButton("CANCEL", "Cancelar")
                ));
    }

    private void handleAddMore(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        if (cmd.isYes() || cmd.isSearchMenu()) {
            askForProduct(conversation, "Perfecto. Escribe el nombre o código del siguiente producto. Ejemplo: casco, foco led, repuesto.");
            return;
        }
        if (cmd.isNo() || cmd.isProformaMenu()) {
            WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversation.getId());
            askForCustomerDocument(conversation, cart);
            return;
        }
        if (cmd.hasProductSearchText()) {
            searchAndOfferProducts(conversation, cmd.businessText());
            return;
        }
        sendButtonsQuietly(conversation.getId(),
                "Puedes agregar otro producto, generar la proforma o escribir directamente otro producto.",
                List.of(
                        new InteractiveButton("ADD_MORE_YES", "Agregar otro"),
                        new InteractiveButton("ADD_MORE_NO", "Generar proforma"),
                        new InteractiveButton("CANCEL", "Cancelar")
                ));
    }

    private void askForCustomerDocument(WhatsAppConversation conversation, WhatsAppCart cart) {
        List<WhatsAppCartItem> items = cartRepository.findItems(cart.getId());
        if (items.isEmpty()) {
            askForProduct(conversation, "Aún no tienes productos en el pedido. Escribe el producto que deseas cotizar.");
            return;
        }
        cartRepository.updateStatus(cart.getId(), WhatsAppEnums.CartStatus.WAITING_CUSTOMER);
        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_CUSTOMER_DOCUMENT);
        sendButtonsQuietly(conversation.getId(),
                buildCartSummary(cart.getId()) + "\n\nPara generar la proforma, elige el tipo de documento o envía directamente tu DNI/RUC.",
                List.of(
                        new InteractiveButton("DOC_DNI", "DNI"),
                        new InteractiveButton("DOC_RUC", "RUC"),
                        new InteractiveButton("CANCEL", "Cancelar")
                ));
    }

    private void handleCustomerDocument(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        if (cmd.isDniOption()) {
            sendTextQuietly(conversation.getId(), "Escribe el número de DNI. Ejemplo: 12345678");
            return;
        }
        if (cmd.isRucOption()) {
            sendTextQuietly(conversation.getId(), "Escribe el número de RUC. Ejemplo: 20611603739");
            return;
        }

        String documentNumber = cmd.businessText().replaceAll("\\D", "");
        if (!(documentNumber.length() == 8 || documentNumber.length() == 11)) {
            sendTextQuietly(conversation.getId(), "Documento inválido. Envíame DNI de 8 dígitos o RUC de 11 dígitos.");
            return;
        }

        String documentType = documentNumber.length() == 11 ? "RUC" : "DNI";
        WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversation.getId());
        cartRepository.setCustomerDocument(cart.getId(), documentType, documentNumber);
        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_CUSTOMER_NAME);

        sendButtonsQuietly(conversation.getId(),
                "Documento registrado: " + documentType + " " + documentNumber + ".\nAhora envía el nombre del cliente para la proforma. Si deseas, puedes omitirlo.",
                List.of(
                        new InteractiveButton("SKIP_NAME", "Omitir"),
                        new InteractiveButton("CANCEL", "Cancelar")
                ));
    }

    private void handleCustomerName(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversation.getId());
        String customerName = cmd.isSkipName() ? null : sanitizeCustomerName(cmd.businessText());
        cartRepository.setCustomerName(cart.getId(), customerName);
        cartRepository.updateStatus(cart.getId(), WhatsAppEnums.CartStatus.READY_TO_PROFORMA);
        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.PROFORMA_CREATED);

        sendTextQuietly(conversation.getId(),
                "Listo ✅ Ya tengo la información para la proforma.\n" +
                        "Origen: WhatsApp " + conversation.getWaId() + " (" + nullSafe(conversation.getProfileName()) + ")\n" +
                        "Documento: " + nullSafe(cart.getCustomerDocumentType()) + " " + nullSafe(cart.getCustomerDocumentNumber()) + "\n" +
                        "Cliente: " + (customerName == null ? "No indicado" : customerName) + "\n\n" +
                        buildCartSummary(cart.getId()) + "\n\nUn asesor validará la proforma final y te enviará el documento.");
    }

    private void handlePostProforma(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        if (cmd.isSearchMenu() || cmd.hasProductSearchText()) {
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.IDLE);
            if (cmd.hasProductSearchText()) searchAndOfferProducts(conversation, cmd.businessText());
            else askForProduct(conversation, "Escribe el producto que deseas buscar.");
            return;
        }
        if (cmd.isAdvisor()) {
            conversationRepository.updateStatus(conversation.getId(), WhatsAppEnums.ConversationStatus.PENDING_HUMAN);
            sendTextQuietly(conversation.getId(), "Te derivaré con un asesor.");
            return;
        }
        sendMainMenu(conversation.getId(), "¿Deseas buscar otro producto, generar una nueva proforma o hablar con un asesor?");
    }

    private void resetFlow(WhatsAppConversation conversation, String message) {
        cartRepository.cancelOpenCarts(conversation.getId());
        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.IDLE);
        sendMainMenu(conversation.getId(), message);
    }

    private void resetConversationOnly(WhatsAppConversation conversation) {
        cartRepository.cancelOpenCarts(conversation.getId());
        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.IDLE);
    }

    private void sendMainMenu(Long conversationId, String body) {
        sendButtonsQuietly(conversationId, body,
                List.of(
                        new InteractiveButton("MENU_SEARCH", "Buscar producto"),
                        new InteractiveButton("MENU_PROFORMA", "Proforma"),
                        new InteractiveButton("MENU_ADVISOR", "Asesor")
                ));
    }

    private String buildCartSummary(Long cartId) {
        List<WhatsAppCartItem> items = cartRepository.findItems(cartId);
        StringBuilder builder = new StringBuilder("Resumen del pedido:");
        BigDecimal total = BigDecimal.ZERO;
        int index = 1;
        for (WhatsAppCartItem item : items) {
            BigDecimal lineTotal = item.getUnitPrice().multiply(item.getQuantity());
            total = total.add(lineTotal);
            builder.append("\n").append(index++).append(") ")
                    .append(shortLine(item.getProductName(), 60))
                    .append(" x ").append(formatStock(item.getQuantity()))
                    .append(" = S/ ").append(formatMoney(lineTotal));
        }
        builder.append("\nTotal estimado: S/ ").append(formatMoney(total));
        return builder.toString();
    }

    private String productLine(int index, WhatsAppProductSearchResult product) {
        return index + ") " + nullSafe(product.getSku()) + " - " + product.getName()
                + "\nPrecio: S/ " + formatMoney(product.getSelectedPrice())
                + " | Stock: " + formatStock(product.getStockQuantity());
    }

    private BigDecimal parseQuantity(String value) {
        if (value == null) return null;
        String normalized = value.trim().replace(",", ".").replaceAll("[^0-9.]", "");
        if (normalized.isBlank()) return null;
        try { return new BigDecimal(normalized); } catch (Exception ignored) { return null; }
    }

    private BigDecimal safeStock(WhatsAppProductSearchResult product) {
        return product == null || product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
    }

    private void sendTextQuietly(Long conversationId, String body) {
        try {
            sendMessageService.sendTextToConversation(conversationId, body);
        } catch (Exception e) {
            log.error("No se pudo enviar texto WhatsApp. conversationId={}, error={}", conversationId, e.getMessage(), e);
        }
    }

    private void sendButtonsQuietly(Long conversationId, String body, List<InteractiveButton> buttons) {
        try {
            sendMessageService.sendButtonsToConversation(conversationId, body, buttons);
        } catch (Exception e) {
            log.error("No se pudo enviar botones WhatsApp. conversationId={}, error={}", conversationId, e.getMessage(), e);
        }
    }

    private void sendListQuietly(Long conversationId, String body, String buttonText, String sectionTitle, List<InteractiveListRow> rows) {
        try {
            sendMessageService.sendListToConversation(conversationId, body, buttonText, sectionTitle, rows);
        } catch (Exception e) {
            log.error("No se pudo enviar lista WhatsApp. conversationId={}, error={}", conversationId, e.getMessage(), e);
        }
    }

    private String sanitizeCustomerName(String value) {
        if (value == null) return null;
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.isBlank()) return null;
        return trimmed.length() > 250 ? trimmed.substring(0, 250) : trimmed;
    }

    private String shortTitle(String value) {
        if (value == null || value.isBlank()) return "Producto";
        return value.length() > 24 ? value.substring(0, 24) : value;
    }

    private String shortDescription(String value) {
        if (value == null) return "";
        return value.length() > 72 ? value.substring(0, 72) : value;
    }

    private String shortLine(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "..." : value;
    }

    private String nullSafe(String value) { return value == null ? "" : value; }
    private String formatMoney(BigDecimal value) { return value == null ? "0.00" : MONEY_FORMAT.format(value); }
    private String formatStock(BigDecimal value) { return value == null ? "0" : STOCK_FORMAT.format(value); }
}
