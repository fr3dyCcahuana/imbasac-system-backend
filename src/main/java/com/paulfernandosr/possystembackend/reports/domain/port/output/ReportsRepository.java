package com.paulfernandosr.possystembackend.reports.domain.port.output;

import com.paulfernandosr.possystembackend.reports.domain.ReportGroupBy;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProductSaleDetailResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProductTopResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProfitPeriodResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SalesAggResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SalesProfitChannelPointResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceRowResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SunatComparisonResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ReportsRepository {
    List<ProfitPeriodResponse> findProfit(LocalDate from, LocalDate to, ReportGroupBy groupBy);

    List<SalesAggResponse> findSalesTotal(LocalDate from, LocalDate to, ReportGroupBy groupBy);

    List<ProductTopResponse> findProductsTop(LocalDate from, LocalDate to, String sortBy, int limit);

    List<ProductSaleDetailResponse> findProductSaleDetails(LocalDate from, LocalDate to, Long productId);

    Map<String, List<SalesProfitChannelPointResponse>> findSalesProfitByChannel(LocalDate from,
                                                                                LocalDate to,
                                                                                ReportGroupBy groupBy);

    List<SunatComparisonResponse> findSunatComparison(LocalDate from, LocalDate to);

    List<SellerPerformanceRowResponse> findSellerPerformance(LocalDate from, LocalDate to, ReportGroupBy groupBy);
}
