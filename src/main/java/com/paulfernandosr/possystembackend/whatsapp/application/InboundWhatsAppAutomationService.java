package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.customer.domain.port.input.ResolveCustomerUseCase;
import com.paulfernandosr.possystembackend.whatsapp.application.aiagent.AiAgentAction;
import com.paulfernandosr.possystembackend.whatsapp.application.aiagent.AiAgentAdapterService;
import com.paulfernandosr.possystembackend.whatsapp.application.aiagent.AiAgentPlanResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.CreateProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType;
import com.paulfernandosr.possystembackend.salev2.domain.model.TaxStatus;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
    private final WhatsAppBatchCodeExtractorService batchCodeExtractorService;
    private final WhatsAppBatchQuoteService batchQuoteService;
    private final WhatsAppMediaCodeExtractionService mediaCodeExtractionService;
    private final AiAgentAdapterService aiAgentAdapterService;
    private final WhatsAppProformaCreationService proformaCreationService;
    private final ResolveCustomerUseCase resolveCustomerUseCase;
    private final UserRepository userRepository;

    public void handleIncomingText(WhatsAppConversation conversation, String text) {
        handleIncomingCommand(conversation, WhatsAppIncomingCommand.fromText(text));
    }

    public void handleIncomingMedia(WhatsAppConversation conversation, WhatsAppEnums.MessageType type, String mediaId, String mimeType, String caption) {
        if (!properties.isAutoReplyEnabled()) return;
        try {
            if (caption != null && batchCodeExtractorService.looksLikeBatchCodeList(caption)) {
                handleBatchCodeList(conversation, batchCodeExtractorService.extractCodes(caption), "caption");
                return;
            }

            sendTextQuietly(conversation.getId(), "Recibí tu " + mediaLabel(type) + " ✅. Estoy intentando leer los códigos para cotizar.");
            WhatsAppMediaCodeExtractionService.MediaExtractionResult extraction = mediaCodeExtractionService.extractCodesFromMedia(mediaId, mimeType);
            if (!extraction.enabled()) {
                sendTextQuietly(conversation.getId(), extraction.message() + "\nPor ahora puedes enviarme los códigos en texto, uno por línea. Ejemplo:\nIMBA0826\n1300115\nIMBA0386");
                return;
            }
            if (extraction.codes().isEmpty()) {
                sendTextQuietly(conversation.getId(), "No pude reconocer códigos claros en la imagen. Por favor envía una foto más nítida o copia los códigos en texto, uno por línea.");
                return;
            }
            handleBatchCodeList(conversation, extraction.codes(), "imagen");
        } catch (Exception exception) {
            log.error("No se pudo procesar media WhatsApp. conversationId={}, mediaId={}, error={}", conversation.getId(), mediaId, exception.getMessage(), exception);
            sendTextQuietly(conversation.getId(), "Recibí tu archivo, pero no pude leerlo automáticamente. Un asesor lo validará o puedes enviar los códigos en texto.");
        }
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

            if (shouldProcessBatchList(state, cmd)) {
                if (isPostProformaState(state)) {
                    prepareCurrentProformaAmendment(conversation);
                }
                handleBatchCodeList(conversation, batchCodeExtractorService.extractCodes(cmd.businessText()), "mensaje");
                return;
            }

            switch (state) {
                case WAITING_PRODUCT_QUERY -> handleProductQuery(conversation, cmd);
                case WAITING_PRODUCT_SELECTION -> handleProductSelection(conversation, cmd);
                case WAITING_QUANTITY -> handleQuantity(conversation, cmd);
                case WAITING_ADD_MORE -> handleAddMore(conversation, cmd);
                case WAITING_BATCH_CONFIRM -> handleBatchConfirm(conversation, cmd);
                case WAITING_BATCH_QUANTITY -> handleBatchQuantity(conversation, cmd);
                case WAITING_BATCH_CUSTOM_QUANTITY -> handleBatchCustomQuantity(conversation, cmd);
                case WAITING_CUSTOMER_DOCUMENT -> handleCustomerDocument(conversation, cmd);
                case WAITING_DNI_NUMBER -> handleDniNumber(conversation, cmd);
                case WAITING_RUC_NUMBER -> handleRucNumber(conversation, cmd);
                case WAITING_CUSTOMER_NAME -> handleCustomerName(conversation, cmd);
                case GENERATING_PROFORMA, PROFORMA_CREATED -> handlePostProforma(conversation, cmd);
                default -> handleIdle(conversation, cmd);
            }
        } catch (Exception exception) {
            log.error("No se pudo procesar automatización WhatsApp. conversationId={}, error={}", conversation.getId(), exception.getMessage(), exception);
            sendTextQuietly(conversation.getId(), "Tu mensaje fue recibido ✅. Un asesor validará la información para continuar.");
        }
    }

    private boolean shouldProcessBatchList(WhatsAppEnums.ConversationState state, WhatsAppIncomingCommand cmd) {
        if (cmd == null || !properties.getSales().isBatchListEnabled()) return false;
        if (state == WhatsAppEnums.ConversationState.WAITING_QUANTITY
                || state == WhatsAppEnums.ConversationState.WAITING_CUSTOMER_DOCUMENT
                || state == WhatsAppEnums.ConversationState.WAITING_DNI_NUMBER
                || state == WhatsAppEnums.ConversationState.WAITING_RUC_NUMBER
                || state == WhatsAppEnums.ConversationState.WAITING_CUSTOMER_NAME
                || state == WhatsAppEnums.ConversationState.WAITING_PRODUCT_SELECTION
                || state == WhatsAppEnums.ConversationState.WAITING_BATCH_QUANTITY
                || state == WhatsAppEnums.ConversationState.WAITING_BATCH_CUSTOM_QUANTITY) {
            return false;
        }
        if (cmd.isGreetingOrReset() || cmd.isCancel() || cmd.isAdvisor() || cmd.isSearchMenu() || cmd.isProformaMenu()) {
            return false;
        }
        return batchCodeExtractorService.looksLikeBatchCodeList(cmd.businessText());
    }

    private void handleBatchCodeList(WhatsAppConversation conversation, List<WhatsAppBatchCodeLine> codes, String source) {
        if (codes == null || codes.isEmpty()) {
            askForProduct(conversation, "No encontré códigos claros. Puedes enviarlos uno por línea. Ejemplo:\nIMBA0826\n1300115\nIMBA0386");
            return;
        }

        WhatsAppBatchQuoteResult result = batchQuoteService.previewCodes(conversation.getId(), codes, source);
        if (result.hasAddedItems()) {
            sendTextQuietly(conversation.getId(), buildBatchPreviewMessage(result, source));
            startBatchQuantityFlow(conversation);
            return;
        }

        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_PRODUCT_QUERY);
        sendButtonsQuietly(conversation.getId(), buildBatchPreviewMessage(result, source) + "\n\nNo hay productos disponibles para cotizar. Puedes enviar otra lista o pedir ayuda a un asesor.", List.of(
                new InteractiveButton("MENU_SEARCH", "Buscar otro"),
                new InteractiveButton("MENU_ADVISOR", "Asesor"),
                new InteractiveButton("CANCEL", "Cancelar")
        ));
    }

    private void handleBatchConfirm(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        // Compatibilidad: si el usuario toca un botón antiguo de confirmar, iniciamos el asistente de cantidades.
        if (cmd.isConfirmBatch()) {
            startBatchQuantityFlow(conversation);
            return;
        }

        if (cmd.isEditBatchQuantities()) {
            startBatchQuantityFlow(conversation);
            return;
        }

        if (cmd.isProformaMenu() || cmd.isNo()) {
            WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversation.getId());
            List<WhatsAppCartItem> items = cartRepository.findItems(cart.getId());
            if (items.isEmpty()) {
                startBatchQuantityFlow(conversation);
                return;
            }
            if (cart.getProformaId() != null) {
                finalizeProformaInfo(conversation, cart, null);
            } else {
                askForCustomerDocument(conversation, cart);
            }
            return;
        }

        if (cmd.isSearchMenu()) {
            askForProduct(conversation, "Escribe el producto que quieres agregar o envía otra lista de códigos.");
            return;
        }

        if (cmd.hasProductSearchText()) {
            if (batchCodeExtractorService.looksLikeBatchCodeList(cmd.businessText())) {
                handleBatchCodeList(conversation, batchCodeExtractorService.extractCodes(cmd.businessText()), "mensaje");
            } else {
                searchAndOfferProducts(conversation, cmd.businessText());
            }
            return;
        }

        sendButtonsQuietly(conversation.getId(),
                "Ahora definiremos cantidades. Por cada producto elige una cantidad. Si no deseas uno, elige 0 / No agregar.",
                List.of(
                        new InteractiveButton("BATCH_CONFIRM", "Definir cant."),
                        new InteractiveButton("MENU_ADVISOR", "Asesor"),
                        new InteractiveButton("CANCEL", "Cancelar")
                ));
    }

    private void startBatchQuantityFlow(WhatsAppConversation conversation) {
        WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversation.getId());
        List<WhatsAppProductSuggestion> suggestions = suggestionRepository.findLatest(conversation.getId(), Math.max(1, properties.getSales().getMaxBatchCodesPerMessage()));
        if (suggestions.isEmpty()) {
            askForProduct(conversation, "No encontré productos disponibles para definir cantidades. Envíame otra lista o escribe el producto que buscas.");
            return;
        }
        cartRepository.setCurrentProductSuggestion(cart.getId(), suggestions.get(0).getId());
        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_BATCH_QUANTITY);
        askCurrentBatchQuantity(conversation, cart, suggestions.get(0), 1, suggestions.size());
    }

    private void handleBatchQuantity(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        if (cmd.isCancel()) {
            resetFlow(conversation, "Listo, cancelé la definición de cantidades.");
            return;
        }
        if (cmd.isAdvisor()) {
            conversationRepository.updateStatus(conversation.getId(), WhatsAppEnums.ConversationStatus.PENDING_HUMAN);
            sendTextQuietly(conversation.getId(), "Te derivaré con un asesor para validar las cantidades.");
            return;
        }
        if (cmd.isBatchQuantityOther()) {
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_BATCH_CUSTOM_QUANTITY);
            sendTextQuietly(conversation.getId(), "Escribe la cantidad para este producto. Si no lo quieres, responde 0.");
            return;
        }

        BigDecimal quantity = cmd.isBatchQuantitySelection() ? cmd.batchQuantityValue() : parseQuantity(cmd.businessText());
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            sendTextQuietly(conversation.getId(), "Elige una cantidad de la lista o escribe un número. Si no deseas este producto, responde 0.");
            return;
        }
        resolveCurrentBatchQuantity(conversation, quantity);
    }

    private void handleBatchCustomQuantity(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        BigDecimal quantity = parseQuantity(cmd.businessText());
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            sendTextQuietly(conversation.getId(), "Cantidad inválida. Escribe solo número. Ejemplo: 2. Si no lo quieres, responde 0.");
            return;
        }
        resolveCurrentBatchQuantity(conversation, quantity);
    }

    private void resolveCurrentBatchQuantity(WhatsAppConversation conversation, BigDecimal quantity) {
        WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversation.getId());
        Long currentId = cart.getCurrentProductSuggestionId();
        if (currentId == null) {
            startBatchQuantityFlow(conversation);
            return;
        }

        Optional<WhatsAppProductSuggestion> currentOpt = suggestionRepository.findById(currentId);
        if (currentOpt.isEmpty()) {
            startBatchQuantityFlow(conversation);
            return;
        }
        WhatsAppProductSuggestion suggestion = currentOpt.get();
        BigDecimal stock = suggestion.getStockQuantity() == null ? BigDecimal.ZERO : suggestion.getStockQuantity();
        if (quantity.compareTo(stock) > 0) {
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_BATCH_QUANTITY);
            sendTextQuietly(conversation.getId(), "La cantidad solicitada no está disponible para " + nullSafe(suggestion.getProductCode()) + ". Elige una cantidad menor o responde 0 para omitir.");
            askCurrentBatchQuantity(conversation, cart, suggestion, batchPosition(conversation, suggestion), suggestionRepository.findLatest(conversation.getId(), Math.max(1, properties.getSales().getMaxBatchCodesPerMessage())).size());
            return;
        }

        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            cartRepository.addItem(WhatsAppCartItem.builder()
                    .cartId(cart.getId())
                    .productId(suggestion.getProductId())
                    .productCode(suggestion.getProductCode())
                    .productName(suggestion.getProductName())
                    .quantity(quantity)
                    .unitPrice(suggestion.getUnitPrice())
                    .priceList(suggestion.getPriceList())
                    .build());
        }

        List<WhatsAppProductSuggestion> suggestions = suggestionRepository.findLatest(conversation.getId(), Math.max(1, properties.getSales().getMaxBatchCodesPerMessage()));
        int index = 0;
        for (int i = 0; i < suggestions.size(); i++) {
            if (suggestions.get(i).getId().equals(currentId)) {
                index = i;
                break;
            }
        }
        int next = index + 1;
        if (next < suggestions.size()) {
            WhatsAppProductSuggestion nextSuggestion = suggestions.get(next);
            cartRepository.setCurrentProductSuggestion(cart.getId(), nextSuggestion.getId());
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_BATCH_QUANTITY);
            askCurrentBatchQuantity(conversation, cart, nextSuggestion, next + 1, suggestions.size());
            return;
        }

        cartRepository.setCurrentProductSuggestion(cart.getId(), null);
        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_ADD_MORE);
        List<WhatsAppCartItem> items = cartRepository.findItems(cart.getId());
        if (items.isEmpty()) {
            sendButtonsQuietly(conversation.getId(),
                    "No agregaste productos de esta lista. Puedes buscar otro producto o pedir ayuda a un asesor.",
                    List.of(
                            new InteractiveButton("MENU_SEARCH", "Buscar producto"),
                            new InteractiveButton("MENU_ADVISOR", "Asesor"),
                            new InteractiveButton("CANCEL", "Cancelar")
                    ));
            return;
        }
        sendButtonsQuietly(conversation.getId(),
                buildCartSummary(cart.getId()) + "\n\n¿Qué deseas hacer ahora?",
                List.of(
                        new InteractiveButton("ADD_MORE_YES", "Agregar otro"),
                        new InteractiveButton("ADD_MORE_NO", cart.getProformaId() == null ? "Generar proforma" : "Actualizar"),
                        new InteractiveButton("MENU_ADVISOR", "Asesor")
                ));
    }

    private int batchPosition(WhatsAppConversation conversation, WhatsAppProductSuggestion suggestion) {
        List<WhatsAppProductSuggestion> suggestions = suggestionRepository.findLatest(conversation.getId(), Math.max(1, properties.getSales().getMaxBatchCodesPerMessage()));
        for (int i = 0; i < suggestions.size(); i++) {
            if (suggestions.get(i).getId().equals(suggestion.getId())) return i + 1;
        }
        return 1;
    }

    private void askCurrentBatchQuantity(WhatsAppConversation conversation, WhatsAppCart cart, WhatsAppProductSuggestion suggestion, int position, int total) {
        String body = "Producto " + position + " de " + total + ":\n"
                + nullSafe(suggestion.getProductCode()) + " - " + shortLine(suggestion.getProductName(), 80) + "\n"
                + "Precio: S/ " + formatMoney(suggestion.getUnitPrice()) + "\n\n"
                + "Cantidad: toca 1, 2, escribe otra o elige No agregar.";

        sendProductQuantityPrompt(conversation.getId(), suggestion.getProductId(), suggestion.getMainImageUrl(), body);
    }

    private List<InteractiveListRow> quantityRows(WhatsAppProductSuggestion suggestion) {
        BigDecimal stock = suggestion.getStockQuantity() == null ? BigDecimal.ZERO : suggestion.getStockQuantity();
        List<InteractiveListRow> rows = new ArrayList<>();
        rows.add(new InteractiveListRow("BQTY_0", "0 - No agregar", "Omitir este producto"));
        int[] defaults = {1, 2, 3, 4, 5, 10};
        for (int value : defaults) {
            BigDecimal qty = BigDecimal.valueOf(value);
            if (stock.compareTo(qty) >= 0) {
                rows.add(new InteractiveListRow("BQTY_" + value, value + " unidad" + (value == 1 ? "" : "es"), "Agregar " + value));
            }
        }
        rows.add(new InteractiveListRow("BATCH_QTY_OTHER", "Otra cantidad", "Escribir manualmente"));
        return rows.size() > 10 ? rows.subList(0, 10) : rows;
    }

    private boolean tryHandleWithAiAgent(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        Optional<AiAgentPlanResponse> planOpt = aiAgentAdapterService.plan(conversation, cmd);
        if (planOpt.isEmpty()) return false;

        AiAgentPlanResponse plan = planOpt.get();
        if (plan.isHandoffRequired() || equalsIgnoreCase(plan.getIntent(), "HUMAN_HANDOFF")) {
            conversationRepository.updateStatus(conversation.getId(), WhatsAppEnums.ConversationStatus.PENDING_HUMAN);
            sendTextQuietly(conversation.getId(), firstNonBlank(plan.getReplyDraft(), "Te derivare con un asesor. Tu conversacion quedo registrada para seguimiento."));
            return true;
        }

        for (AiAgentAction action : plan.getActions()) {
            String tool = action.getTool() == null ? "" : action.getTool().trim().toUpperCase();
            switch (tool) {
                case "SEARCH_PRODUCTS" -> {
                    String query = firstNonBlank(stringArg(action, "query"), cmd.businessText());
                    searchAndOfferProducts(conversation, query);
                    return true;
                }
                case "ASK_PRODUCT" -> {
                    askForProduct(conversation, firstNonBlank(plan.getReplyDraft(), "Escribe el producto que deseas buscar. Ejemplo: casco, foco led, cadena."));
                    return true;
                }
                case "REQUEST_HUMAN" -> {
                    conversationRepository.updateStatus(conversation.getId(), WhatsAppEnums.ConversationStatus.PENDING_HUMAN);
                    sendTextQuietly(conversation.getId(), firstNonBlank(plan.getReplyDraft(), "Te derivare con un asesor para ayudarte mejor."));
                    return true;
                }
                case "START_QUOTE", "CAPTURE_CUSTOMER_DATA" -> {
                    handleProformaRequest(conversation);
                    return true;
                }
                case "SHOW_MENU" -> {
                    sendMainMenu(conversation.getId(), firstNonBlank(plan.getReplyDraft(), "Puedes buscar productos, generar una proforma o hablar con un asesor."));
                    return true;
                }
                case "FALLBACK_MENU" -> {
                    return false;
                }
                default -> log.debug("AI Agent devolvio accion no soportada. conversationId={}, tool={}", conversation.getId(), tool);
            }
        }
        return false;
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
            if (tryHandleWithAiAgent(conversation, cmd)) return;
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
            if (tryHandleWithAiAgent(conversation, cmd)) return;
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
        if (cart.getProformaId() != null) {
            finalizeProformaInfo(conversation, cart, null);
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
            sendProductQuantityPrompt(conversation.getId(), only.getProductId(), only.getMainImageUrl(),
                    "Encontré este producto disponible:\n" + productLine(1, only) + "\n\n¿Cuántas unidades deseas? Puedes tocar 1, 2 o escribir otra cantidad.");
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
                            shortDescription(product.getName() + " | S/ " + formatMoney(product.getSelectedPrice()))
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
            sendProductQuantityPrompt(conversation.getId(), suggestion.getProductId(), suggestion.getMainImageUrl(),
                    "Seleccionaste: " + suggestion.getProductName() + "\n" +
                            "Precio: S/ " + formatMoney(suggestion.getUnitPrice()) + "\n\n" +
                            "Cantidad: toca 1, 2, escribe otra o elige No agregar.");
            return;
        }
        if (cmd.hasProductSearchText()) {
            if (tryHandleWithAiAgent(conversation, cmd)) return;
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
        BigDecimal quantity = cmd.isBatchQuantitySelection() ? cmd.batchQuantityValue() : parseQuantity(cmd.businessText());
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            sendTextQuietly(conversation.getId(), "Cantidad inválida. Escribe solo el número. Ejemplo: 2. Si no deseas este producto, responde 0.");
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

        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            cartRepository.setCurrentProductSuggestion(cart.getId(), null);
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_ADD_MORE);

            List<WhatsAppCartItem> items = cartRepository.findItems(cart.getId());
            if (items.isEmpty()) {
                sendButtonsQuietly(conversation.getId(),
                        "Listo, no agregué " + shortLine(suggestion.getProductName(), 70) + ". Puedes buscar otro producto o cancelar.",
                        List.of(
                                new InteractiveButton("MENU_SEARCH", "Buscar producto"),
                                new InteractiveButton("MENU_ADVISOR", "Asesor"),
                                new InteractiveButton("CANCEL", "Cancelar")
                        ));
            } else {
                sendButtonsQuietly(conversation.getId(),
                        "Listo, omití " + shortLine(suggestion.getProductName(), 70) + ".\n\n¿Qué deseas hacer ahora?",
                        List.of(
                                new InteractiveButton("ADD_MORE_YES", "Agregar otro"),
                                new InteractiveButton("ADD_MORE_NO", cart.getProformaId() == null ? "Generar proforma" : "Actualizar"),
                                new InteractiveButton("CANCEL", "Cancelar")
                        ));
            }
            return;
        }

        if (suggestion.getStockQuantity() != null && quantity.compareTo(suggestion.getStockQuantity()) > 0) {
            sendTextQuietly(conversation.getId(), "La cantidad solicitada no está disponible. Escribe una cantidad menor o responde 0 para omitir este producto.");
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
        cartRepository.setCurrentProductSuggestion(cart.getId(), null);

        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_ADD_MORE);
        boolean amendingProforma = cart.getProformaId() != null;
        sendButtonsQuietly(conversation.getId(),
                "Agregué " + formatStock(quantity) + " x " + suggestion.getProductName() + ".\n\n¿Qué deseas hacer ahora?",
                List.of(
                        new InteractiveButton("ADD_MORE_YES", "Agregar otro"),
                        new InteractiveButton("ADD_MORE_NO", amendingProforma ? "Actualizar" : "Generar proforma"),
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
            if (cart.getProformaId() != null) {
                finalizeProformaInfo(conversation, cart, null);
            } else {
                askForCustomerDocument(conversation, cart);
            }
            return;
        }
        if (isRemoveProductIntent(cmd) && conversation.getLastProformaId() != null) {
            handleRemoveProductFromCurrentProforma(conversation, cmd);
            return;
        }
        if (cmd.hasProductSearchText()) {
            if (tryHandleWithAiAgent(conversation, cmd)) return;
            searchAndOfferProducts(conversation, cmd.businessText());
            return;
        }
        boolean amendingProforma = cartRepository.findOpenByConversationId(conversation.getId())
                .map(cart -> cart.getProformaId() != null)
                .orElse(false);
        sendButtonsQuietly(conversation.getId(),
                "Puedes agregar otro producto, generar la proforma o escribir directamente otro producto.",
                List.of(
                        new InteractiveButton("ADD_MORE_YES", "Agregar otro"),
                        new InteractiveButton("ADD_MORE_NO", amendingProforma ? "Actualizar" : "Generar proforma"),
                        new InteractiveButton("CANCEL", "Cancelar")
                ));
    }

    private void askForCustomerDocument(WhatsAppConversation conversation, WhatsAppCart cart) {
        List<WhatsAppCartItem> items = cartRepository.findItems(cart.getId());
        if (items.isEmpty()) {
            askForProduct(conversation, "Aún no tienes productos en el pedido. Escribe el producto que deseas cotizar.");
            return;
        }
        if (cart.getProformaId() != null) {
            finalizeProformaInfo(conversation, cart, null);
            return;
        }
        cartRepository.updateStatus(cart.getId(), WhatsAppEnums.CartStatus.WAITING_CUSTOMER);
        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_CUSTOMER_DOCUMENT);

        sendListQuietly(conversation.getId(),
                buildCartSummary(cart.getId()) + "\n\n¿A nombre de quién deseas preparar la proforma? Puedes identificar al cliente o continuar con el nombre del chat.",
                "Identificar cliente",
                "Datos del cliente",
                List.of(
                        new InteractiveListRow("DOC_DNI", "DNI", "Ingresar DNI de 8 dígitos"),
                        new InteractiveListRow("DOC_RUC", "RUC", "Ingresar RUC de 11 dígitos"),
                        new InteractiveListRow("DOC_NAME", "Solo nombre", "Registrar proforma solo con nombre"),
                        new InteractiveListRow("DOC_OMIT", "Omitir", "Usar el nombre del chat como referencia"),
                        new InteractiveListRow("CANCEL", "Cancelar", "Cancelar esta proforma")
                ));
    }

    private void handleCustomerDocument(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        if (cmd.isDniOption()) {
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_DNI_NUMBER);
            sendTextQuietly(conversation.getId(), "Perfecto. Escribe el número de DNI. Ejemplo: 12345678");
            return;
        }
        if (cmd.isRucOption()) {
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_RUC_NUMBER);
            sendTextQuietly(conversation.getId(), "Perfecto. Escribe el número de RUC. Ejemplo: 20611603739");
            return;
        }
        if (cmd.isNameOnlyOption()) {
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.WAITING_CUSTOMER_NAME);
            sendTextQuietly(conversation.getId(), "Escribe el nombre del cliente para la proforma. Ejemplo: José Ramírez");
            return;
        }
        if (cmd.isOmitCustomerIdentity() || cmd.isSkipName()) {
            WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversation.getId());
            cartRepository.setCustomerName(cart.getId(), defaultChatCustomerName(conversation));
            WhatsAppCart refreshedCart = refreshOpenCart(conversation, cart);
            finalizeProformaInfo(conversation, refreshedCart, defaultChatCustomerName(conversation));
            return;
        }

        String documentNumber = cmd.businessText().replaceAll("\\D", "");
        if (documentNumber.length() == 8) {
            registerDocumentAndFinish(conversation, "DNI", documentNumber);
            return;
        }
        if (documentNumber.length() == 11) {
            registerDocumentAndFinish(conversation, "RUC", documentNumber);
            return;
        }

        sendListQuietly(conversation.getId(),
                "No pude identificar el dato. Elige una opción o envía directamente un DNI de 8 dígitos o RUC de 11 dígitos.",
                "Identificar cliente",
                "Datos del cliente",
                List.of(
                        new InteractiveListRow("DOC_DNI", "DNI", "Ingresar DNI de 8 dígitos"),
                        new InteractiveListRow("DOC_RUC", "RUC", "Ingresar RUC de 11 dígitos"),
                        new InteractiveListRow("DOC_NAME", "Solo nombre", "Registrar proforma solo con nombre"),
                        new InteractiveListRow("DOC_OMIT", "Omitir", "Usar el nombre del chat como referencia"),
                        new InteractiveListRow("CANCEL", "Cancelar", "Cancelar esta proforma")
                ));
    }

    private void handleDniNumber(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        String documentNumber = cmd.businessText().replaceAll("\\D", "");
        if (documentNumber.length() != 8) {
            sendTextQuietly(conversation.getId(), "DNI inválido. Debe tener 8 dígitos. También puedes escribir 'cancelar'.");
            return;
        }
        registerDocumentAndFinish(conversation, "DNI", documentNumber);
    }

    private void handleRucNumber(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        String documentNumber = cmd.businessText().replaceAll("\\D", "");
        if (documentNumber.length() != 11) {
            sendTextQuietly(conversation.getId(), "RUC inválido. Debe tener 11 dígitos. También puedes escribir 'cancelar'.");
            return;
        }
        registerDocumentAndFinish(conversation, "RUC", documentNumber);
    }

    private void registerDocumentAndFinish(WhatsAppConversation conversation, String documentType, String documentNumber) {
        WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversation.getId());
        cartRepository.setCustomerDocument(cart.getId(), documentType, documentNumber);

        Optional<ResolvedCustomerSnapshot> resolvedCustomer = resolveCustomerSnapshot(documentType, documentNumber, conversation.getId());
        String customerName = firstNonBlank(
                resolvedCustomer.map(ResolvedCustomerSnapshot::legalName).orElse(null),
                defaultChatCustomerName(conversation)
        );
        cartRepository.setCustomerName(cart.getId(), customerName);

        WhatsAppCart refreshedCart = refreshOpenCart(conversation, cart);
        finalizeProformaInfo(conversation, refreshedCart, customerName);
    }

    private void handleCustomerName(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversation.getId());
        String customerName = cmd.isSkipName() || cmd.isOmitCustomerIdentity()
                ? defaultChatCustomerName(conversation)
                : sanitizeCustomerName(cmd.businessText());
        if (customerName == null || customerName.isBlank()) {
            customerName = defaultChatCustomerName(conversation);
        }
        cartRepository.setCustomerName(cart.getId(), customerName);
        WhatsAppCart refreshedCart = refreshOpenCart(conversation, cart);
        finalizeProformaInfo(conversation, refreshedCart, customerName);
    }

    private void finalizeProformaInfo(WhatsAppConversation conversation, WhatsAppCart cart, String customerName) {
        cart = refreshOpenCart(conversation, cart);
        Optional<ResolvedCustomerSnapshot> resolvedCustomer = resolveCustomerSnapshot(cart);

        String effectiveCustomerName = firstNonBlank(
                resolvedCustomer.map(ResolvedCustomerSnapshot::legalName).orElse(null),
                customerName,
                cart.getCustomerName(),
                defaultChatCustomerName(conversation)
        );

        String document = (cart.getCustomerDocumentType() == null || cart.getCustomerDocumentNumber() == null)
                ? "No indicado"
                : cart.getCustomerDocumentType() + " " + cart.getCustomerDocumentNumber();

        boolean amendment = cart.getProformaId() != null;

        if (properties.getSales().isAllowProformaGeneration()) {
            Optional<ProformaV2Response> saved = amendment
                    ? appendItemsToCurrentProforma(conversation, cart)
                    : createProformaFromCart(conversation, cart, effectiveCustomerName, resolvedCustomer);

            if (saved.isPresent()) {
                ProformaV2Response proforma = saved.get();
                Long proformaId = proforma.getId() != null ? proforma.getId() : cart.getProformaId();
                cartRepository.linkProforma(cart.getId(), proformaId);
                conversationRepository.linkProforma(conversation.getId(), proformaId);
                conversationRepository.updateStatus(conversation.getId(), WhatsAppEnums.ConversationStatus.QUOTED);
                conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.PROFORMA_CREATED);

                String savedDocument = buildProformaDocumentLabel(proforma, document);
                String savedCustomerName = firstNonBlank(proforma.getCustomerName(), effectiveCustomerName);
                String savedSummary = buildProformaResponseSummary(proforma, cart.getId());

                sendTextQuietly(conversation.getId(),
                        "Listo. " + (amendment ? "Actualicé" : "Generé") + " tu proforma " + formatProformaDoc(proforma) + ".\n" +
                                "Documento: " + savedDocument + "\n" +
                                "Cliente: " + savedCustomerName + "\n\n" +
                                savedSummary + "\n\n" +
                                "Total: S/ " + formatMoney(proforma.getTotal()) + ". Un asesor puede revisar cualquier detalle adicional.");
                return;
            }
        }

        cartRepository.updateStatus(cart.getId(), WhatsAppEnums.CartStatus.READY_TO_PROFORMA);
        conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.PROFORMA_CREATED);

        sendTextQuietly(conversation.getId(),
                "Listo ✅ Ya tengo la información para la proforma.\n" +
                        "Origen del chat: WhatsApp " + conversation.getWaId() + " (" + defaultChatCustomerName(conversation) + ")\n" +
                        "Documento: " + document + "\n" +
                        "Cliente: " + effectiveCustomerName + "\n\n" +
                        buildCartSummary(cart.getId()) + "\n\nUn asesor validará la proforma final y te enviará el documento.");
    }

    private Optional<ProformaV2Response> appendItemsToCurrentProforma(WhatsAppConversation conversation, WhatsAppCart cart) {
        List<WhatsAppCartItem> items = cartRepository.findItems(cart.getId());
        if (cart.getProformaId() == null || items.isEmpty()) return Optional.empty();

        try {
            return Optional.of(proformaCreationService.appendItems(cart.getProformaId(), items));
        } catch (Exception exception) {
            log.error("No se pudo actualizar proforma desde WhatsApp. conversationId={}, cartId={}, proformaId={}, error={}",
                    conversation.getId(), cart.getId(), cart.getProformaId(), exception.getMessage(), exception);
            return Optional.empty();
        }
    }

    private Optional<ProformaV2Response> createProformaFromCart(WhatsAppConversation conversation,
                                                                WhatsAppCart cart,
                                                                String customerName,
                                                                Optional<ResolvedCustomerSnapshot> resolvedCustomer) {
        cart = refreshOpenCart(conversation, cart);
        List<WhatsAppCartItem> items = cartRepository.findItems(cart.getId());
        if (items.isEmpty()) return Optional.empty();

        try {
            Optional<Long> createdBy = resolveProformaCreatedBy();
            if (createdBy.isEmpty()) {
                log.error("No se pudo generar proforma desde WhatsApp porque no hay usuario habilitado para created_by. Configure WHATSAPP_PROFORMA_CREATED_BY o WHATSAPP_PROFORMA_CREATED_BY_USERNAME.");
                return Optional.empty();
            }

            ResolvedCustomerSnapshot resolved = resolvedCustomer.orElse(null);
            String effectiveCustomerName = firstNonBlank(
                    resolved == null ? null : resolved.legalName(),
                    customerName,
                    cart.getCustomerName(),
                    defaultChatCustomerName(conversation)
            );

            String effectiveDocType = firstNonBlank(
                    cart.getCustomerDocumentType(),
                    resolved == null || resolved.documentType() == null ? null : resolved.documentType().name()
            );
            String effectiveDocNumber = firstNonBlank(
                    cart.getCustomerDocumentNumber(),
                    resolved == null ? null : resolved.documentNumber()
            );

            CreateProformaV2Request request = CreateProformaV2Request.builder()
                    .stationId(properties.getSales().getProformaStationId())
                    .createdBy(createdBy.get())
                    .series(properties.getSales().getProformaSeries())
                    .priceList(resolveProformaPriceList(items))
                    .currency("PEN")
                    .taxStatus(resolveProformaTaxStatus())
                    .igvRate(properties.getSales().getProformaIgvRate())
                    .igvIncluded(properties.getSales().isProformaIgvIncluded())
                    .customerId(resolved == null ? null : resolved.id())
                    .customerDocType(effectiveDocType)
                    .customerDocNumber(effectiveDocNumber)
                    .customerName(effectiveCustomerName)
                    .customerAddress(resolved == null ? null : resolved.address())
                    .customerUbigeo(resolved == null ? null : resolved.ubigeo())
                    .customerDepartment(resolved == null ? null : resolved.department())
                    .customerProvince(resolved == null ? null : resolved.province())
                    .customerDistrict(resolved == null ? null : resolved.district())
                    .paymentType(PaymentType.CONTADO)
                    .notes("Generada desde WhatsApp " + conversation.getWaId())
                    .items(items.stream()
                            .map(item -> CreateProformaV2Request.Item.builder()
                                    .productId(item.getProductId())
                                    .description(item.getProductName())
                                    .quantity(item.getQuantity().stripTrailingZeros().toPlainString())
                                    .unitPriceOverride(item.getUnitPrice())
                                    .build())
                            .toList())
                    .build();

            return Optional.of(proformaCreationService.create(request));
        } catch (Exception exception) {
            log.error("No se pudo generar proforma desde WhatsApp. conversationId={}, cartId={}, error={}",
                    conversation.getId(), cart.getId(), exception.getMessage(), exception);
            return Optional.empty();
        }
    }

    private boolean isPostProformaState(WhatsAppEnums.ConversationState state) {
        return state == WhatsAppEnums.ConversationState.PROFORMA_CREATED
                || state == WhatsAppEnums.ConversationState.GENERATING_PROFORMA;
    }

    private void prepareCurrentProformaAmendment(WhatsAppConversation conversation) {
        if (conversation == null || conversation.getId() == null || conversation.getLastProformaId() == null) {
            return;
        }

        WhatsAppCart cart = cartRepository.findOrCreateOpenCartForProforma(conversation.getId(), conversation.getLastProformaId());
        cartRepository.setCurrentProductSuggestion(cart.getId(), null);
    }

    private WhatsAppCart refreshOpenCart(WhatsAppConversation conversation, WhatsAppCart fallback) {
        if (conversation == null || conversation.getId() == null) return fallback;
        return cartRepository.findOpenByConversationId(conversation.getId()).orElse(fallback);
    }

    private Optional<ResolvedCustomerSnapshot> resolveCustomerSnapshot(WhatsAppCart cart) {
        if (cart == null) return Optional.empty();
        return resolveCustomerSnapshot(cart.getCustomerDocumentType(), cart.getCustomerDocumentNumber(), cart.getConversationId());
    }

    private Optional<ResolvedCustomerSnapshot> resolveCustomerSnapshot(String documentType, String documentNumber, Long conversationId) {
        Optional<DocumentType> parsedType = parseDocumentType(documentType);
        String number = documentNumber == null ? "" : documentNumber.trim();
        if (parsedType.isEmpty() || number.isBlank()) return Optional.empty();
        if (parsedType.get() == DocumentType.DNI && !number.matches("\\d{8}")) return Optional.empty();
        if (parsedType.get() == DocumentType.RUC && !number.matches("\\d{11}")) return Optional.empty();

        try {
            Customer resolved = resolveCustomerUseCase.resolveCustomer(Customer.builder()
                    .documentType(parsedType.get())
                    .documentNumber(number)
                    .enabled(true)
                    .build());
            return Optional.of(ResolvedCustomerSnapshot.from(resolved));
        } catch (Exception exception) {
            log.warn("No se pudo resolver cliente WhatsApp por documento. conversationId={}, documentType={}, documentNumber={}, error={}",
                    conversationId, documentType, documentNumber, exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<DocumentType> parseDocumentType(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(DocumentType.valueOf(value.trim().toUpperCase()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private record ResolvedCustomerSnapshot(
            Long id,
            String legalName,
            DocumentType documentType,
            String documentNumber,
            String address,
            String ubigeo,
            String department,
            String province,
            String district
    ) {
        static ResolvedCustomerSnapshot from(Customer customer) {
            if (customer == null) {
                return new ResolvedCustomerSnapshot(null, null, null, null, null, null, null, null, null);
            }
            return new ResolvedCustomerSnapshot(
                    customer.getId(),
                    customer.getLegalName(),
                    customer.getDocumentType(),
                    customer.getDocumentNumber(),
                    customer.getAddress(),
                    customer.getUbigeo(),
                    customer.getDepartment(),
                    customer.getProvince(),
                    customer.getDistrict()
            );
        }
    }

    private Optional<Long> resolveProformaCreatedBy() {
        Long configuredUserId = properties.getSales().getProformaCreatedBy();
        if (configuredUserId != null && configuredUserId > 0) {
            Optional<User> configuredUser = userRepository.findById(configuredUserId);
            if (configuredUser.isPresent() && configuredUser.get().isEnabled()) {
                return Optional.of(configuredUserId);
            }
            log.warn("Usuario configurado para proformas WhatsApp no existe o está deshabilitado. configuredUserId={}", configuredUserId);
        }

        String configuredUsername = properties.getSales().getProformaCreatedByUsername();
        if (configuredUsername != null && !configuredUsername.isBlank()) {
            Optional<User> configuredUser = userRepository.findByUsername(configuredUsername.trim());
            if (configuredUser.isPresent() && configuredUser.get().isEnabled()) {
                return Optional.of(configuredUser.get().getId());
            }
            log.warn("Usuario configurado para proformas WhatsApp no existe o está deshabilitado. configuredUsername={}", configuredUsername);
        }

        Optional<Long> fallbackUserId = userRepository.findAll(null).stream()
                .filter(User::isEnabled)
                .filter(user -> user.getId() != null)
                .min(Comparator.comparing(User::getId))
                .map(User::getId);

        fallbackUserId.ifPresent(userId ->
                log.warn("Usando usuario habilitado fallback para proformas WhatsApp. userId={}. Configure WHATSAPP_PROFORMA_CREATED_BY para evitar este fallback.", userId));

        return fallbackUserId;
    }

    private Character resolveProformaPriceList(List<WhatsAppCartItem> items) {
        String fromItem = items.stream()
                .map(WhatsAppCartItem::getPriceList)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(properties.getSales().normalizedDefaultPriceList());
        return fromItem.trim().toUpperCase().charAt(0);
    }

    private TaxStatus resolveProformaTaxStatus() {
        String value = properties.getSales().getProformaTaxStatus();
        if (value == null || value.isBlank()) return TaxStatus.NO_GRAVADA;
        try {
            return TaxStatus.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return TaxStatus.NO_GRAVADA;
        }
    }

    private String formatProformaDoc(ProformaV2Response proforma) {
        if (proforma == null) return "";
        String series = proforma.getSeries() == null ? properties.getSales().getProformaSeries() : proforma.getSeries();
        Long number = proforma.getNumber();
        return number == null ? series : series + "-" + number;
    }

    private void handlePostProforma(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        if (cmd.isSearchMenu()) {
            prepareCurrentProformaAmendment(conversation);
            askForProduct(conversation, "Escribe el producto que deseas agregar a la proforma. Ejemplo: casco, foco led, código DK151092.");
            return;
        }
        if (isRemoveProductIntent(cmd)) {
            handleRemoveProductFromCurrentProforma(conversation, cmd);
            return;
        }
        if (cmd.hasProductSearchText()) {
            prepareCurrentProformaAmendment(conversation);
            if (tryHandleWithAiAgent(conversation, cmd)) return;
            searchAndOfferProducts(conversation, cmd.businessText());
            return;
        }
        if (cmd.isAdvisor()) {
            conversationRepository.updateStatus(conversation.getId(), WhatsAppEnums.ConversationStatus.PENDING_HUMAN);
            sendTextQuietly(conversation.getId(), "Te derivaré con un asesor.");
            return;
        }
        sendMainMenu(conversation.getId(), "¿Deseas buscar otro producto, generar una nueva proforma o hablar con un asesor?");
    }

    private boolean isRemoveProductIntent(WhatsAppIncomingCommand cmd) {
        if (cmd == null || cmd.normalizedText() == null) return false;
        String text = cmd.normalizedText();
        return text.contains("no quiero")
                || text.contains("ya no quiero")
                || text.contains("no deseo")
                || text.contains("ya no deseo")
                || text.startsWith("quitar ")
                || text.startsWith("quita ")
                || text.startsWith("eliminar ")
                || text.startsWith("elimina ")
                || text.startsWith("sacar ")
                || text.startsWith("saca ")
                || text.startsWith("retirar ")
                || text.startsWith("retira ")
                || text.startsWith("borra ")
                || text.startsWith("borrar ");
    }

    private void handleRemoveProductFromCurrentProforma(WhatsAppConversation conversation, WhatsAppIncomingCommand cmd) {
        Long proformaId = conversation.getLastProformaId();
        if (proformaId == null) {
            sendTextQuietly(conversation.getId(), "Aún no hay una proforma activa para modificar.");
            return;
        }

        String text = cmd == null ? "" : cmd.businessText();
        Set<Long> productIds = new LinkedHashSet<>();
        Set<String> productCodes = new LinkedHashSet<>(extractCodeCandidates(text));

        Optional<WhatsAppProductSearchResult> exact = searchProductsService.findExactForBot(text);
        exact.ifPresent(product -> {
            productIds.add(product.getProductId());
            if (product.getSku() != null && !product.getSku().isBlank()) {
                productCodes.add(product.getSku());
            }
        });

        if (productIds.isEmpty() && productCodes.isEmpty()) {
            sendTextQuietly(conversation.getId(), "Indícame el código exacto que deseas quitar. Ejemplo: quitar DK151092.");
            return;
        }

        try {
            WhatsAppProformaCreationService.RemoveItemsResult result = proformaCreationService.removeItems(
                    proformaId,
                    new ArrayList<>(productIds),
                    new ArrayList<>(productCodes)
            );

            if (result.wouldLeaveEmptyProforma()) {
                sendTextQuietly(conversation.getId(), "No puedo dejar la proforma sin productos. Si deseas cancelarla completa, pide apoyo a un asesor.");
                return;
            }

            if (result.removedCount() <= 0 || result.proforma() == null) {
                sendTextQuietly(conversation.getId(), "No encontré ese producto en la proforma actual. Revisa el código o escribe otro.");
                return;
            }

            ProformaV2Response proforma = result.proforma();
            conversationRepository.updateStatus(conversation.getId(), WhatsAppEnums.ConversationStatus.QUOTED);
            conversationRepository.updateConversationState(conversation.getId(), WhatsAppEnums.ConversationState.PROFORMA_CREATED);

            sendTextQuietly(conversation.getId(),
                    "Listo. Quité el producto de tu proforma " + formatProformaDoc(proforma) + ".\n" +
                            "Documento: " + buildProformaDocumentLabel(proforma, null) + "\n" +
                            "Cliente: " + firstNonBlank(proforma.getCustomerName(), defaultChatCustomerName(conversation)) + "\n\n" +
                            buildProformaResponseSummary(proforma, null) + "\n\n" +
                            "Total: S/ " + formatMoney(proforma.getTotal()) + ".");
        } catch (Exception exception) {
            log.error("No se pudo quitar producto de proforma WhatsApp. conversationId={}, proformaId={}, error={}",
                    conversation.getId(), proformaId, exception.getMessage(), exception);
            sendTextQuietly(conversation.getId(), "No pude quitar ese producto ahora. Un asesor puede revisarlo.");
        }
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

    private String buildBatchPreviewMessage(WhatsAppBatchQuoteResult result, String source) {
        StringBuilder builder = new StringBuilder();
        builder.append("Procesé tu lista de códigos");
        if (source != null && !source.isBlank()) builder.append(" desde ").append(source);
        builder.append(" ✅");

        int totalCodes = result.getProcessedCodesCount() > 0 ? result.getProcessedCodesCount() : result.getExtractedCodesCount();
        builder.append("\nCódigos leídos: ").append(totalCodes);
        if (result.isLimitedByMax()) {
            builder.append(" (máximo procesado: ").append(result.getMaxCodesAllowed()).append(")");
        }
        builder.append("\nDisponibles para cotizar: ").append(result.getAddedItems().size());
        builder.append("\nNo disponibles / no encontrados: ").append(result.unavailableCount());

        if (result.hasAddedItems()) {
            builder.append("\n\nProductos disponibles para cotizar:");
            int index = 1;
            for (WhatsAppBatchQuoteResult.AddedItem item : result.getAddedItems()) {
                if (index > 10) {
                    builder.append("\n... y más productos disponibles.");
                    break;
                }
                builder.append("\n").append(index++).append(") ")
                        .append(nullSafe(item.getCode())).append(" - ")
                        .append(shortLine(item.getName(), 45))
                        .append(" | S/ ").append(formatMoney(item.getUnitPrice()));
            }
            builder.append("\n\nAhora te pediré la cantidad de cada producto.");
            builder.append("\nSi no deseas un producto, elige 0 / No agregar.");
        } else {
            builder.append("\n\nNo mostraré ni cotizaré productos sin stock.");
        }

        if (result.unavailableCount() > 0) {
            builder.append("\n\nOmití productos sin stock o no encontrados. Un asesor puede revisarlos si lo necesitas.");
        }
        return builder.toString();
    }

    private String buildBatchConfirmedMessage(WhatsAppBatchQuoteResult result) {
        StringBuilder builder = new StringBuilder("Listo OK Agregue al pedido:");
        int index = 1;
        for (WhatsAppBatchQuoteResult.AddedItem item : result.getAddedItems()) {
            if (index > 10) {
                builder.append("\n... y mas productos agregados.");
                break;
            }
            builder.append("\n").append(index++).append(") ")
                    .append(shortLine(item.getName(), 48))
                    .append(" x ").append(formatStock(item.getQuantity()))
                    .append(" = S/ ").append(formatMoney(item.getLineTotal()));
        }
        builder.append("\nTotal agregado: S/ ").append(formatMoney(result.getTotal()));
        return builder.toString();
    }

    private String mediaLabel(WhatsAppEnums.MessageType type) {
        if (type == WhatsAppEnums.MessageType.IMAGE) return "imagen";
        if (type == WhatsAppEnums.MessageType.DOCUMENT) return "documento";
        return "archivo";
    }

    private String buildProformaDocumentLabel(ProformaV2Response proforma, String fallback) {
        if (proforma == null) return firstNonBlank(fallback, "No indicado");

        String docType = proforma.getCustomerDocType();
        String docNumber = proforma.getCustomerDocNumber();

        if (docType == null || docType.isBlank() || docNumber == null || docNumber.isBlank()) {
            return firstNonBlank(fallback, "No indicado");
        }

        return docType.trim() + " " + docNumber.trim();
    }

    private String buildProformaResponseSummary(ProformaV2Response proforma, Long fallbackCartId) {
        if (proforma == null || proforma.getItems() == null || proforma.getItems().isEmpty()) {
            return buildCartSummary(fallbackCartId);
        }

        StringBuilder builder = new StringBuilder("Resumen del pedido:");
        BigDecimal total = BigDecimal.ZERO;
        int index = 1;

        for (ProformaV2Response.Item item : proforma.getItems()) {
            BigDecimal lineTotal = item.getLineSubtotal() == null ? BigDecimal.ZERO : item.getLineSubtotal();
            total = total.add(lineTotal);

            builder.append("\n").append(index++).append(") ")
                    .append(firstNonBlank(item.getSku(), ""));

            if (item.getDescription() != null && !item.getDescription().isBlank()) {
                if (item.getSku() != null && !item.getSku().isBlank()) {
                    builder.append(" ");
                }
                builder.append(shortLine(item.getDescription(), 60));
            }

            builder.append(" x ").append(formatStock(item.getQuantity()))
                    .append(" = S/ ").append(formatMoney(lineTotal));
        }

        builder.append("\nTotal estimado: S/ ").append(formatMoney(total));
        return builder.toString();
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
                + "\nPrecio: S/ " + formatMoney(product.getSelectedPrice());
    }


    private void sendProductQuantityPrompt(Long conversationId, Long productId, String rawImageUrl, String body) {
        sendImageButtonsQuietly(conversationId, body, productId, rawImageUrl, productQuantityButtons());
    }

    private List<InteractiveButton> productQuantityButtons() {
        return List.of(
                new InteractiveButton("BQTY_1", "1 unidad"),
                new InteractiveButton("BQTY_2", "2 unidades"),
                new InteractiveButton("BQTY_0", "No agregar")
        );
    }

    private void sendProductImageOrText(Long conversationId, Long productId, String rawImageUrl, String caption) {
        if (!sendProductImageIfAvailable(conversationId, productId, rawImageUrl, caption)) {
            sendTextQuietly(conversationId, caption);
        }
    }

    private boolean sendProductImageIfAvailable(Long conversationId, Long productId, String rawImageUrl, String caption) {
        String imageUrl = buildProductImageUrl(productId, rawImageUrl);
        if (imageUrl == null || imageUrl.isBlank()) return false;

        try {
            sendMessageService.sendImageToConversation(conversationId, imageUrl, caption);
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar imagen de producto por WhatsApp. conversationId={}, productId={}, imageUrl={}, error={}",
                    conversationId, productId, imageUrl, e.getMessage());
            return false;
        }
    }

    private String buildProductImageUrl(Long productId, String rawImageUrl) {
        if (!properties.getSales().isSendProductImages()) return null;
        if (rawImageUrl == null || rawImageUrl.isBlank()) return null;

        String image = rawImageUrl.trim();
        String base = properties.getSales().getProductImagesBaseUrl();

        if (image.startsWith("http://") || image.startsWith("https://")) {
            if (base == null || base.isBlank()) return image;

            String marker = "/images/products/";
            int markerIndex = image.indexOf(marker);
            if (markerIndex < 0) return image;

            image = image.substring(markerIndex + marker.length());
        }

        if (base == null || base.isBlank()) return null;

        String normalizedBase = base.trim();
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        String normalizedImage = image;
        while (normalizedImage.startsWith("/")) {
            normalizedImage = normalizedImage.substring(1);
        }

        String prefix = "images/products/";
        if (normalizedImage.startsWith(prefix)) {
            normalizedImage = normalizedImage.substring(prefix.length());
        }

        if (!normalizedImage.contains("/") && productId != null) {
            normalizedImage = productId + "/" + normalizedImage;
        }

        return normalizedBase + "/" + normalizedImage;
    }

    private List<String> extractCodeCandidates(String rawQuery) {
        String text = rawQuery == null ? "" : rawQuery.trim();
        if (text.isBlank()) return List.of();

        Set<String> candidates = new LinkedHashSet<>();
        String[] tokens = text.split("[^A-Za-z0-9_-]+");
        for (String token : tokens) {
            String normalized = token == null ? "" : token.trim();
            if (normalized.length() < 4) continue;

            boolean hasDigit = normalized.matches(".*\d.*");
            boolean hasLetter = normalized.matches(".*[A-Za-z].*");
            boolean codeLike = hasDigit || (hasLetter && normalized.equals(normalized.toUpperCase(Locale.ROOT)));
            if (!codeLike) continue;

            candidates.add(normalized.toUpperCase(Locale.ROOT));
        }

        List<String> result = new ArrayList<>(candidates);
        result.sort((left, right) -> Integer.compare(right.length(), left.length()));
        return result;
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

    private void sendImageButtonsQuietly(Long conversationId, String body, Long productId, String rawImageUrl, List<InteractiveButton> buttons) {
        String imageUrl = buildProductImageUrl(productId, rawImageUrl);

        try {
            if (imageUrl != null && !imageUrl.isBlank()) {
                sendMessageService.sendButtonsWithImageHeaderToConversation(conversationId, body, imageUrl, buttons);
            } else {
                sendMessageService.sendButtonsToConversation(conversationId, body, buttons);
            }
        } catch (Exception e) {
            log.warn("No se pudo enviar botón con imagen. conversationId={}, productId={}, imageUrl={}, error={}",
                    conversationId, productId, imageUrl, e.getMessage());
            sendButtonsQuietly(conversationId, body, buttons);
        }
    }

    private void sendListQuietly(Long conversationId, String body, String buttonText, String sectionTitle, List<InteractiveListRow> rows) {
        try {
            sendMessageService.sendListToConversation(conversationId, body, buttonText, sectionTitle, rows);
        } catch (Exception e) {
            log.error("No se pudo enviar lista WhatsApp. conversationId={}, error={}", conversationId, e.getMessage(), e);
        }
    }

    private String defaultChatCustomerName(WhatsAppConversation conversation) {
        String profile = conversation == null ? null : conversation.getProfileName();
        if (profile != null && !profile.isBlank()) return sanitizeCustomerName(profile);
        String waId = conversation == null ? null : conversation.getWaId();
        return waId == null || waId.isBlank() ? "Cliente WhatsApp" : "Cliente WhatsApp " + waId;
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

    private String stringArg(AiAgentAction action, String key) {
        if (action == null || action.getArguments() == null || key == null) return null;
        Object value = action.getArguments().get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String nullSafe(String value) { return value == null ? "" : value; }
    private String formatMoney(BigDecimal value) { return value == null ? "0.00" : MONEY_FORMAT.format(value); }
    private String formatStock(BigDecimal value) { return value == null ? "0" : STOCK_FORMAT.format(value); }
}
