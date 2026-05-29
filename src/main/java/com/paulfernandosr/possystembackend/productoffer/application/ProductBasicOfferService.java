package com.paulfernandosr.possystembackend.productoffer.application;

import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.ProductImagePublicUrlService;
import com.paulfernandosr.possystembackend.productoffer.domain.ProductBasicOffer;
import com.paulfernandosr.possystembackend.productoffer.domain.ProductBasicOfferItem;
import com.paulfernandosr.possystembackend.productoffer.infrastructure.adapter.input.dto.ProductBasicOfferItemRequest;
import com.paulfernandosr.possystembackend.productoffer.infrastructure.adapter.input.dto.ProductBasicOfferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductBasicOfferService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "PAUSED", "EXPIRED");

    private final JdbcClient jdbcClient;
    private final ProductImagePublicUrlService imageUrlService;

    public List<ProductBasicOffer> findAll(String status) {
        String normalizedStatus = clean(status);
        if ("ALL".equalsIgnoreCase(normalizedStatus)) {
            normalizedStatus = null;
        }
        if (normalizedStatus != null) {
            normalizedStatus = status(normalizedStatus);
        }

        return jdbcClient.sql("""
                SELECT id
                FROM product_basic_offer
                WHERE (?::text IS NULL OR status = ?)
                ORDER BY starts_at DESC, updated_at DESC, id DESC
                """)
                .params(normalizedStatus, normalizedStatus)
                .query(Long.class)
                .list()
                .stream()
                .map(this::findById)
                .toList();
    }

    public ProductBasicOffer findById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id de oferta es obligatorio.");
        }

        ProductBasicOffer base = jdbcClient.sql("""
                SELECT id, code, name, starts_at, ends_at, status, created_at, updated_at
                FROM product_basic_offer
                WHERE id = ?
                """)
                .param(id)
                .query((rs, rowNum) -> new ProductBasicOffer(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getObject("starts_at", LocalDate.class),
                        rs.getObject("ends_at", LocalDate.class),
                        rs.getString("status"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("updated_at")),
                        List.of()
                ))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada."));

        return new ProductBasicOffer(
                base.id(),
                base.code(),
                base.name(),
                base.startsAt(),
                base.endsAt(),
                base.status(),
                base.createdAt(),
                base.updatedAt(),
                findItems(base.id())
        );
    }

    @Transactional
    public ProductBasicOffer create(ProductBasicOfferRequest request) {
        validate(request);

        Long id = jdbcClient.sql("""
                INSERT INTO product_basic_offer (code, name, starts_at, ends_at, status)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """)
                .params(
                        nextCode(),
                        required(request.name(), "nombre"),
                        request.startsAt(),
                        request.endsAt(),
                        status(request.status())
                )
                .query(Long.class)
                .single();

        replaceItems(id, request.items());
        return findById(id);
    }

    @Transactional
    public ProductBasicOffer update(Long id, ProductBasicOfferRequest request) {
        validate(request);

        int updated = jdbcClient.sql("""
                UPDATE product_basic_offer
                   SET name = ?,
                       starts_at = ?,
                       ends_at = ?,
                       status = ?,
                       updated_at = NOW()
                 WHERE id = ?
                """)
                .params(
                        required(request.name(), "nombre"),
                        request.startsAt(),
                        request.endsAt(),
                        status(request.status()),
                        id
                )
                .update();

        if (updated == 0) {
            throw new IllegalArgumentException("Oferta no encontrada.");
        }

        replaceItems(id, request.items());
        return findById(id);
    }

    @Transactional
    public void delete(Long id) {
        int deleted = jdbcClient.sql("DELETE FROM product_basic_offer WHERE id = ?")
                .param(id)
                .update();
        if (deleted == 0) {
            throw new IllegalArgumentException("Oferta no encontrada.");
        }
    }

    private void replaceItems(Long offerId, List<ProductBasicOfferItemRequest> items) {
        jdbcClient.sql("DELETE FROM product_basic_offer_item WHERE offer_id = ?")
                .param(offerId)
                .update();

        int index = 0;
        for (ProductBasicOfferItemRequest item : items) {
            int inserted = jdbcClient.sql("""
                    INSERT INTO product_basic_offer_item (
                        offer_id, product_id, min_quantity, offer_price,
                        regular_price_snapshot, sort_order
                    )
                    SELECT ?, p.id, ?, ?, COALESCE(p.price_a, 0), ?
                    FROM product p
                    WHERE p.id = ?
                    """)
                    .params(
                            offerId,
                            item.minQuantity(),
                            item.offerPrice(),
                            item.sortOrder() == null ? index : item.sortOrder(),
                            item.productId()
                    )
                    .update();

            if (inserted == 0) {
                throw new IllegalArgumentException("Producto no encontrado: " + item.productId());
            }
            index++;
        }
    }

    private List<ProductBasicOfferItem> findItems(Long offerId) {
        return jdbcClient.sql("""
                SELECT
                    pboi.id,
                    pboi.product_id,
                    p.sku,
                    p.name,
                    p.presentation,
                    p.brand,
                    p.model,
                    p.category,
                    p.compatibility,
                    COALESCE(pboi.regular_price_snapshot, p.price_a, 0) AS regular_price,
                    pboi.offer_price,
                    pboi.min_quantity,
                    pboi.sort_order,
                    CASE
                        WHEN p.manage_by_serial = TRUE THEN COALESCE(su.serial_qty, 0)
                        ELSE COALESCE(ps.quantity_on_hand, 0)
                    END AS stock_on_hand,
                    img.image_url
                FROM product_basic_offer_item pboi
                JOIN product p ON p.id = pboi.product_id
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
                WHERE pboi.offer_id = ?
                ORDER BY pboi.sort_order ASC, pboi.id ASC
                """)
                .param(offerId)
                .query((rs, rowNum) -> new ProductBasicOfferItem(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("presentation"),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getString("category"),
                        rs.getString("compatibility"),
                        rs.getBigDecimal("regular_price"),
                        rs.getBigDecimal("offer_price"),
                        rs.getBigDecimal("min_quantity"),
                        rs.getBigDecimal("stock_on_hand"),
                        imageUrlService.toPublicUrl(rs.getString("image_url")),
                        rs.getInt("sort_order")
                ))
                .list();
    }

    private void validate(ProductBasicOfferRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Datos de oferta son obligatorios.");
        }
        required(request.name(), "nombre");
        if (request.startsAt() == null || request.endsAt() == null) {
            throw new IllegalArgumentException("El rango de fechas es obligatorio.");
        }
        if (request.startsAt().isAfter(request.endsAt())) {
            throw new IllegalArgumentException("La fecha inicial no puede ser mayor que la fecha final.");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Agrega al menos un producto a la oferta.");
        }

        Set<Long> productIds = new HashSet<>();
        for (ProductBasicOfferItemRequest item : request.items()) {
            if (item.productId() == null || item.productId() <= 0) {
                throw new IllegalArgumentException("Producto de oferta es obligatorio.");
            }
            if (!productIds.add(item.productId())) {
                throw new IllegalArgumentException("Producto duplicado en la oferta: " + item.productId());
            }
            if (item.minQuantity() == null || item.minQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("La cantidad minima debe ser mayor a 0.");
            }
            if (item.offerPrice() == null || item.offerPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El precio de oferta debe ser mayor a 0.");
            }
        }
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static String required(String value, String field) {
        String clean = clean(value);
        if (clean == null) {
            throw new IllegalArgumentException("El campo " + field + " es obligatorio.");
        }
        return clean;
    }

    private static String status(String value) {
        String clean = clean(value);
        String status = clean == null ? "ACTIVE" : clean.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Estado de oferta invalido.");
        }
        return status;
    }

    private static String nextCode() {
        return ("PO-" + Long.toString(System.currentTimeMillis(), 36)).toUpperCase(Locale.ROOT);
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
