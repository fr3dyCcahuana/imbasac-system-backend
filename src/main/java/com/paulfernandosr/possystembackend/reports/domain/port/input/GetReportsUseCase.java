package com.paulfernandosr.possystembackend.reports.domain.port.input;

import com.paulfernandosr.possystembackend.reports.domain.ReportGroupBy;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.DashboardSalesProfitResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProductSaleDetailResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProductTopResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProfitPeriodResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SalesAggResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceDetailResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SunatComparisonResponse;

import java.time.LocalDate;
import java.util.List;

public interface GetReportsUseCase {
    List<ProfitPeriodResponse> getProfit(LocalDate from, LocalDate to, ReportGroupBy groupBy);

    List<SalesAggResponse> getSalesTotal(LocalDate from, LocalDate to, ReportGroupBy groupBy);

    List<ProductTopResponse> getProductsTop(LocalDate from, LocalDate to, String sortBy, int limit);

    byte[] getProductsTopExcel(LocalDate from, LocalDate to, int limit);

    List<ProductSaleDetailResponse> getProductSaleDetails(LocalDate from, LocalDate to, Long productId);

    DashboardSalesProfitResponse getDashboardSalesProfit(LocalDate from, LocalDate to, ReportGroupBy groupBy);

    List<SunatComparisonResponse> getSunatComparison(LocalDate from, LocalDate to);

    SellerPerformanceResponse getSellerPerformance(LocalDate from, LocalDate to, ReportGroupBy groupBy);

    SellerPerformanceDetailResponse getSellerPerformanceDetail(LocalDate from, LocalDate to, Long sellerId);
}
