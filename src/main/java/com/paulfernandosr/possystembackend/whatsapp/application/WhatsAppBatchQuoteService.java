package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppCartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WhatsAppBatchQuoteService {
    private final SearchWhatsAppProductsService searchProductsService;
    private final WhatsAppCartRepository cartRepository;

    public WhatsAppBatchQuoteResult addCodesToCart(Long conversationId, List<WhatsAppBatchCodeLine> codes) {
        WhatsAppCart cart = cartRepository.findOrCreateOpenCart(conversationId);
        WhatsAppBatchQuoteResult result = WhatsAppBatchQuoteResult.builder().total(BigDecimal.ZERO).build();
        if (codes == null || codes.isEmpty()) return result;

        BigDecimal total = BigDecimal.ZERO;
        for (WhatsAppBatchCodeLine line : codes) {
            String code = line.getCode();
            BigDecimal qty = line.getQuantity() == null ? BigDecimal.ONE : line.getQuantity();
            Optional<WhatsAppProductSearchResult> productOpt = searchProductsService.findExactForBot(code);
            if (productOpt.isEmpty()) {
                result.getNotFoundCodes().add(code);
                continue;
            }

            WhatsAppProductSearchResult product = productOpt.get();
            BigDecimal stock = product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
            if (stock.compareTo(BigDecimal.ZERO) <= 0) {
                result.getNoStockCodes().add(code + " - " + product.getName());
                continue;
            }
            if (qty.compareTo(stock) > 0) {
                result.getInsufficientStockCodes().add(code + " - solicitado " + fmt(qty) + ", disponible " + fmt(stock));
                continue;
            }

            BigDecimal unitPrice = product.getSelectedPrice() == null ? BigDecimal.ZERO : product.getSelectedPrice();
            BigDecimal lineTotal = unitPrice.multiply(qty);
            total = total.add(lineTotal);

            cartRepository.addItem(WhatsAppCartItem.builder()
                    .cartId(cart.getId())
                    .productId(product.getProductId())
                    .productCode(product.getSku())
                    .productName(product.getName())
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .priceList(product.getSelectedPriceList())
                    .build());

            result.getAddedItems().add(WhatsAppBatchQuoteResult.AddedItem.builder()
                    .code(product.getSku())
                    .name(product.getName())
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .stock(stock)
                    .build());
        }
        result.setTotal(total);
        return result;
    }

    private String fmt(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }
}
