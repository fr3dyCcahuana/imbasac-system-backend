package com.paulfernandosr.possystembackend.promotion.application;

import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.ProductImagePublicUrlService;
import com.paulfernandosr.possystembackend.promotion.domain.PromotionCampaign;
import com.paulfernandosr.possystembackend.promotion.domain.PromotionItem;
import com.paulfernandosr.possystembackend.promotion.infrastructure.adapter.input.dto.PromotionCampaignRequest;
import com.paulfernandosr.possystembackend.promotion.infrastructure.adapter.input.dto.PromotionItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PromotionCampaignService {

    private final JdbcClient jdbcClient;
    private final ProductImagePublicUrlService imageUrlService;

    public List<PromotionCampaign> findAll(String status, String kind) {
        String statusFilter = clean(status);
        String kindFilter = valueOrNull(kind);
        String sql = """
                SELECT id, code, name, slug, kind, type, title, subtitle, description, badge,
                       hero_image_url, banner_image_url, discount_type, discount_value,
                       starts_at, ends_at, status, priority, channel, featured
                FROM promotion_campaign
                WHERE (?::text IS NULL OR status = ?)
                  AND (?::text IS NULL OR kind = ?)
                ORDER BY priority DESC, updated_at DESC, id DESC
                """;
        return jdbcClient.sql(sql)
                .params(statusFilter, statusFilter, kindFilter, kindFilter)
                .query((rs, rowNum) -> mapCampaign(rs.getLong("id")))
                .list();
    }

    public List<PromotionCampaign> findActiveForLanding() {
        String sql = """
                SELECT id
                FROM promotion_campaign
                WHERE status = 'ACTIVE'
                  AND channel IN ('LANDING', 'BOTH')
                  AND (starts_at IS NULL OR starts_at <= NOW())
                  AND (ends_at IS NULL OR ends_at >= NOW())
                ORDER BY priority DESC, id DESC
                LIMIT 12
                """;
        return jdbcClient.sql(sql)
                .query(Long.class)
                .list()
                .stream()
                .map(this::mapCampaign)
                .toList();
    }

    public PromotionCampaign findById(Long id) {
        return mapCampaign(id);
    }

    @Transactional
    public PromotionCampaign create(PromotionCampaignRequest request) {
        Long id = jdbcClient.sql("""
                INSERT INTO promotion_campaign (
                    code, name, slug, kind, type, title, subtitle, description, badge,
                    hero_image_url, banner_image_url, discount_type, discount_value,
                    starts_at, ends_at, status, priority, channel, featured
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """)
                .params(
                        upper(request.code()),
                        required(request.name(), "name"),
                        slug(request.slug(), request.name()),
                        value(request.kind(), "PROMOTION"),
                        value(request.type(), defaultType(request.kind())),
                        required(request.title(), "title"),
                        clean(request.subtitle()),
                        clean(request.description()),
                        clean(request.badge()),
                        clean(request.heroImageUrl()),
                        clean(request.bannerImageUrl()),
                        value(request.discountType(), "PERCENT"),
                        request.discountValue() == null ? java.math.BigDecimal.ZERO : request.discountValue(),
                        ts(request.startsAt()),
                        ts(request.endsAt()),
                        value(request.status(), "DRAFT"),
                        request.priority() == null ? 0 : request.priority(),
                        value(request.channel(), "LANDING"),
                        request.featured()
                )
                .query(Long.class)
                .single();

        replaceItems(id, request.items());
        return mapCampaign(id);
    }

    @Transactional
    public PromotionCampaign update(Long id, PromotionCampaignRequest request) {
        jdbcClient.sql("""
                UPDATE promotion_campaign
                   SET code = ?,
                       name = ?,
                       slug = ?,
                       kind = ?,
                       type = ?,
                       title = ?,
                       subtitle = ?,
                       description = ?,
                       badge = ?,
                       hero_image_url = ?,
                       banner_image_url = ?,
                       discount_type = ?,
                       discount_value = ?,
                       starts_at = ?,
                       ends_at = ?,
                       status = ?,
                       priority = ?,
                       channel = ?,
                       featured = ?,
                       updated_at = NOW()
                 WHERE id = ?
                """)
                .params(
                        upper(request.code()),
                        required(request.name(), "name"),
                        slug(request.slug(), request.name()),
                        value(request.kind(), "PROMOTION"),
                        value(request.type(), defaultType(request.kind())),
                        required(request.title(), "title"),
                        clean(request.subtitle()),
                        clean(request.description()),
                        clean(request.badge()),
                        clean(request.heroImageUrl()),
                        clean(request.bannerImageUrl()),
                        value(request.discountType(), "PERCENT"),
                        request.discountValue() == null ? java.math.BigDecimal.ZERO : request.discountValue(),
                        ts(request.startsAt()),
                        ts(request.endsAt()),
                        value(request.status(), "DRAFT"),
                        request.priority() == null ? 0 : request.priority(),
                        value(request.channel(), "LANDING"),
                        request.featured(),
                        id
                )
                .update();

        replaceItems(id, request.items());
        return mapCampaign(id);
    }

    @Transactional
    public void delete(Long id) {
        jdbcClient.sql("DELETE FROM promotion_campaign WHERE id = ?")
                .param(id)
                .update();
    }

    private void replaceItems(Long promotionId, List<PromotionItemRequest> items) {
        jdbcClient.sql("DELETE FROM promotion_campaign_item WHERE promotion_id = ?")
                .param(promotionId)
                .update();

        if (items == null || items.isEmpty()) {
            return;
        }

        for (PromotionItemRequest item : items) {
            if (item.productId() == null) continue;
            jdbcClient.sql("""
                    INSERT INTO promotion_campaign_item (
                        promotion_id, product_id, role, quantity, promo_price,
                        discount_percent, required, sort_order, stock_limit
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    .params(
                            promotionId,
                            item.productId(),
                            value(item.role(), "PRIMARY"),
                            item.quantity() == null ? java.math.BigDecimal.ONE : item.quantity(),
                            item.promoPrice(),
                            item.discountPercent(),
                            item.required() == null || item.required(),
                            item.sortOrder() == null ? 0 : item.sortOrder(),
                            item.stockLimit()
                    )
                    .update();
        }
    }

    private PromotionCampaign mapCampaign(Long id) {
        PromotionCampaign base = jdbcClient.sql("""
                SELECT id, code, name, slug, kind, type, title, subtitle, description, badge,
                       hero_image_url, banner_image_url, discount_type, discount_value,
                       starts_at, ends_at, status, priority, channel, featured
                FROM promotion_campaign
                WHERE id = ?
                """)
                .param(id)
                .query((rs, rowNum) -> new PromotionCampaign(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("kind"),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("subtitle"),
                        rs.getString("description"),
                        rs.getString("badge"),
                        imageUrlService.toPublicUrl(rs.getString("hero_image_url")),
                        imageUrlService.toPublicUrl(rs.getString("banner_image_url")),
                        rs.getString("discount_type"),
                        rs.getBigDecimal("discount_value"),
                        toLocalDateTime(rs.getTimestamp("starts_at")),
                        toLocalDateTime(rs.getTimestamp("ends_at")),
                        rs.getString("status"),
                        rs.getInt("priority"),
                        rs.getString("channel"),
                        rs.getBoolean("featured"),
                        List.<PromotionItem>of()
                ))
                .single();

        List<PromotionItem> items = findItems(id);
        return new PromotionCampaign(
                base.id(), base.code(), base.name(), base.slug(), base.kind(), base.type(), base.title(),
                base.subtitle(), base.description(), base.badge(), base.heroImageUrl(),
                base.bannerImageUrl(), base.discountType(), base.discountValue(), base.startsAt(),
                base.endsAt(), base.status(), base.priority(), base.channel(), base.featured(), items
        );
    }

    private List<PromotionItem> findItems(Long promotionId) {
        String sql = """
                SELECT
                    pci.id,
                    pci.product_id,
                    p.sku,
                    p.name,
                    p.brand,
                    p.model,
                    p.category,
                    pci.role,
                    pci.quantity,
                    p.price_a,
                    pci.promo_price,
                    pci.discount_percent,
                    pci.required,
                    pci.sort_order,
                    CASE
                        WHEN p.manage_by_serial = TRUE THEN COALESCE(su.serial_qty, 0)
                        ELSE COALESCE(ps.quantity_on_hand, 0)
                    END AS stock_on_hand,
                    img.image_url
                FROM promotion_campaign_item pci
                JOIN product p ON p.id = pci.product_id
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
                WHERE pci.promotion_id = ?
                ORDER BY pci.sort_order ASC, pci.id ASC
                """;
        return jdbcClient.sql(sql)
                .param(promotionId)
                .query((rs, rowNum) -> new PromotionItem(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getString("category"),
                        rs.getString("role"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("price_a"),
                        rs.getBigDecimal("promo_price"),
                        rs.getBigDecimal("discount_percent"),
                        rs.getBoolean("required"),
                        rs.getInt("sort_order"),
                        rs.getBigDecimal("stock_on_hand"),
                        imageUrlService.toPublicUrl(rs.getString("image_url"))
                ))
                .list();
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static String value(String value, String fallback) {
        String clean = clean(value);
        return clean == null ? fallback : clean.toUpperCase(Locale.ROOT);
    }

    private static String valueOrNull(String value) {
        String clean = clean(value);
        return clean == null ? null : clean.toUpperCase(Locale.ROOT);
    }

    private static String defaultType(String kind) {
        return "OFFER".equalsIgnoreCase(clean(kind)) ? "PRODUCT_DISCOUNT" : "COMBO";
    }

    private static String upper(String value) {
        return required(value, "code").toUpperCase(Locale.ROOT);
    }

    private static String required(String value, String field) {
        String clean = clean(value);
        if (clean == null) throw new IllegalArgumentException("El campo " + field + " es obligatorio.");
        return clean;
    }

    private static String slug(String slug, String name) {
        String clean = clean(slug);
        if (clean != null) return clean.toLowerCase(Locale.ROOT);
        return required(name, "name").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private static Timestamp ts(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
