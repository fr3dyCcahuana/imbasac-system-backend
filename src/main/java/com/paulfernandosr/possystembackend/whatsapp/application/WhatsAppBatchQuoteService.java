package com.paulfernandosr.possystembackend.whatsapp.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppCartRepository;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppProductSuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WhatsAppBatchQuoteService {
    private final WhatsAppIntegrationProperties properties;
    private final SearchWhatsAppProductsService searchProductsService;
    private final WhatsAppCartRepository cartRepository;
    private final WhatsAppProductSuggestionRepository suggestionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Previsualiza una lista de códigos, pero NO agrega al carrito todavía.
     * Esto evita asumir cantidades sin confirmación del cliente.
     */
    public WhatsAppBatchQuoteResult previewCodes(Long conversationId, List<WhatsAppBatchCodeLine> codes, String source) {
        WhatsAppBatchQuoteResult result = WhatsAppBatchQuoteResult.builder()
                .total(BigDecimal.ZERO)
                .extractedCodesCount(codes == null ? 0 : codes.size())
                .processedCodesCount(0)
                .maxCodesAllowed(Math.max(1, properties.getSales().getMaxBatchCodesPerMessage()))
                .limitedByMax(codes != null && codes.size() >= Math.max(1, properties.getSales().getMaxBatchCodesPerMessage()))
                .build();
        if (codes == null || codes.isEmpty()) return result;

        BigDecimal total = BigDecimal.ZERO;
        for (WhatsAppBatchCodeLine line : codes) {
            String code = line.getCode();
            BigDecimal qty = line.getQuantity() == null ? BigDecimal.ONE : line.getQuantity();
            result.setProcessedCodesCount(result.getProcessedCodesCount() + 1);

            Optional<WhatsAppProductSearchResult> productOpt = searchProductsService.findExactForBot(code);
            if (productOpt.isEmpty()) {
                result.getNotFoundCodes().add(code);
                continue;
            }

            WhatsAppProductSearchResult product = productOpt.get();
            BigDecimal stock = product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
            if (stock.compareTo(BigDecimal.ZERO) <= 0) {
                result.getNoStockCodes().add(code);
                continue;
            }
            if (qty.compareTo(stock) > 0) {
                result.getInsufficientStockCodes().add(code + " solicitado " + fmt(qty) + ", disponible " + fmt(stock));
                continue;
            }

            BigDecimal unitPrice = product.getSelectedPrice() == null ? BigDecimal.ZERO : product.getSelectedPrice();
            BigDecimal lineTotal = unitPrice.multiply(qty);
            total = total.add(lineTotal);

            result.getAddedItems().add(WhatsAppBatchQuoteResult.AddedItem.builder()
                    .productId(product.getProductId())
                    .code(product.getSku())
                    .name(product.getName())
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .stock(stock)
                    .priceList(product.getSelectedPriceList())
                    .build());
        }
        result.setTotal(total);

        if (result.hasAddedItems()) {
            suggestionRepository.replaceBatchSuggestions(conversationId, result.getAddedItems(), source == null ? "batch" : source);
            cartRepository.findOrCreateOpenCart(conversationId);
        }
        return result;
    }

    /**
     * Confirma la última previsualización masiva y la agrega al carrito.
     */
    public WhatsAppBatchQuoteResult confirmLatestBatchSuggestions(Long conversationId) {
        WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversationId);
        List<WhatsAppProductSuggestion> suggestions = suggestionRepository.findLatest(conversationId, Math.max(1, properties.getSales().getMaxBatchCodesPerMessage()));
        WhatsAppBatchQuoteResult result = WhatsAppBatchQuoteResult.builder()
                .total(BigDecimal.ZERO)
                .extractedCodesCount(suggestions.size())
                .processedCodesCount(suggestions.size())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (WhatsAppProductSuggestion suggestion : suggestions) {
            BigDecimal qty = requestedQuantity(suggestion).max(BigDecimal.ONE);
            BigDecimal stock = suggestion.getStockQuantity() == null ? BigDecimal.ZERO : suggestion.getStockQuantity();
            if (stock.compareTo(BigDecimal.ZERO) <= 0) {
                result.getNoStockCodes().add(suggestion.getProductCode());
                continue;
            }
            if (qty.compareTo(stock) > 0) {
                result.getInsufficientStockCodes().add(suggestion.getProductCode() + " solicitado " + fmt(qty) + ", disponible " + fmt(stock));
                continue;
            }

            BigDecimal unitPrice = suggestion.getUnitPrice() == null ? BigDecimal.ZERO : suggestion.getUnitPrice();
            BigDecimal lineTotal = unitPrice.multiply(qty);
            total = total.add(lineTotal);

            cartRepository.addItem(WhatsAppCartItem.builder()
                    .cartId(cart.getId())
                    .productId(suggestion.getProductId())
                    .productCode(suggestion.getProductCode())
                    .productName(suggestion.getProductName())
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .priceList(suggestion.getPriceList())
                    .build());

            result.getAddedItems().add(WhatsAppBatchQuoteResult.AddedItem.builder()
                    .productId(suggestion.getProductId())
                    .code(suggestion.getProductCode())
                    .name(suggestion.getProductName())
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .stock(stock)
                    .priceList(suggestion.getPriceList())
                    .build());
        }
        result.setTotal(total);
        return result;
    }

    /** Compatibilidad con versiones anteriores: ahora previsualiza y confirma en dos pasos. */
    public WhatsAppBatchQuoteResult addCodesToCart(Long conversationId, List<WhatsAppBatchCodeLine> codes) {
        WhatsAppBatchQuoteResult preview = previewCodes(conversationId, codes, "mensaje");
        if (!preview.hasAddedItems()) return preview;
        return confirmLatestBatchSuggestions(conversationId);
    }

    private BigDecimal requestedQuantity(WhatsAppProductSuggestion suggestion) {
        if (suggestion == null || suggestion.getRawSnapshot() == null || suggestion.getRawSnapshot().isBlank()) {
            return BigDecimal.ONE;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(suggestion.getRawSnapshot(), new TypeReference<>() {});
            Object value = map.get("requestedQuantity");
            if (value == null) return BigDecimal.ONE;
            BigDecimal qty = new BigDecimal(String.valueOf(value).replace(',', '.'));
            return qty.compareTo(BigDecimal.ZERO) > 0 ? qty : BigDecimal.ONE;
        } catch (Exception ignored) {
            return BigDecimal.ONE;
        }
    }

    private String fmt(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }
}
