package com.paulfernandosr.possystembackend.landing.application;

import com.paulfernandosr.possystembackend.landing.domain.LandingContent;
import com.paulfernandosr.possystembackend.landing.domain.LandingProduct;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.ProductImagePublicUrlService;
import com.paulfernandosr.possystembackend.promotion.application.PromotionCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LandingContentService {

    private final JdbcClient jdbcClient;
    private final ProductImagePublicUrlService imageUrlService;
    private final PromotionCampaignService promotionCampaignService;

    public LandingContent getContent() {
        List<LandingProduct> products = findFeaturedProducts();
        List<String> categories = findCategories();

        return new LandingContent(
                "Repuestos, motos y accesorios para vender mas rapido",
                "Catalogo actualizado, promociones listas para WhatsApp y atencion directa para clientes que buscan stock real.",
                "51999999999",
                "Hola IMBASAC, quiero cotizar repuestos y accesorios.",
                products,
                promotionCampaignService.findActiveForLanding(),
                categories.isEmpty()
                        ? List.of("Repuestos Bajaj", "Accesorios", "Motos", "Lubricantes")
                        : categories
        );
    }

    private List<LandingProduct> findFeaturedProducts() {
        String sql = """
                SELECT
                    p.id AS product_id,
                    p.sku,
                    p.name,
                    p.brand,
                    p.model,
                    p.category,
                    p.price_a,
                    CASE
                        WHEN p.manage_by_serial = TRUE THEN COALESCE(su.serial_qty, 0)
                        ELSE COALESCE(ps.quantity_on_hand, 0)
                    END AS stock_on_hand,
                    img.image_url
                FROM product p
                LEFT JOIN product_stock ps ON ps.product_id = p.id
                LEFT JOIN (
                    SELECT product_id, COUNT(*)::numeric(14,3) AS serial_qty
                    FROM product_serial_unit
                    WHERE status = 'EN_ALMACEN'
                    GROUP BY product_id
                ) su ON su.product_id = p.id
                LEFT JOIN LATERAL (
                    SELECT image_url
                    FROM product_image
                    WHERE product_id = p.id
                    ORDER BY is_main DESC, position ASC, id ASC
                    LIMIT 1
                ) img ON TRUE
                WHERE COALESCE(p.affects_stock, TRUE) = TRUE
                ORDER BY
                    CASE
                        WHEN p.name ILIKE '%BAJAJ%' OR p.brand ILIKE '%BAJAJ%' THEN 0
                        WHEN p.category ILIKE '%REPUEST%' THEN 1
                        ELSE 2
                    END,
                    stock_on_hand DESC,
                    p.updated_at DESC
                LIMIT 6
                """;

        return jdbcClient.sql(sql)
                .query((rs, rowNum) -> new LandingProduct(
                        rs.getLong("product_id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getString("category"),
                        rs.getBigDecimal("price_a"),
                        rs.getBigDecimal("stock_on_hand"),
                        imageUrlService.toPublicUrl(rs.getString("image_url"))
                ))
                .list();
    }

    private List<String> findCategories() {
        String sql = """
                SELECT category
                FROM product
                WHERE category IS NOT NULL AND BTRIM(category) <> ''
                GROUP BY category
                ORDER BY COUNT(*) DESC, category ASC
                LIMIT 6
                """;

        return jdbcClient.sql(sql)
                .query(String.class)
                .list();
    }
}
