package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppProductSearchResult;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppProductCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SearchWhatsAppProductsService {
    private final WhatsAppIntegrationProperties properties;
    private final WhatsAppProductCatalogRepository productCatalogRepository;

    public List<WhatsAppProductSearchResult> searchForBot(String rawQuery) {
        String priceList = properties.getSales().normalizedDefaultPriceList();
        int limit = properties.getSales().safeMaxProductsResponse();
        BigDecimal minimumStock = BigDecimal.valueOf(properties.getSales().getMinimumStockToShow());
        return productCatalogRepository.searchProducts(
                cleanQuery(rawQuery),
                priceList,
                limit,
                true,
                properties.getSales().isOnlyShowProductsWithStock(),
                minimumStock
        );
    }

    public Optional<WhatsAppProductSearchResult> findExactForBot(String rawQuery) {
        String priceList = properties.getSales().normalizedDefaultPriceList();
        return productCatalogRepository.findExactProduct(cleanQuery(rawQuery), priceList, true);
    }

    public List<WhatsAppProductSearchResult> searchForAdmin(String rawQuery, String requestedPriceList, Integer requestedLimit) {
        String priceList = normalizePriceList(requestedPriceList == null ? properties.getSales().normalizedDefaultPriceList() : requestedPriceList);
        int limit = requestedLimit == null || requestedLimit <= 0 ? properties.getSales().safeMaxProductsResponse() : Math.min(requestedLimit, 50);
        return productCatalogRepository.searchProducts(cleanQuery(rawQuery), priceList, limit, false, false, BigDecimal.ZERO);
    }

    public String resolveEffectivePriceList(String requestedPriceList) {
        return normalizePriceList(requestedPriceList == null ? properties.getSales().normalizedDefaultPriceList() : requestedPriceList);
    }

    private String normalizePriceList(String value) {
        if (value == null || value.isBlank()) return "A";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "A", "B", "C", "D" -> normalized;
            default -> "A";
        };
    }

    private String cleanQuery(String query) {
        if (query == null) return "";
        return query.trim().replaceAll("\\s+", " ");
    }
}
