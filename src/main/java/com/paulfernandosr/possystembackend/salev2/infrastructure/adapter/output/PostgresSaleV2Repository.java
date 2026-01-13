package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType;
import com.paulfernandosr.possystembackend.salev2.domain.model.PriceList;
import com.paulfernandosr.possystembackend.salev2.domain.model.TaxStatus;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresSaleV2Repository implements SaleV2Repository {

    private final JdbcClient jdbcClient;

    @Override
    public Long insertSale(
            Long stationId,
            Long saleSessionId,
            Long createdBy,
            DocType docType,
            String series,
            Long number,
            LocalDate issueDate,
            String currency,
            BigDecimal exchangeRate,
            PriceList priceList,
            Long customerId,
            String customerDocType,
            String customerDocNumber,
            String customerName,
            String customerAddress,
            TaxStatus taxStatus,
            String taxReason,
            BigDecimal igvRate,
            PaymentType paymentType,
            Integer creditDays,
            LocalDate dueDate,
            String notes
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        String sql = """
                INSERT INTO sale(
                    station_id,
                    sale_session_id,
                    created_by,
                    doc_type,
                    series,
                    number,
                    issue_date,
                    currency,
                    exchange_rate,
                    price_list,
                    customer_id,
                    customer_doc_type,
                    customer_doc_number,
                    customer_name,
                    customer_address,
                    tax_status,
                    tax_reason,
                    igv_rate,
                    payment_type,
                    credit_days,
                    due_date,
                    notes
                )
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;

        jdbcClient.sql(sql)
                .params(
                        stationId,
                        saleSessionId,
                        createdBy,
                        docType.name(),
                        series,
                        number,
                        issueDate,
                        currency,
                        exchangeRate,
                        priceList.name(),
                        customerId,
                        customerDocType,
                        customerDocNumber,
                        customerName,
                        customerAddress,
                        taxStatus.name(),
                        taxReason,
                        igvRate,
                        paymentType.name(),
                        creditDays,
                        dueDate,
                        notes
                )
                .update(keyHolder, "id");

        return Optional.ofNullable(keyHolder.getKey())
                .map(Number::longValue)
                .orElseThrow();
    }

    @Override
    public Long insertSaleItem(
            Long saleId,
            int lineNumber,
            Long productId,
            String sku,
            String description,
            String presentation,
            BigDecimal factor,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            String lineKind,
            String giftReason,
            boolean facturableSunat,
            boolean affectsStock,
            boolean visibleInDocument,
            BigDecimal unitCostSnapshot,
            BigDecimal totalCostSnapshot,
            BigDecimal revenueTotal
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        String sql = """
                INSERT INTO sale_item(
                    sale_id,
                    line_number,
                    product_id,
                    sku,
                    description,
                    presentation,
                    factor,
                    quantity,
                    unit_price,
                    discount_percent,
                    discount_amount,
                    line_kind,
                    gift_reason,
                    facturable_sunat,
                    affects_stock,
                    visible_in_document,
                    unit_cost_snapshot,
                    total_cost_snapshot,
                    revenue_total
                )
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;

        jdbcClient.sql(sql)
                .params(
                        saleId,
                        lineNumber,
                        productId,
                        sku,
                        description,
                        presentation,
                        factor,
                        quantity,
                        unitPrice,
                        discountPercent,
                        discountAmount,
                        lineKind,
                        giftReason,
                        facturableSunat,
                        affectsStock,
                        visibleInDocument,
                        unitCostSnapshot,
                        totalCostSnapshot,
                        revenueTotal
                )
                .update(keyHolder, "id");

        return Optional.ofNullable(keyHolder.getKey())
                .map(Number::longValue)
                .orElseThrow();
    }

    @Override
    public void updateTotals(Long saleId,
                             BigDecimal subtotal,
                             BigDecimal discountTotal,
                             BigDecimal igvAmount,
                             BigDecimal total,
                             BigDecimal giftCostTotal) {

        String sql = """
                UPDATE sale
                   SET subtotal = ?,
                       discount_total = ?,
                       igv_amount = ?,
                       total = ?,
                       gift_cost_total = ?,
                       updated_at = NOW()
                 WHERE id = ?
                """;

        jdbcClient.sql(sql)
                .params(subtotal, discountTotal, igvAmount, total, giftCostTotal, saleId)
                .update();
    }

    @Override
    public void insertPayment(Long saleId, String method, BigDecimal amount) {
        String sql = """
                INSERT INTO sale_payment(sale_id, method, amount)
                VALUES (?,?,?)
                """;

        jdbcClient.sql(sql)
                .params(saleId, method, amount)
                .update();
    }

    @Override
    public void addIncomeToOpenSession(Long userId, BigDecimal salesIncome, BigDecimal totalDiscount) {
        String sql = """
                UPDATE sale_sessions
                   SET sales_income = sales_income + ?,
                       total_discount = total_discount + ?
                 WHERE user_id = ?
                   AND closed_at IS NULL
                """;

        jdbcClient.sql(sql)
                .params(salesIncome, totalDiscount, userId)
                .update();
    }
}
