package com.paulfernandosr.possystembackend.productoffer.application;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductBasicOfferPriceResolver {

    private final JdbcClient jdbcClient;

    public BigDecimal resolveLowerOfferPrice(Long productId, BigDecimal quantity, BigDecimal basePrice) {
        if (productId == null || quantity == null || basePrice == null) {
            return basePrice;
        }

        return findBestActiveOffer(productId, quantity)
                .map(OfferPrice::offerPrice)
                .filter(offerPrice -> offerPrice.compareTo(BigDecimal.ZERO) > 0)
                .filter(offerPrice -> offerPrice.compareTo(basePrice) < 0)
                .orElse(basePrice);
    }

    public Optional<OfferPrice> findBestActiveOffer(Long productId, BigDecimal quantity) {
        if (productId == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        return jdbcClient.sql("""
                SELECT
                    pbo.id AS offer_id,
                    pbo.code AS offer_code,
                    pbo.name AS offer_name,
                    pboi.offer_price,
                    pboi.min_quantity
                FROM product_basic_offer_item pboi
                JOIN product_basic_offer pbo ON pbo.id = pboi.offer_id
                WHERE pboi.product_id = ?
                  AND pbo.status = 'ACTIVE'
                  AND (pbo.starts_at IS NULL OR pbo.starts_at <= CURRENT_DATE)
                  AND (pbo.ends_at IS NULL OR pbo.ends_at >= CURRENT_DATE)
                  AND pboi.offer_price IS NOT NULL
                  AND pboi.offer_price > 0
                  AND COALESCE(pboi.min_quantity, 1) <= ?
                ORDER BY pboi.offer_price ASC, COALESCE(pboi.min_quantity, 1) ASC, pbo.id DESC
                LIMIT 1
                """)
                .param(productId)
                .param(quantity)
                .query((rs, rowNum) -> new OfferPrice(
                        rs.getLong("offer_id"),
                        rs.getString("offer_code"),
                        rs.getString("offer_name"),
                        rs.getBigDecimal("offer_price"),
                        rs.getBigDecimal("min_quantity")
                ))
                .optional();
    }

    public record OfferPrice(
            Long offerId,
            String offerCode,
            String offerName,
            BigDecimal offerPrice,
            BigDecimal minQuantity
    ) {
    }
}
