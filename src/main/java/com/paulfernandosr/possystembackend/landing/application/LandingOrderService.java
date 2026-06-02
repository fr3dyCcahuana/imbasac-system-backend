package com.paulfernandosr.possystembackend.landing.application;

import com.paulfernandosr.possystembackend.landing.domain.LandingOrder;
import com.paulfernandosr.possystembackend.landing.domain.LandingOrderItem;
import com.paulfernandosr.possystembackend.landing.infrastructure.adapter.input.dto.LandingOrderItemRequest;
import com.paulfernandosr.possystembackend.landing.infrastructure.adapter.input.dto.LandingOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LandingOrderService {

    private final JdbcClient jdbcClient;

    @Transactional
    public LandingOrder create(LandingOrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Debe enviar al menos un producto.");
        }

        Series series = nextSeries();
        List<LandingOrderItem> items = request.items().stream()
                .map(this::resolveItem)
                .toList();

        BigDecimal subtotal = items.stream()
                .map(item -> item.unitPrice().multiply(item.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = items.stream()
                .map(LandingOrderItem::discountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = items.stream()
                .map(LandingOrderItem::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Long orderId = jdbcClient.sql("""
                INSERT INTO landing_order (
                    series, number, customer_name, customer_document_type,
                    customer_document_number, phone, email,
                    department_code, department_name, province_code, province_name,
                    district_code, district_name, address,
                    subtotal, discount_total, total, notes
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """)
                .params(
                        series.series(),
                        series.number(),
                        clean(request.customerName()),
                        clean(request.customerDocumentType()),
                        clean(request.customerDocumentNumber()),
                        clean(request.phone()),
                        clean(request.email()),
                        clean(request.departmentCode()),
                        clean(request.departmentName()),
                        clean(request.provinceCode()),
                        clean(request.provinceName()),
                        clean(request.districtCode()),
                        clean(request.districtName()),
                        clean(request.address()),
                        subtotal,
                        discount,
                        total,
                        clean(request.notes())
                )
                .query(Long.class)
                .single();

        for (LandingOrderItem item : items) {
            jdbcClient.sql("""
                    INSERT INTO landing_order_item (
                        landing_order_id, promotion_id, product_id, description,
                        quantity, unit_price, discount_amount, total
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    .params(
                            orderId,
                            item.promotionId(),
                            item.productId(),
                            item.description(),
                            item.quantity(),
                            item.unitPrice(),
                            item.discountAmount(),
                            item.total()
                    )
                    .update();
        }

        return new LandingOrder(orderId, series.series(), series.number(), request.customerName(), request.phone(), "NEW", total, items);
    }

    private Series nextSeries() {
        Series row = jdbcClient.sql("""
                SELECT id, series, next_number
                FROM document_series
                WHERE doc_type = 'LANDING_ORDER'
                  AND enabled = TRUE
                ORDER BY id ASC
                LIMIT 1
                FOR UPDATE
                """)
                .query((rs, rowNum) -> new Series(
                        rs.getLong("id"),
                        rs.getString("series"),
                        rs.getLong("next_number")
                ))
                .single();

        jdbcClient.sql("UPDATE document_series SET next_number = next_number + 1 WHERE id = ?")
                .param(row.id())
                .update();
        return row;
    }

    private LandingOrderItem resolveItem(LandingOrderItemRequest request) {
        ProductLine product = jdbcClient.sql("""
                SELECT id, name, price_a
                FROM product
                WHERE id = ?
                """)
                .param(request.productId())
                .query((rs, rowNum) -> new ProductLine(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getBigDecimal("price_a")
                ))
                .single();

        BigDecimal quantity = request.quantity() == null ? BigDecimal.ONE : request.quantity();
        BigDecimal unitPrice = request.unitPrice() == null ? product.price() : request.unitPrice();
        BigDecimal discount = request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount();
        BigDecimal total = unitPrice.multiply(quantity).subtract(discount).setScale(2, RoundingMode.HALF_UP);

        return new LandingOrderItem(
                product.id(),
                request.promotionId(),
                product.name(),
                quantity,
                unitPrice,
                discount,
                total
        );
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private record Series(Long id, String series, Long number) {
    }

    private record ProductLine(Long id, String name, BigDecimal price) {
    }
}
