package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppProductSearchResult;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppProductCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresWhatsAppProductCatalogRepository implements WhatsAppProductCatalogRepository {
    private final JdbcClient jdbcClient;

    private final RowMapper<WhatsAppProductSearchResult> mapper = (rs, rowNum) -> WhatsAppProductSearchResult.builder()
            .productId(rs.getLong("product_id"))
            .sku(rs.getString("sku"))
            .name(rs.getString("name"))
            .category(rs.getString("category"))
            .brand(rs.getString("brand"))
            .model(rs.getString("model"))
            .presentation(rs.getString("presentation"))
            .warehouseLocation(rs.getString("warehouse_location"))
            .manageBySerial(rs.getBoolean("manage_by_serial"))
            .stockQuantity(rs.getBigDecimal("stock_quantity"))
            .selectedPrice(rs.getBigDecimal("selected_price"))
            .selectedPriceList(rs.getString("selected_price_list"))
            .mainImageUrl(rs.getString("main_image_url"))
            .build();

    @Override
    public List<WhatsAppProductSearchResult> searchProducts(String query,
                                                            String priceList,
                                                            int limit,
                                                            boolean onlyFacturable,
                                                            boolean onlyWithStock,
                                                            BigDecimal minimumStock) {
        String normalizedQuery = query == null ? "" : query.trim();
        String like = "%" + normalizedQuery + "%";
        String priceColumn = priceColumn(priceList);
        BigDecimal minStock = minimumStock == null ? BigDecimal.ZERO : minimumStock;

        String sql = baseSelect(priceColumn) + """
                WHERE (:onlyFacturable = false OR p.facturable_sunat = true)
                  AND (:onlyWithStock = false OR COALESCE(ps.quantity_on_hand, 0) >= :minimumStock)
                  AND (
                    :query = ''
                    OR p.sku ILIKE :like
                    OR p.name ILIKE :like
                    OR COALESCE(p.category, '') ILIKE :like
                    OR COALESCE(p.brand, '') ILIKE :like
                    OR COALESCE(p.model, '') ILIKE :like
                    OR COALESCE(p.factory_code, '') ILIKE :like
                    OR COALESCE(p.compatibility, '') ILIKE :like
                    OR COALESCE(p.barcode, '') ILIKE :like
                  )
                ORDER BY
                    CASE WHEN lower(p.sku) = lower(:query) THEN 0 ELSE 1 END,
                    CASE WHEN p.sku ILIKE :like THEN 1 ELSE 2 END,
                    CASE WHEN p.name ILIKE :like THEN 2 ELSE 3 END,
                    COALESCE(ps.quantity_on_hand, 0) DESC,
                    p.name ASC
                LIMIT :limit
                """;

        return jdbcClient.sql(sql)
                .param("query", normalizedQuery)
                .param("like", like)
                .param("priceList", normalizePriceList(priceList))
                .param("onlyFacturable", onlyFacturable)
                .param("onlyWithStock", onlyWithStock)
                .param("minimumStock", minStock)
                .param("limit", Math.max(1, limit))
                .query(mapper)
                .list();
    }

    @Override
    public Optional<WhatsAppProductSearchResult> findExactProduct(String query, String priceList, boolean onlyFacturable) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) return Optional.empty();
        String priceColumn = priceColumn(priceList);

        String sql = baseSelect(priceColumn) + """
                WHERE (:onlyFacturable = false OR p.facturable_sunat = true)
                  AND (
                    lower(p.sku) = lower(:query)
                    OR lower(COALESCE(p.factory_code, '')) = lower(:query)
                    OR lower(COALESCE(p.barcode, '')) = lower(:query)
                  )
                ORDER BY p.name ASC
                LIMIT 1
                """;

        return jdbcClient.sql(sql)
                .param("query", normalizedQuery)
                .param("priceList", normalizePriceList(priceList))
                .param("onlyFacturable", onlyFacturable)
                .query(mapper)
                .optional();
    }

    private String baseSelect(String priceColumn) {
        return """
                SELECT
                    p.id AS product_id,
                    p.sku,
                    p.name,
                    p.category,
                    p.brand,
                    p.model,
                    p.presentation,
                    p.warehouse_location,
                    p.manage_by_serial,
                    COALESCE(ps.quantity_on_hand, 0) AS stock_quantity,
                    COALESCE(%s, p.price_a, p.price_b, p.price_c, p.price_d, 0) AS selected_price,
                    :priceList AS selected_price_list,
                    (
                        SELECT pi.image_url
                        FROM product_image pi
                        WHERE pi.product_id = p.id
                        ORDER BY pi.is_main DESC, pi.position ASC, pi.id ASC
                        LIMIT 1
                    ) AS main_image_url
                FROM product p
                LEFT JOIN product_stock ps ON ps.product_id = p.id
                """.formatted(priceColumn);
    }

    private String priceColumn(String priceList) {
        return switch (normalizePriceList(priceList)) {
            case "B" -> "p.price_b";
            case "C" -> "p.price_c";
            case "D" -> "p.price_d";
            default -> "p.price_a";
        };
    }

    private String normalizePriceList(String priceList) {
        if (priceList == null) return "A";
        return switch (priceList.trim().toUpperCase()) {
            case "A", "B", "C", "D" -> priceList.trim().toUpperCase();
            default -> "A";
        };
    }
}
