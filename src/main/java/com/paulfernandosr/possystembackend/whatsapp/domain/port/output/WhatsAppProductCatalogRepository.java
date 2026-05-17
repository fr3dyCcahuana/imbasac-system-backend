package com.paulfernandosr.possystembackend.whatsapp.domain.port.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppProductSearchResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface WhatsAppProductCatalogRepository {
    List<WhatsAppProductSearchResult> searchProducts(String query, String priceList, int limit, boolean onlyFacturable, boolean onlyWithStock, BigDecimal minimumStock);
    Optional<WhatsAppProductSearchResult> findExactProduct(String query, String priceList, boolean onlyFacturable);
}
