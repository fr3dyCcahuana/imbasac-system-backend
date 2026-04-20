package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.ProductPurchasePrice;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductPurchasePriceRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductPurchasePriceRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresProductPurchasePriceRepository implements ProductPurchasePriceRepository {

    private final JdbcClient jdbcClient;

    @Override
    public List<ProductPurchasePrice> findLatest10ByProductId(Long productId) {
        String sql = """
            SELECT
                pi.id AS purchase_item_id,
                pi.purchase_id,
                pi.line_number,
                p.issue_date,
                p.entry_date,
                p.document_type,
                p.document_series,
                p.document_number,
                p.currency,
                p.payment_type,
                p.supplier_ruc,
                p.supplier_business_name,
                p.supplier_address,
                pi.quantity,
                pi.unit_cost,
                pi.discount_percent,
                pi.discount_amount,
                pi.igv_rate,
                pi.igv_amount,
                pi.freight_allocated,
                pi.total_cost,
                pi.lot_code,
                pi.expiration_date,
                pi.created_at
            FROM purchase_item pi
            INNER JOIN purchase p
                    ON p.id = pi.purchase_id
            WHERE pi.product_id = ?
              AND COALESCE(p.status, 'REGISTRADA') <> 'ANULADA'
            ORDER BY
                COALESCE(p.entry_date, p.issue_date) DESC,
                p.issue_date DESC,
                pi.created_at DESC,
                pi.id DESC
            LIMIT 10
            """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(new ProductPurchasePriceRowMapper())
                .list();
    }
}
