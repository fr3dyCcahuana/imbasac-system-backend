package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.reports.domain.ReportGroupBy;
import com.paulfernandosr.possystembackend.reports.domain.port.output.ReportsRepository;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProductSaleDetailResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProductTopResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProfitPeriodResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SalesAggResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SalesProfitChannelPointResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceCategoryDetailResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerCommissionConfigResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceDetailResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceProductDetailResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceRowResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SunatComparisonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostgresReportsRepository implements ReportsRepository {

    private final JdbcClient jdbcClient;

    @Override
    public SellerCommissionConfigResponse findSellerCommissionConfig(Long sellerId) {
        String sql = """
                SELECT
                  u.id AS seller_id,
                  COALESCE(c.monthly_goal, 50000.00) AS monthly_goal,
                  COALESCE(c.bajaj_rate, 0.005000) AS bajaj_rate,
                  COALESCE(c.ktm_rate, 0.003000) AS ktm_rate,
                  COALESCE(c.imba_rate, 0.000200) AS imba_rate,
                  COALESCE(c.bon_rate, 0.000200) AS bon_rate,
                  (c.seller_id IS NOT NULL) AS customized
                FROM users u
                LEFT JOIN seller_commission_config c ON c.seller_id = u.id
                WHERE u.id = :sellerId
                """;
        return jdbcClient.sql(sql)
                .param("sellerId", sellerId)
                .query((rs, rowNum) -> SellerCommissionConfigResponse.builder()
                        .sellerId(rs.getLong("seller_id"))
                        .monthlyGoal(rs.getBigDecimal("monthly_goal"))
                        .bajajRate(rs.getBigDecimal("bajaj_rate"))
                        .ktmRate(rs.getBigDecimal("ktm_rate"))
                        .imbaRate(rs.getBigDecimal("imba_rate"))
                        .bonRate(rs.getBigDecimal("bon_rate"))
                        .customized(rs.getBoolean("customized"))
                        .build())
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("No se encontro el usuario vendedor."));
    }

    @Override
    public SellerCommissionConfigResponse saveSellerCommissionConfig(SellerCommissionConfigResponse config) {
        String sql = """
                INSERT INTO seller_commission_config
                  (seller_id, monthly_goal, bajaj_rate, ktm_rate, imba_rate, bon_rate)
                VALUES
                  (:sellerId, :monthlyGoal, :bajajRate, :ktmRate, :imbaRate, :bonRate)
                ON CONFLICT (seller_id) DO UPDATE SET
                  monthly_goal = EXCLUDED.monthly_goal,
                  bajaj_rate = EXCLUDED.bajaj_rate,
                  ktm_rate = EXCLUDED.ktm_rate,
                  imba_rate = EXCLUDED.imba_rate,
                  bon_rate = EXCLUDED.bon_rate,
                  updated_at = now()
                RETURNING seller_id, monthly_goal, bajaj_rate, ktm_rate, imba_rate, bon_rate
                """;
        return jdbcClient.sql(sql)
                .param("sellerId", config.getSellerId())
                .param("monthlyGoal", config.getMonthlyGoal())
                .param("bajajRate", config.getBajajRate())
                .param("ktmRate", config.getKtmRate())
                .param("imbaRate", config.getImbaRate())
                .param("bonRate", config.getBonRate())
                .query((rs, rowNum) -> SellerCommissionConfigResponse.builder()
                        .sellerId(rs.getLong("seller_id"))
                        .monthlyGoal(rs.getBigDecimal("monthly_goal"))
                        .bajajRate(rs.getBigDecimal("bajaj_rate"))
                        .ktmRate(rs.getBigDecimal("ktm_rate"))
                        .imbaRate(rs.getBigDecimal("imba_rate"))
                        .bonRate(rs.getBigDecimal("bon_rate"))
                        .customized(true)
                        .build())
                .single();
    }

    @Override
    public List<ProfitPeriodResponse> findProfit(LocalDate from, LocalDate to, ReportGroupBy groupBy) {
        String sql = """
                WITH report_rows AS (
                  %s
                )
                SELECT
                  %s AS period_start,
                  COALESCE(SUM(total_sales), 0) AS revenue_net_igv,
                  COALESCE(SUM(total_cost), 0) AS total_cogs,
                  COALESCE(SUM(gross_profit), 0) AS gross_profit
                FROM report_rows
                GROUP BY period_start
                ORDER BY period_start
                """.formatted(commercialRowsCte(), periodExpression("period_date", groupBy));

        return jdbcClient.sql(sql)
                .params(from, to, from, to, from, to)
                .query((rs, rowNum) -> ProfitPeriodResponse.builder()
                        .periodStart(rs.getObject("period_start", LocalDate.class))
                        .revenueNetIgv(rs.getBigDecimal("revenue_net_igv"))
                        .totalCogs(rs.getBigDecimal("total_cogs"))
                        .grossProfit(rs.getBigDecimal("gross_profit"))
                        .build())
                .list();
    }

    @Override
    public List<SalesAggResponse> findSalesTotal(LocalDate from, LocalDate to, ReportGroupBy groupBy) {
        String sql = """
                WITH report_rows AS (
                  %s
                )
                SELECT
                  %s AS period_start,
                  COALESCE(SUM(total_sales), 0) AS total_sales,
                  COUNT(*) AS count_sales
                FROM report_rows
                GROUP BY period_start
                ORDER BY period_start
                """.formatted(commercialRowsCte(), periodExpression("period_date", groupBy));

        return jdbcClient.sql(sql)
                .params(from, to, from, to, from, to)
                .query((rs, rowNum) -> SalesAggResponse.builder()
                        .periodStart(rs.getObject("period_start", LocalDate.class))
                        .totalSales(rs.getBigDecimal("total_sales"))
                        .countSales(rs.getLong("count_sales"))
                        .build())
                .list();
    }

    @Override
    public List<ProductTopResponse> findProductsTop(LocalDate from, LocalDate to, String sortBy, int limit) {
        String orderBy = switch (sortBy) {
            case "REVENUE" -> "total_sales";
            case "PROFIT" -> "total_profit";
            default -> "total_qty";
        };

        String sql = """
                WITH product_rows AS (
                  %s
                ),
                product_stock_available AS (
                  SELECT
                    p.id AS product_id,
                    CASE
                      WHEN p.manage_by_serial = TRUE THEN COALESCE(serial_stock.stock_qty, 0)
                      ELSE COALESCE(ps.quantity_on_hand, 0)
                    END AS stock_available
                  FROM product p
                  LEFT JOIN product_stock ps ON ps.product_id = p.id
                  LEFT JOIN (
                    SELECT product_id, COUNT(*)::numeric AS stock_qty
                    FROM product_serial_unit
                    WHERE status = 'EN_ALMACEN'
                    GROUP BY product_id
                  ) serial_stock ON serial_stock.product_id = p.id
                )
                SELECT
                  pr.product_id,
                  pr.product_sku,
                  pr.product_name,
                  COALESCE(MAX(NULLIF(TRIM(pr.brand), '')), '') AS brand,
                  COALESCE(MAX(NULLIF(TRIM(pr.category), '')), '') AS category,
                  COALESCE(MAX(NULLIF(TRIM(pr.model), '')), '') AS model,
                  COALESCE(SUM(pr.quantity), 0) AS total_qty,
                  COALESCE(SUM(pr.total_sales), 0) AS total_sales,
                  COALESCE(SUM(pr.total_profit), 0) AS total_profit,
                  COALESCE(MAX(psa.stock_available), 0) AS stock_available,
                  COUNT(DISTINCT pr.source || '-' || pr.doc_id) AS count_sales
                FROM product_rows pr
                LEFT JOIN product_stock_available psa ON psa.product_id = pr.product_id
                GROUP BY pr.product_id, pr.product_sku, pr.product_name
                ORDER BY %s DESC, product_name ASC
                LIMIT ?
                """.formatted(commercialProductRowsCte(), orderBy);

        return jdbcClient.sql(sql)
                .params(from, to, from, to, from, to, limit)
                .query((rs, rowNum) -> ProductTopResponse.builder()
                        .productId(rs.getLong("product_id"))
                        .productSku(rs.getString("product_sku"))
                        .productName(rs.getString("product_name"))
                        .brand(rs.getString("brand"))
                        .category(rs.getString("category"))
                        .model(rs.getString("model"))
                        .totalQty(rs.getBigDecimal("total_qty"))
                        .totalSales(rs.getBigDecimal("total_sales"))
                        .totalProfit(rs.getBigDecimal("total_profit"))
                        .stockAvailable(rs.getBigDecimal("stock_available"))
                        .countSales(rs.getLong("count_sales"))
                        .build())
                .list();
    }

    @Override
    public List<ProductSaleDetailResponse> findProductSaleDetails(LocalDate from, LocalDate to, Long productId) {
        String sql = """
                WITH product_rows AS (
                  %s
                )
                SELECT
                  source,
                  CASE source
                    WHEN 'COUNTER_SALE' THEN 'Venta por ventanilla'
                    WHEN 'CONTRACT' THEN 'Contratos'
                    WHEN 'PROFORMA' THEN 'Proformas'
                    ELSE source
                  END AS source_label,
                  doc_id,
                  series,
                  number,
                  issue_date,
                  customer_name,
                  COALESCE(SUM(quantity), 0) AS quantity,
                  COALESCE(SUM(total_sales), 0) AS total_sales
                FROM product_rows
                WHERE product_id = ?
                GROUP BY source, doc_id, series, number, issue_date, customer_name
                ORDER BY issue_date DESC, source ASC, series ASC, number DESC
                LIMIT 100
                """.formatted(commercialProductRowsCte());

        return jdbcClient.sql(sql)
                .params(from, to, from, to, from, to, productId)
                .query((rs, rowNum) -> ProductSaleDetailResponse.builder()
                        .source(rs.getString("source"))
                        .sourceLabel(rs.getString("source_label"))
                        .documentId(rs.getLong("doc_id"))
                        .series(rs.getString("series"))
                        .number(rs.getObject("number") != null ? rs.getLong("number") : null)
                        .issueDate(rs.getObject("issue_date", LocalDate.class))
                        .customerName(rs.getString("customer_name"))
                        .quantity(rs.getBigDecimal("quantity"))
                        .totalSales(rs.getBigDecimal("total_sales"))
                        .build())
                .list();
    }

    @Override
    public Map<String, List<SalesProfitChannelPointResponse>> findSalesProfitByChannel(LocalDate from,
                                                                                       LocalDate to,
                                                                                       ReportGroupBy groupBy) {
        String sql = """
                WITH report_rows AS (
                  %s
                )
                SELECT
                  source,
                  %s AS period_start,
                  COALESCE(SUM(total_sales), 0) AS total_sales,
                  COALESCE(SUM(total_cost), 0) AS total_cost,
                  COALESCE(SUM(gross_profit), 0) AS gross_profit,
                  COUNT(*) AS count_sales
                FROM report_rows
                GROUP BY source, period_start
                ORDER BY source, period_start
                """.formatted(commercialRowsCte(), periodExpression("period_date", groupBy));

        List<ChannelPointRow> rows = jdbcClient.sql(sql)
                .params(from, to, from, to, from, to)
                .query((rs, rowNum) -> new ChannelPointRow(
                        rs.getString("source"),
                        SalesProfitChannelPointResponse.builder()
                                .periodStart(rs.getObject("period_start", LocalDate.class))
                                .totalSales(rs.getBigDecimal("total_sales"))
                                .totalCost(rs.getBigDecimal("total_cost"))
                                .grossProfit(rs.getBigDecimal("gross_profit"))
                                .countSales(rs.getLong("count_sales"))
                                .build()
                ))
                .list();

        return rows.stream().collect(Collectors.groupingBy(
                ChannelPointRow::source,
                LinkedHashMap::new,
                Collectors.mapping(ChannelPointRow::point, Collectors.toList())
        ));
    }

    @Override
    public List<SunatComparisonResponse> findSunatComparison(LocalDate from, LocalDate to) {
        String sql = """
                WITH comparison_rows AS (
                  SELECT
                    'COUNTER_SALE' AS source,
                    cs.id AS doc_id,
                    COALESCE(cs.total, 0) AS commercial_total,
                    CASE
                      WHEN COALESCE(sunat.sunat_total, 0) > 0
                        THEN COALESCE(cs.total, 0) * COALESCE(sunat.sunat_taxed_total, 0) / sunat.sunat_total
                      ELSE 0
                    END AS commercial_taxed_total,
                    CASE
                      WHEN COALESCE(sunat.sunat_total, 0) > 0
                        THEN COALESCE(cs.total, 0) * COALESCE(sunat.sunat_non_taxed_total, 0) / sunat.sunat_total
                      ELSE COALESCE(cs.total, 0)
                    END AS commercial_non_taxed_total,
                    COALESCE(sunat.sunat_total, 0) AS sunat_total,
                    COALESCE(sunat.sunat_taxed_total, 0) AS sunat_taxed_total,
                    COALESCE(sunat.sunat_non_taxed_total, 0) AS sunat_non_taxed_total
                  FROM counter_sale cs
                  JOIN (
                    SELECT
                      counter_sale_id,
                      COALESCE(SUM(emitted_revenue_total), 0) AS sunat_total,
                      COALESCE(SUM(CASE WHEN tax_status = 'GRAVADA' THEN emitted_revenue_total ELSE 0 END), 0) AS sunat_taxed_total,
                      COALESCE(SUM(CASE WHEN tax_status = 'GRAVADA' THEN 0 ELSE emitted_revenue_total END), 0) AS sunat_non_taxed_total
                    FROM (
                      SELECT
                        li.counter_sale_id,
                        li.emitted_revenue_total,
                        s.tax_status
                      FROM sale_counter_sale_sunat_link_item li
                      JOIN sale_counter_sale_sunat_link l
                        ON l.sale_id = li.sale_id
                       AND l.counter_sale_id = li.counter_sale_id
                      JOIN sale s ON s.id = li.sale_id
                      WHERE l.reservation_status = 'ACEPTADO'
                        AND s.status = 'EMITIDA'
                        AND s.sunat_status = 'ACEPTADO'

                      UNION ALL

                      SELECT
                        cl.counter_sale_id,
                        cl.emitted_revenue_total,
                        s.tax_status
                      FROM counter_sale_sunat_combo_line cl
                      JOIN counter_sale_sunat_combo c ON c.id = cl.combo_id
                      JOIN sale s ON s.id = c.generated_sale_id
                      WHERE c.combo_status = 'ACEPTADO'
                        AND s.status = 'EMITIDA'
                        AND s.sunat_status = 'ACEPTADO'
                    ) x
                    GROUP BY counter_sale_id
                  ) sunat ON sunat.counter_sale_id = cs.id
                  WHERE cs.status = 'EMITIDA'
                    AND cs.issue_date BETWEEN ? AND ?

                  UNION ALL

                  SELECT
                    'CONTRACT' AS source,
                    c.id AS doc_id,
                    COALESCE(c.total_amount, c.cash_price, 0) AS commercial_total,
                    CASE WHEN s.tax_status = 'GRAVADA' THEN COALESCE(c.total_amount, c.cash_price, 0) ELSE 0 END AS commercial_taxed_total,
                    CASE WHEN s.tax_status = 'GRAVADA' THEN 0 ELSE COALESCE(c.total_amount, c.cash_price, 0) END AS commercial_non_taxed_total,
                    COALESCE(s.total, 0) AS sunat_total,
                    CASE WHEN s.tax_status = 'GRAVADA' THEN COALESCE(s.total, 0) ELSE 0 END AS sunat_taxed_total,
                    CASE WHEN s.tax_status = 'GRAVADA' THEN 0 ELSE COALESCE(s.total, 0) END AS sunat_non_taxed_total
                  FROM contract c
                  JOIN sale s ON s.id = c.sale_id
                  WHERE c.status = 'FACTURADO'
                    AND s.status = 'EMITIDA'
                    AND s.sunat_status = 'ACEPTADO'
                    AND c.issue_date BETWEEN ? AND ?

                  UNION ALL

                  SELECT
                    'PROFORMA' AS source,
                    p.id AS doc_id,
                    COALESCE(p.total, 0) AS commercial_total,
                    CASE WHEN s.tax_status = 'GRAVADA' THEN COALESCE(p.total, 0) ELSE 0 END AS commercial_taxed_total,
                    CASE WHEN s.tax_status = 'GRAVADA' THEN 0 ELSE COALESCE(p.total, 0) END AS commercial_non_taxed_total,
                    COALESCE(s.total, 0) AS sunat_total,
                    CASE WHEN s.tax_status = 'GRAVADA' THEN COALESCE(s.total, 0) ELSE 0 END AS sunat_taxed_total,
                    CASE WHEN s.tax_status = 'GRAVADA' THEN 0 ELSE COALESCE(s.total, 0) END AS sunat_non_taxed_total
                  FROM proforma p
                  JOIN sale s ON s.id = p.converted_sale_id
                  WHERE p.status = 'CONVERTIDA'
                    AND s.status = 'EMITIDA'
                    AND s.sunat_status = 'ACEPTADO'
                    AND p.issue_date BETWEEN ? AND ?
                )
                SELECT
                  source,
                  CASE source
                    WHEN 'COUNTER_SALE' THEN 'Venta por ventanilla'
                    WHEN 'CONTRACT' THEN 'Contratos'
                    WHEN 'PROFORMA' THEN 'Proformas'
                    ELSE source
                  END AS label,
                  COALESCE(SUM(commercial_total), 0) AS commercial_total,
                  COALESCE(SUM(sunat_total), 0) AS sunat_total,
                  COALESCE(SUM(commercial_total - sunat_total), 0) AS difference,
                  COALESCE(SUM(commercial_taxed_total), 0) AS commercial_taxed_total,
                  COALESCE(SUM(commercial_non_taxed_total), 0) AS commercial_non_taxed_total,
                  COALESCE(SUM(sunat_taxed_total), 0) AS sunat_taxed_total,
                  COALESCE(SUM(sunat_non_taxed_total), 0) AS sunat_non_taxed_total,
                  COALESCE(SUM(commercial_taxed_total - sunat_taxed_total), 0) AS taxed_difference,
                  COALESCE(SUM(commercial_non_taxed_total - sunat_non_taxed_total), 0) AS non_taxed_difference,
                  GREATEST(COALESCE(SUM(commercial_taxed_total - sunat_taxed_total), 0), 0) * 18 / 118 AS estimated_tax_saving,
                  GREATEST(COALESCE(SUM(commercial_non_taxed_total - sunat_non_taxed_total), 0), 0) * 8 / 100 AS estimated_non_taxed_tax_saving,
                  GREATEST(COALESCE(SUM(commercial_taxed_total - sunat_taxed_total), 0), 0) * 18 / 118
                    + GREATEST(COALESCE(SUM(commercial_non_taxed_total - sunat_non_taxed_total), 0), 0) * 8 / 100 AS estimated_total_tax_saving,
                  COUNT(*) AS count_sales
                FROM comparison_rows
                GROUP BY source
                ORDER BY CASE source
                  WHEN 'COUNTER_SALE' THEN 1
                  WHEN 'CONTRACT' THEN 2
                  WHEN 'PROFORMA' THEN 3
                  ELSE 4
                END
                """;

        return jdbcClient.sql(sql)
                .params(from, to, from, to, from, to)
                .query((rs, rowNum) -> SunatComparisonResponse.builder()
                        .source(rs.getString("source"))
                        .label(rs.getString("label"))
                        .commercialTotal(rs.getBigDecimal("commercial_total"))
                        .sunatTotal(rs.getBigDecimal("sunat_total"))
                        .difference(rs.getBigDecimal("difference"))
                        .estimatedTaxSaving(rs.getBigDecimal("estimated_tax_saving"))
                        .commercialTaxedTotal(rs.getBigDecimal("commercial_taxed_total"))
                        .commercialNonTaxedTotal(rs.getBigDecimal("commercial_non_taxed_total"))
                        .sunatTaxedTotal(rs.getBigDecimal("sunat_taxed_total"))
                        .sunatNonTaxedTotal(rs.getBigDecimal("sunat_non_taxed_total"))
                        .taxedDifference(rs.getBigDecimal("taxed_difference"))
                        .nonTaxedDifference(rs.getBigDecimal("non_taxed_difference"))
                        .estimatedNonTaxedTaxSaving(rs.getBigDecimal("estimated_non_taxed_tax_saving"))
                        .estimatedTotalTaxSaving(rs.getBigDecimal("estimated_total_tax_saving"))
                        .countSales(rs.getLong("count_sales"))
                        .build())
                .list();
    }

    @Override
    public List<SellerPerformanceRowResponse> findSellerPerformance(LocalDate from,
                                                                    LocalDate to,
                                                                    ReportGroupBy groupBy) {
        String periodExpr = periodExpression("period_date", groupBy);
        String sql = """
                WITH seller_sales_rows AS (
                  %s
                ),
                seller_sales AS (
                  SELECT
                    %s AS period_start,
                    source,
                    seller_id,
                    seller_username,
                    seller_name,
                    COALESCE(SUM(total_sales), 0) AS total_sales,
                    COUNT(DISTINCT source || '-' || doc_id) AS count_sales
                  FROM seller_sales_rows
                  GROUP BY period_start, source, seller_id, seller_username, seller_name
                ),
                product_rows AS (
                  %s
                ),
                eligible_product_rows AS (
                  SELECT
                    classified.*,
                    CASE
                      WHEN search_text LIKE '%%BAJAJ%%' THEN 'BAJAJ'
                      WHEN search_text LIKE '%%KTM%%' THEN 'KTM'
                      WHEN search_text LIKE '%%BON%%' OR search_text LIKE '%%VON%%' THEN 'BON'
                      WHEN search_text LIKE '%%IMBA%%' OR search_text LIKE '%%IMBASAC%%' OR search_text LIKE '%%INVA%%' THEN 'IMBA'
                      ELSE NULL
                    END AS incentive_group,
                    CASE
                      WHEN search_text LIKE '%%BAJAJ%%' THEN COALESCE(config.bajaj_rate, 0.005000)
                      WHEN search_text LIKE '%%KTM%%' THEN COALESCE(config.ktm_rate, 0.003000)
                      WHEN search_text LIKE '%%BON%%' OR search_text LIKE '%%VON%%' THEN COALESCE(config.bon_rate, 0.000200)
                      WHEN search_text LIKE '%%IMBA%%' OR search_text LIKE '%%IMBASAC%%' OR search_text LIKE '%%INVA%%' THEN COALESCE(config.imba_rate, 0.000200)
                      ELSE 0
                    END AS commission_rate,
                    COALESCE(config.monthly_goal, 50000.00) AS monthly_goal
                  FROM (
                    SELECT
                      pr.*,
                      UPPER(CONCAT_WS(' ', COALESCE(pr.brand, ''), COALESCE(pr.category, ''), COALESCE(pr.product_name, ''))) AS search_text
                    FROM product_rows pr
                  ) classified
                  LEFT JOIN seller_commission_config config ON config.seller_id = classified.seller_id
                  WHERE search_text LIKE '%%BAJAJ%%'
                     OR search_text LIKE '%%KTM%%'
                     OR search_text LIKE '%%BON%%'
                     OR search_text LIKE '%%VON%%'
                     OR search_text LIKE '%%IMBA%%'
                     OR search_text LIKE '%%IMBASAC%%'
                     OR search_text LIKE '%%INVA%%'
                ),
                monthly_ranked AS (
                  SELECT
                    *,
                    DATE_TRUNC('month', period_date)::date AS month_start,
                    COALESCE(SUM(eligible_sales) OVER (
                      PARTITION BY seller_id, DATE_TRUNC('month', period_date)::date
                      ORDER BY period_date, source, doc_id, line_id
                      ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
                    ), 0) AS previous_month_sales,
                    SUM(eligible_sales) OVER (
                      PARTITION BY seller_id, DATE_TRUNC('month', period_date)::date
                      ORDER BY period_date, source, doc_id, line_id
                      ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                    ) AS current_month_sales
                  FROM eligible_product_rows
                ),
                commissioned AS (
                  SELECT
                    %s AS period_start,
                    source,
                    seller_id,
                    MAX(monthly_goal) AS monthly_goal,
                    COALESCE(SUM(eligible_sales), 0) AS eligible_sales,
                    COALESCE(SUM(
                      GREATEST(current_month_sales - monthly_goal, 0)
                      - GREATEST(previous_month_sales - monthly_goal, 0)
                    ), 0) AS commission_base,
                    COALESCE(SUM((
                      GREATEST(current_month_sales - monthly_goal, 0)
                      - GREATEST(previous_month_sales - monthly_goal, 0)
                    ) * commission_rate), 0) AS estimated_commission
                  FROM monthly_ranked
                  GROUP BY period_start, source, seller_id
                )
                SELECT
                  ss.period_start,
                  ss.source,
                  CASE ss.source
                    WHEN 'COUNTER_SALE' THEN 'Venta por ventanilla'
                    WHEN 'CONTRACT' THEN 'Contratos'
                    WHEN 'PROFORMA' THEN 'Proformas'
                    ELSE ss.source
                  END AS source_label,
                  ss.seller_id,
                  ss.seller_username,
                  ss.seller_name,
                  ss.total_sales,
                  ss.count_sales,
                  COALESCE(c.eligible_sales, 0) AS eligible_sales,
                  COALESCE(c.commission_base, 0) AS commission_base,
                  COALESCE(c.estimated_commission, 0) AS estimated_commission,
                  CASE
                    WHEN COALESCE(c.eligible_sales, 0) = 0 THEN 0
                    ELSE COALESCE(c.eligible_sales, 0) / COALESCE(c.monthly_goal, 50000.00) * 100
                  END AS score
                FROM seller_sales ss
                LEFT JOIN commissioned c
                  ON c.period_start = ss.period_start
                 AND c.source = ss.source
                 AND c.seller_id = ss.seller_id
                ORDER BY ss.total_sales DESC, ss.seller_name ASC, ss.source ASC, ss.period_start ASC
                """.formatted(commercialSellerRowsCte(), periodExpr, commercialSellerProductRowsCte(), periodExpr);

        return jdbcClient.sql(sql)
                .params(from, to, from, to, from, to, from, to, from, to, from, to)
                .query((rs, rowNum) -> SellerPerformanceRowResponse.builder()
                        .periodStart(rs.getObject("period_start", LocalDate.class))
                        .source(rs.getString("source"))
                        .sourceLabel(rs.getString("source_label"))
                        .sellerId(rs.getLong("seller_id"))
                        .sellerUsername(rs.getString("seller_username"))
                        .sellerName(rs.getString("seller_name"))
                        .totalSales(rs.getBigDecimal("total_sales"))
                        .countSales(rs.getLong("count_sales"))
                        .eligibleSales(rs.getBigDecimal("eligible_sales"))
                        .commissionBase(rs.getBigDecimal("commission_base"))
                        .estimatedCommission(rs.getBigDecimal("estimated_commission"))
                        .score(rs.getBigDecimal("score"))
                        .build())
                .list();
    }

    @Override
    public SellerPerformanceDetailResponse findSellerPerformanceDetail(LocalDate from,
                                                                       LocalDate to,
                                                                       Long sellerId,
                                                                       SellerCommissionConfigResponse config) {
        BigDecimal incentiveThreshold = config.getMonthlyGoal();
        String baseCte = sellerCommissionDetailCte(config);
        String totalsSql = """
                WITH commissioned_lines AS (
                  %s
                )
                SELECT
                  COALESCE(MAX(seller_username), 'SIN_USUARIO') AS seller_username,
                  COALESCE(MAX(seller_name), 'SIN USUARIO') AS seller_name,
                  COALESCE(SUM(quantity), 0) AS quantity,
                  COUNT(DISTINCT source || '-' || doc_id) AS count_documents,
                  COALESCE(SUM(eligible_sales), 0) AS eligible_sales,
                  COALESCE(SUM(commission_base), 0) AS commission_base,
                  COALESCE(SUM(estimated_commission), 0) AS estimated_commission,
                  CASE
                    WHEN COALESCE(SUM(eligible_sales), 0) = 0 THEN 0
                    ELSE COALESCE(SUM(eligible_sales), 0) / ? * 100
                  END AS score
                FROM commissioned_lines
                """.formatted(baseCte);

        SellerPerformanceDetailResponse totals = jdbcClient.sql(totalsSql)
                .params(from, to, from, to, from, to,
                        incentiveThreshold, incentiveThreshold, incentiveThreshold, incentiveThreshold,
                        sellerId, incentiveThreshold)
                .query((rs, rowNum) -> SellerPerformanceDetailResponse.builder()
                        .from(from)
                        .to(to)
                        .sellerId(sellerId)
                        .sellerUsername(rs.getString("seller_username"))
                        .sellerName(rs.getString("seller_name"))
                        .incentiveThreshold(incentiveThreshold)
                        .quantity(rs.getBigDecimal("quantity"))
                        .countDocuments(rs.getLong("count_documents"))
                        .eligibleSales(rs.getBigDecimal("eligible_sales"))
                        .commissionBase(rs.getBigDecimal("commission_base"))
                        .estimatedCommission(rs.getBigDecimal("estimated_commission"))
                        .score(rs.getBigDecimal("score"))
                        .build())
                .single();

        String categorySql = """
                WITH commissioned_lines AS (
                  %s
                )
                SELECT
                  incentive_group,
                  commission_rate,
                  COALESCE(SUM(quantity), 0) AS quantity,
                  COUNT(DISTINCT source || '-' || doc_id) AS count_documents,
                  COALESCE(SUM(eligible_sales), 0) AS eligible_sales,
                  COALESCE(SUM(commission_base), 0) AS commission_base,
                  COALESCE(SUM(estimated_commission), 0) AS estimated_commission
                FROM commissioned_lines
                GROUP BY incentive_group, commission_rate
                ORDER BY eligible_sales DESC, incentive_group ASC
                """.formatted(baseCte);

        List<SellerPerformanceCategoryDetailResponse> categories = jdbcClient.sql(categorySql)
                .params(from, to, from, to, from, to,
                        incentiveThreshold, incentiveThreshold, incentiveThreshold, incentiveThreshold,
                        sellerId)
                .query((rs, rowNum) -> SellerPerformanceCategoryDetailResponse.builder()
                        .incentiveGroup(rs.getString("incentive_group"))
                        .commissionRate(rs.getBigDecimal("commission_rate"))
                        .quantity(rs.getBigDecimal("quantity"))
                        .countDocuments(rs.getLong("count_documents"))
                        .eligibleSales(rs.getBigDecimal("eligible_sales"))
                        .commissionBase(rs.getBigDecimal("commission_base"))
                        .estimatedCommission(rs.getBigDecimal("estimated_commission"))
                        .build())
                .list();

        String productsSql = """
                WITH commissioned_lines AS (
                  %s
                )
                SELECT
                  source,
                  CASE source
                    WHEN 'COUNTER_SALE' THEN 'Venta por ventanilla'
                    WHEN 'CONTRACT' THEN 'Contratos'
                    WHEN 'PROFORMA' THEN 'Proformas'
                    ELSE source
                  END AS source_label,
                  product_id,
                  product_name,
                  brand,
                  category,
                  incentive_group,
                  commission_rate,
                  COALESCE(SUM(quantity), 0) AS quantity,
                  COUNT(DISTINCT source || '-' || doc_id) AS count_documents,
                  COALESCE(SUM(eligible_sales), 0) AS eligible_sales,
                  COALESCE(SUM(commission_base), 0) AS commission_base,
                  COALESCE(SUM(estimated_commission), 0) AS estimated_commission
                FROM commissioned_lines
                GROUP BY source, product_id, product_name, brand, category, incentive_group, commission_rate
                ORDER BY eligible_sales DESC, product_name ASC, source ASC
                """.formatted(baseCte);

        List<SellerPerformanceProductDetailResponse> products = jdbcClient.sql(productsSql)
                .params(from, to, from, to, from, to,
                        incentiveThreshold, incentiveThreshold, incentiveThreshold, incentiveThreshold,
                        sellerId)
                .query((rs, rowNum) -> SellerPerformanceProductDetailResponse.builder()
                        .source(rs.getString("source"))
                        .sourceLabel(rs.getString("source_label"))
                        .productId(rs.getObject("product_id") != null ? rs.getLong("product_id") : null)
                        .productName(rs.getString("product_name"))
                        .brand(rs.getString("brand"))
                        .category(rs.getString("category"))
                        .incentiveGroup(rs.getString("incentive_group"))
                        .commissionRate(rs.getBigDecimal("commission_rate"))
                        .quantity(rs.getBigDecimal("quantity"))
                        .countDocuments(rs.getLong("count_documents"))
                        .eligibleSales(rs.getBigDecimal("eligible_sales"))
                        .commissionBase(rs.getBigDecimal("commission_base"))
                        .estimatedCommission(rs.getBigDecimal("estimated_commission"))
                        .build())
                .list();

        totals.setCategories(categories);
        totals.setProducts(products);
        return totals;
    }

    private String periodExpression(ReportGroupBy groupBy) {
        return periodExpression("s.issue_date", groupBy);
    }

    private String sellerCommissionDetailCte(SellerCommissionConfigResponse config) {
        return """
                WITH product_rows AS (
                  %s
                ),
                eligible_product_rows AS (
                  SELECT
                    *,
                    CASE
                      WHEN search_text LIKE '%%BAJAJ%%' THEN 'BAJAJ'
                      WHEN search_text LIKE '%%KTM%%' THEN 'KTM'
                      WHEN search_text LIKE '%%BON%%' OR search_text LIKE '%%VON%%' THEN 'BON'
                      WHEN search_text LIKE '%%IMBA%%' OR search_text LIKE '%%IMBASAC%%' OR search_text LIKE '%%INVA%%' THEN 'IMBA'
                      ELSE NULL
                    END AS incentive_group,
                    CASE
                      WHEN search_text LIKE '%%BAJAJ%%' THEN %s
                      WHEN search_text LIKE '%%KTM%%' THEN %s
                      WHEN search_text LIKE '%%BON%%' OR search_text LIKE '%%VON%%' THEN %s
                      WHEN search_text LIKE '%%IMBA%%' OR search_text LIKE '%%IMBASAC%%' OR search_text LIKE '%%INVA%%' THEN %s
                      ELSE 0
                    END AS commission_rate
                  FROM (
                    SELECT
                      pr.*,
                      UPPER(CONCAT_WS(' ', COALESCE(pr.brand, ''), COALESCE(pr.category, ''), COALESCE(pr.product_name, ''))) AS search_text
                    FROM product_rows pr
                  ) classified
                  WHERE search_text LIKE '%%BAJAJ%%'
                     OR search_text LIKE '%%KTM%%'
                     OR search_text LIKE '%%BON%%'
                     OR search_text LIKE '%%VON%%'
                     OR search_text LIKE '%%IMBA%%'
                     OR search_text LIKE '%%IMBASAC%%'
                     OR search_text LIKE '%%INVA%%'
                ),
                monthly_ranked AS (
                  SELECT
                    *,
                    DATE_TRUNC('month', period_date)::date AS month_start,
                    COALESCE(SUM(eligible_sales) OVER (
                      PARTITION BY seller_id, DATE_TRUNC('month', period_date)::date
                      ORDER BY period_date, source, doc_id, line_id
                      ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
                    ), 0) AS previous_month_sales,
                    SUM(eligible_sales) OVER (
                      PARTITION BY seller_id, DATE_TRUNC('month', period_date)::date
                      ORDER BY period_date, source, doc_id, line_id
                      ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                    ) AS current_month_sales
                  FROM eligible_product_rows
                )
                SELECT
                  *,
                  GREATEST(current_month_sales - ?, 0)
                    - GREATEST(previous_month_sales - ?, 0) AS commission_base,
                  (
                    GREATEST(current_month_sales - ?, 0)
                    - GREATEST(previous_month_sales - ?, 0)
                  ) * commission_rate AS estimated_commission
                FROM monthly_ranked
                WHERE seller_id = ?
                """.formatted(
                commercialSellerProductRowsCte(),
                config.getBajajRate().toPlainString(),
                config.getKtmRate().toPlainString(),
                config.getBonRate().toPlainString(),
                config.getImbaRate().toPlainString()
        );
    }

    private String periodExpression(String column, ReportGroupBy groupBy) {
        return switch (groupBy) {
            case DAILY -> column;
            case WEEKLY -> "DATE_TRUNC('week', " + column + ")::date";
            case MONTHLY -> "DATE_TRUNC('month', " + column + ")::date";
            case YEARLY -> "DATE_TRUNC('year', " + column + ")::date";
        };
    }

    private String commercialRowsCte() {
        return """
                SELECT
                  'COUNTER_SALE' AS source,
                  cs.id AS doc_id,
                  cs.issue_date AS period_date,
                  COALESCE(cs.total, 0) AS total_sales,
                  COALESCE(costs.total_cost, 0) AS total_cost,
                  COALESCE(cs.total, 0) - COALESCE(costs.total_cost, 0) AS gross_profit
                FROM counter_sale cs
                JOIN (
                  SELECT DISTINCT l.counter_sale_id
                  FROM sale_counter_sale_sunat_link l
                  JOIN sale s ON s.id = l.sale_id
                  WHERE l.reservation_status = 'ACEPTADO'
                    AND s.status = 'EMITIDA'
                    AND s.sunat_status = 'ACEPTADO'
                  UNION
                  SELECT DISTINCT m.counter_sale_id
                  FROM counter_sale_sunat_combo_member m
                  JOIN counter_sale_sunat_combo c ON c.id = m.combo_id
                  JOIN sale s ON s.id = c.generated_sale_id
                  WHERE c.combo_status = 'ACEPTADO'
                    AND s.status = 'EMITIDA'
                    AND s.sunat_status = 'ACEPTADO'
                ) accepted ON accepted.counter_sale_id = cs.id
                LEFT JOIN (
                  SELECT
                    counter_sale_id,
                    COALESCE(SUM(COALESCE(total_cost_snapshot, 0)), 0) AS total_cost
                  FROM counter_sale_item
                  WHERE line_kind = 'VENDIDO'
                  GROUP BY counter_sale_id
                ) costs ON costs.counter_sale_id = cs.id
                WHERE cs.status = 'EMITIDA'
                  AND cs.issue_date BETWEEN ? AND ?

                UNION ALL

                SELECT
                  'CONTRACT' AS source,
                  c.id AS doc_id,
                  c.issue_date AS period_date,
                  COALESCE(c.total_amount, c.cash_price, 0) AS total_sales,
                  COALESCE(costs.total_cost, 0) AS total_cost,
                  COALESCE(c.total_amount, c.cash_price, 0) - COALESCE(costs.total_cost, 0) AS gross_profit
                FROM contract c
                JOIN sale s ON s.id = c.sale_id
                LEFT JOIN (
                  SELECT
                    sale_id,
                    COALESCE(SUM(COALESCE(total_cost_snapshot, 0)), 0) AS total_cost
                  FROM sale_item
                  WHERE line_kind = 'VENDIDO'
                  GROUP BY sale_id
                ) costs ON costs.sale_id = s.id
                WHERE c.status = 'FACTURADO'
                  AND s.status = 'EMITIDA'
                  AND s.sunat_status = 'ACEPTADO'
                  AND c.issue_date BETWEEN ? AND ?

                UNION ALL

                SELECT
                  'PROFORMA' AS source,
                  p.id AS doc_id,
                  p.issue_date AS period_date,
                  COALESCE(p.total, 0) AS total_sales,
                  COALESCE(costs.total_cost, 0) AS total_cost,
                  COALESCE(p.total, 0) - COALESCE(costs.total_cost, 0) AS gross_profit
                FROM proforma p
                JOIN sale s ON s.id = p.converted_sale_id
                LEFT JOIN (
                  SELECT
                    sale_id,
                    COALESCE(SUM(COALESCE(total_cost_snapshot, 0)), 0) AS total_cost
                  FROM sale_item
                  WHERE line_kind = 'VENDIDO'
                  GROUP BY sale_id
                ) costs ON costs.sale_id = s.id
                WHERE p.status = 'CONVERTIDA'
                  AND s.status = 'EMITIDA'
                  AND s.sunat_status = 'ACEPTADO'
                  AND p.issue_date BETWEEN ? AND ?
                """;
    }

    private String commercialSellerRowsCte() {
        return """
                SELECT
                  'COUNTER_SALE' AS source,
                  cs.id AS doc_id,
                  cs.issue_date AS period_date,
                  COALESCE(cs.created_by, 0) AS seller_id,
                  COALESCE(u.username, 'SIN_USUARIO') AS seller_username,
                  COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.username, 'SIN USUARIO') AS seller_name,
                  COALESCE(cs.total, 0) AS total_sales
                FROM counter_sale cs
                JOIN (
                  SELECT DISTINCT l.counter_sale_id
                  FROM sale_counter_sale_sunat_link l
                  JOIN sale s ON s.id = l.sale_id
                  WHERE l.reservation_status = 'ACEPTADO'
                    AND s.status = 'EMITIDA'
                    AND s.sunat_status = 'ACEPTADO'
                  UNION
                  SELECT DISTINCT m.counter_sale_id
                  FROM counter_sale_sunat_combo_member m
                  JOIN counter_sale_sunat_combo c ON c.id = m.combo_id
                  JOIN sale s ON s.id = c.generated_sale_id
                  WHERE c.combo_status = 'ACEPTADO'
                    AND s.status = 'EMITIDA'
                    AND s.sunat_status = 'ACEPTADO'
                ) accepted ON accepted.counter_sale_id = cs.id
                LEFT JOIN users u ON u.id = cs.created_by
                WHERE cs.status = 'EMITIDA'
                  AND cs.issue_date BETWEEN ? AND ?

                UNION ALL

                SELECT
                  'CONTRACT' AS source,
                  c.id AS doc_id,
                  c.issue_date AS period_date,
                  COALESCE(c.created_by, 0) AS seller_id,
                  COALESCE(u.username, 'SIN_USUARIO') AS seller_username,
                  COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.username, 'SIN USUARIO') AS seller_name,
                  COALESCE(c.total_amount, c.cash_price, 0) AS total_sales
                FROM contract c
                JOIN sale s ON s.id = c.sale_id
                LEFT JOIN users u ON u.id = c.created_by
                WHERE c.status = 'FACTURADO'
                  AND s.status = 'EMITIDA'
                  AND s.sunat_status = 'ACEPTADO'
                  AND c.issue_date BETWEEN ? AND ?

                UNION ALL

                SELECT
                  'PROFORMA' AS source,
                  p.id AS doc_id,
                  p.issue_date AS period_date,
                  COALESCE(p.created_by, 0) AS seller_id,
                  COALESCE(u.username, 'SIN_USUARIO') AS seller_username,
                  COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.username, 'SIN USUARIO') AS seller_name,
                  COALESCE(p.total, 0) AS total_sales
                FROM proforma p
                JOIN sale s ON s.id = p.converted_sale_id
                LEFT JOIN users u ON u.id = p.created_by
                WHERE p.status = 'CONVERTIDA'
                  AND s.status = 'EMITIDA'
                  AND s.sunat_status = 'ACEPTADO'
                  AND p.issue_date BETWEEN ? AND ?
                """;
    }

    private String commercialSellerProductRowsCte() {
        return """
                SELECT
                  'COUNTER_SALE' AS source,
                  cs.id AS doc_id,
                  csi.id AS line_id,
                  cs.issue_date AS period_date,
                  COALESCE(cs.created_by, 0) AS seller_id,
                  COALESCE(u.username, 'SIN_USUARIO') AS seller_username,
                  COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.username, 'SIN USUARIO') AS seller_name,
                  csi.product_id,
                  COALESCE(NULLIF(TRIM(p.name), ''), csi.description) AS product_name,
                  COALESCE(NULLIF(TRIM(p.brand), ''), '') AS brand,
                  COALESCE(NULLIF(TRIM(p.category), ''), '') AS category,
                  COALESCE(csi.quantity, 0) AS quantity,
                  COALESCE(csi.revenue_total, 0) AS eligible_sales
                FROM counter_sale cs
                JOIN (
                  SELECT DISTINCT l.counter_sale_id
                  FROM sale_counter_sale_sunat_link l
                  JOIN sale s ON s.id = l.sale_id
                  WHERE l.reservation_status = 'ACEPTADO'
                    AND s.status = 'EMITIDA'
                    AND s.sunat_status = 'ACEPTADO'
                  UNION
                  SELECT DISTINCT m.counter_sale_id
                  FROM counter_sale_sunat_combo_member m
                  JOIN counter_sale_sunat_combo c ON c.id = m.combo_id
                  JOIN sale s ON s.id = c.generated_sale_id
                  WHERE c.combo_status = 'ACEPTADO'
                    AND s.status = 'EMITIDA'
                    AND s.sunat_status = 'ACEPTADO'
                ) accepted ON accepted.counter_sale_id = cs.id
                JOIN counter_sale_item csi ON csi.counter_sale_id = cs.id
                LEFT JOIN product p ON p.id = csi.product_id
                LEFT JOIN users u ON u.id = cs.created_by
                WHERE cs.status = 'EMITIDA'
                  AND cs.issue_date BETWEEN ? AND ?
                  AND csi.line_kind = 'VENDIDO'

                UNION ALL

                SELECT
                  'CONTRACT' AS source,
                  c.id AS doc_id,
                  ci.id AS line_id,
                  c.issue_date AS period_date,
                  COALESCE(c.created_by, 0) AS seller_id,
                  COALESCE(u.username, 'SIN_USUARIO') AS seller_username,
                  COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.username, 'SIN USUARIO') AS seller_name,
                  ci.product_id,
                  COALESCE(NULLIF(TRIM(p.name), ''), ci.description) AS product_name,
                  COALESCE(NULLIF(TRIM(p.brand), ''), NULLIF(TRIM(ci.brand), ''), '') AS brand,
                  COALESCE(NULLIF(TRIM(p.category), ''), '') AS category,
                  1::numeric AS quantity,
                  COALESCE(c.total_amount, c.cash_price, ci.unit_price, 0) AS eligible_sales
                FROM contract c
                JOIN sale s ON s.id = c.sale_id
                JOIN contract_item ci ON ci.contract_id = c.id
                LEFT JOIN product p ON p.id = ci.product_id
                LEFT JOIN users u ON u.id = c.created_by
                WHERE c.status = 'FACTURADO'
                  AND s.status = 'EMITIDA'
                  AND s.sunat_status = 'ACEPTADO'
                  AND c.issue_date BETWEEN ? AND ?

                UNION ALL

                SELECT
                  'PROFORMA' AS source,
                  p.id AS doc_id,
                  COALESCE(pi.line_number, 0) AS line_id,
                  p.issue_date AS period_date,
                  COALESCE(p.created_by, 0) AS seller_id,
                  COALESCE(u.username, 'SIN_USUARIO') AS seller_username,
                  COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.username, 'SIN USUARIO') AS seller_name,
                  pi.product_id,
                  COALESCE(NULLIF(TRIM(pr.name), ''), pi.description) AS product_name,
                  COALESCE(NULLIF(TRIM(pr.brand), ''), '') AS brand,
                  COALESCE(NULLIF(TRIM(pr.category), ''), '') AS category,
                  COALESCE(pi.quantity, 0) AS quantity,
                  COALESCE(pi.line_subtotal, 0) AS eligible_sales
                FROM proforma p
                JOIN sale s ON s.id = p.converted_sale_id
                JOIN proforma_item pi ON pi.proforma_id = p.id
                LEFT JOIN product pr ON pr.id = pi.product_id
                LEFT JOIN users u ON u.id = p.created_by
                WHERE p.status = 'CONVERTIDA'
                  AND s.status = 'EMITIDA'
                  AND s.sunat_status = 'ACEPTADO'
                  AND p.issue_date BETWEEN ? AND ?
                """;
    }

    private String commercialProductRowsCte() {
        return """
                SELECT
                  'COUNTER_SALE' AS source,
                  cs.id AS doc_id,
                  cs.series,
                  cs.number,
                  cs.issue_date,
                  COALESCE(NULLIF(TRIM(cs.customer_name), ''), 'CLIENTE NO REGISTRADO') AS customer_name,
                  csi.product_id,
                  COALESCE(NULLIF(TRIM(p.sku), ''), '') AS product_sku,
                  COALESCE(NULLIF(TRIM(p.name), ''), csi.description) AS product_name,
                  COALESCE(NULLIF(TRIM(p.brand), ''), '') AS brand,
                  COALESCE(NULLIF(TRIM(p.category), ''), '') AS category,
                  COALESCE(NULLIF(TRIM(p.model), ''), '') AS model,
                  COALESCE(csi.quantity, 0) AS quantity,
                  COALESCE(csi.revenue_total, 0) AS total_sales,
                  COALESCE(csi.revenue_total, 0) - COALESCE(csi.total_cost_snapshot, 0) AS total_profit
                FROM counter_sale cs
                JOIN (
                  SELECT DISTINCT l.counter_sale_id
                  FROM sale_counter_sale_sunat_link l
                  JOIN sale s ON s.id = l.sale_id
                  WHERE l.reservation_status = 'ACEPTADO'
                    AND s.status = 'EMITIDA'
                    AND s.sunat_status = 'ACEPTADO'
                  UNION
                  SELECT DISTINCT m.counter_sale_id
                  FROM counter_sale_sunat_combo_member m
                  JOIN counter_sale_sunat_combo c ON c.id = m.combo_id
                  JOIN sale s ON s.id = c.generated_sale_id
                  WHERE c.combo_status = 'ACEPTADO'
                    AND s.status = 'EMITIDA'
                    AND s.sunat_status = 'ACEPTADO'
                ) accepted ON accepted.counter_sale_id = cs.id
                JOIN counter_sale_item csi ON csi.counter_sale_id = cs.id
                LEFT JOIN product p ON p.id = csi.product_id
                WHERE cs.status = 'EMITIDA'
                  AND cs.issue_date BETWEEN ? AND ?
                  AND csi.line_kind = 'VENDIDO'

                UNION ALL

                SELECT
                  'CONTRACT' AS source,
                  c.id AS doc_id,
                  c.series,
                  c.number,
                  c.issue_date,
                  COALESCE(NULLIF(TRIM(c.customer_name), ''), 'CLIENTE NO REGISTRADO') AS customer_name,
                  ci.product_id,
                  COALESCE(NULLIF(TRIM(p.sku), ''), '') AS product_sku,
                  COALESCE(NULLIF(TRIM(p.name), ''), ci.description) AS product_name,
                  COALESCE(NULLIF(TRIM(p.brand), ''), NULLIF(TRIM(ci.brand), ''), '') AS brand,
                  COALESCE(NULLIF(TRIM(p.category), ''), '') AS category,
                  COALESCE(NULLIF(TRIM(p.model), ''), NULLIF(TRIM(ci.model), ''), '') AS model,
                  1::numeric AS quantity,
                  COALESCE(c.total_amount, c.cash_price, ci.unit_price, 0) AS total_sales,
                  COALESCE(c.total_amount, c.cash_price, ci.unit_price, 0) - COALESCE(costs.total_cost, 0) AS total_profit
                FROM contract c
                JOIN sale s ON s.id = c.sale_id
                JOIN contract_item ci ON ci.contract_id = c.id
                LEFT JOIN product p ON p.id = ci.product_id
                LEFT JOIN (
                  SELECT
                    sale_id,
                    product_id,
                    COALESCE(SUM(COALESCE(total_cost_snapshot, 0)), 0) AS total_cost
                  FROM sale_item
                  WHERE line_kind = 'VENDIDO'
                  GROUP BY sale_id, product_id
                ) costs ON costs.sale_id = s.id AND costs.product_id = ci.product_id
                WHERE c.status = 'FACTURADO'
                  AND s.status = 'EMITIDA'
                  AND s.sunat_status = 'ACEPTADO'
                  AND c.issue_date BETWEEN ? AND ?

                UNION ALL

                SELECT
                  'PROFORMA' AS source,
                  p.id AS doc_id,
                  p.series,
                  p.number,
                  p.issue_date,
                  COALESCE(NULLIF(TRIM(p.customer_name), ''), 'CLIENTE NO REGISTRADO') AS customer_name,
                  pi.product_id,
                  COALESCE(NULLIF(TRIM(pr.sku), ''), '') AS product_sku,
                  COALESCE(NULLIF(TRIM(pr.name), ''), pi.description) AS product_name,
                  COALESCE(NULLIF(TRIM(pr.brand), ''), '') AS brand,
                  COALESCE(NULLIF(TRIM(pr.category), ''), '') AS category,
                  COALESCE(NULLIF(TRIM(pr.model), ''), '') AS model,
                  COALESCE(pi.quantity, 0) AS quantity,
                  COALESCE(pi.line_subtotal, 0) AS total_sales,
                  COALESCE(pi.line_subtotal, 0) - COALESCE(costs.total_cost, 0) AS total_profit
                FROM proforma p
                JOIN sale s ON s.id = p.converted_sale_id
                JOIN proforma_item pi ON pi.proforma_id = p.id
                LEFT JOIN product pr ON pr.id = pi.product_id
                LEFT JOIN (
                  SELECT
                    sale_id,
                    product_id,
                    COALESCE(SUM(COALESCE(total_cost_snapshot, 0)), 0) AS total_cost
                  FROM sale_item
                  WHERE line_kind = 'VENDIDO'
                  GROUP BY sale_id, product_id
                ) costs ON costs.sale_id = s.id AND costs.product_id = pi.product_id
                WHERE p.status = 'CONVERTIDA'
                  AND s.status = 'EMITIDA'
                  AND s.sunat_status = 'ACEPTADO'
                  AND p.issue_date BETWEEN ? AND ?
                """;
    }

    private record ChannelPointRow(String source, SalesProfitChannelPointResponse point) {
    }
}
