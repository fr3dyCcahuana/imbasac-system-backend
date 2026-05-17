package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppProductSearchResult;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppProductCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SearchWhatsAppProductsService {
    private final WhatsAppIntegrationProperties properties;
    private final WhatsAppProductCatalogRepository productCatalogRepository;

    public List<WhatsAppProductSearchResult> searchForBot(String rawQuery) {
        String priceList = properties.getSales().normalizedDefaultPriceList();
        int limit = properties.getSales().safeMaxProductsResponse();
        BigDecimal minimumStock = BigDecimal.valueOf(properties.getSales().getMinimumStockToShow());
        boolean onlyWithStock = properties.getSales().isOnlyShowProductsWithStock();

        String query = cleanQuery(rawQuery);
        List<WhatsAppProductSearchResult> direct = search(query, priceList, limit, true, onlyWithStock, minimumStock);
        if (!direct.isEmpty()) return direct;

        Optional<WhatsAppProductSearchResult> exact = findExactForBot(rawQuery);
        if (exact.isPresent() && (!onlyWithStock || safeStock(exact.get()).compareTo(minimumStock) >= 0)) {
            return List.of(exact.get());
        }

        for (String candidate : extractCodeCandidates(rawQuery)) {
            if (candidate.equalsIgnoreCase(query)) continue;
            List<WhatsAppProductSearchResult> byCandidate = search(candidate, priceList, limit, true, onlyWithStock, minimumStock);
            if (!byCandidate.isEmpty()) return byCandidate;
        }

        return List.of();
    }

    public Optional<WhatsAppProductSearchResult> findExactForBot(String rawQuery) {
        String priceList = properties.getSales().normalizedDefaultPriceList();
        String query = cleanQuery(rawQuery);

        Optional<WhatsAppProductSearchResult> direct = findExactInternal(query, priceList, true);
        if (direct.isPresent()) return direct;

        for (String candidate : extractCodeCandidates(rawQuery)) {
            Optional<WhatsAppProductSearchResult> exact = findExactInternal(candidate, priceList, true);
            if (exact.isPresent()) return exact;
        }

        return Optional.empty();
    }

    public List<WhatsAppProductSearchResult> searchForAdmin(String rawQuery, String requestedPriceList, Integer requestedLimit) {
        String priceList = normalizePriceList(requestedPriceList == null ? properties.getSales().normalizedDefaultPriceList() : requestedPriceList);
        int limit = requestedLimit == null || requestedLimit <= 0 ? properties.getSales().safeMaxProductsResponse() : Math.min(requestedLimit, 50);
        return productCatalogRepository.searchProducts(cleanQuery(rawQuery), priceList, limit, false, false, BigDecimal.ZERO);
    }

    public String resolveEffectivePriceList(String requestedPriceList) {
        return normalizePriceList(requestedPriceList == null ? properties.getSales().normalizedDefaultPriceList() : requestedPriceList);
    }

    private List<WhatsAppProductSearchResult> search(String query,
                                                     String priceList,
                                                     int limit,
                                                     boolean onlyFacturable,
                                                     boolean onlyWithStock,
                                                     BigDecimal minimumStock) {
        return productCatalogRepository.searchProducts(
                query,
                priceList,
                limit,
                onlyFacturable,
                onlyWithStock,
                minimumStock
        );
    }

    private Optional<WhatsAppProductSearchResult> findExactInternal(String query, String priceList, boolean onlyFacturable) {
        return productCatalogRepository.findExactProduct(query, priceList, onlyFacturable);
    }

    /**
     * Permite frases naturales como "agrega este codigo DK151092".
     * Primero se intenta el texto completo y, si no hay resultados, se prueban tokens tipo SKU/código.
     */
    private List<String> extractCodeCandidates(String rawQuery) {
        String text = rawQuery == null ? "" : rawQuery.trim();
        if (text.isBlank()) return List.of();

        Set<String> candidates = new LinkedHashSet<>();
        String[] tokens = text.split("[^A-Za-z0-9_-]+");
        for (String token : tokens) {
            String normalized = token == null ? "" : token.trim();
            if (normalized.length() < 4) continue;

            boolean hasDigit = normalized.matches(".*\\d.*");
            boolean hasLetter = normalized.matches(".*[A-Za-z].*");
            boolean codeLike = hasDigit || (hasLetter && normalized.equals(normalized.toUpperCase(Locale.ROOT)));
            if (!codeLike) continue;

            candidates.add(normalized);
        }

        List<String> result = new ArrayList<>(candidates);
        result.sort((left, right) -> Integer.compare(right.length(), left.length()));
        return result;
    }

    private BigDecimal safeStock(WhatsAppProductSearchResult product) {
        return product == null || product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
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
