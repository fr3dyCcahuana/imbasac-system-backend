package com.paulfernandosr.possystembackend.reports.application;

import com.paulfernandosr.possystembackend.reports.domain.ReportGroupBy;
import com.paulfernandosr.possystembackend.reports.domain.port.input.GetReportsUseCase;
import com.paulfernandosr.possystembackend.reports.domain.port.output.ReportsRepository;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.DashboardSalesProfitResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProductSaleDetailResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProductTopResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProfitPeriodResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SalesAggResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SalesProfitChannelPointResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SalesProfitChannelResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SunatComparisonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetReportsService implements GetReportsUseCase {

    private static final Set<String> PRODUCT_SORTS = Set.of("QTY", "REVENUE", "PROFIT");
    private static final BigDecimal SELLER_INCENTIVE_THRESHOLD = new BigDecimal("50000.00");
    private static final List<ChannelDefinition> CHANNELS = List.of(
            new ChannelDefinition("COUNTER_SALE", "Venta por ventanilla"),
            new ChannelDefinition("CONTRACT", "Contratos"),
            new ChannelDefinition("PROFORMA", "Proformas")
    );

    private final ReportsRepository reportsRepository;

    @Override
    public List<ProfitPeriodResponse> getProfit(LocalDate from, LocalDate to, ReportGroupBy groupBy) {
        validateRange(from, to);
        return reportsRepository.findProfit(from, to, groupBy);
    }

    @Override
    public List<SalesAggResponse> getSalesTotal(LocalDate from, LocalDate to, ReportGroupBy groupBy) {
        validateRange(from, to);
        return reportsRepository.findSalesTotal(from, to, groupBy);
    }

    @Override
    public List<ProductTopResponse> getProductsTop(LocalDate from, LocalDate to, String sortBy, int limit) {
        validateRange(from, to);
        String normalizedSort = sortBy == null ? "QTY" : sortBy.trim().toUpperCase();
        if (!PRODUCT_SORTS.contains(normalizedSort)) {
            throw new IllegalArgumentException("sortBy debe ser QTY, REVENUE o PROFIT.");
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return reportsRepository.findProductsTop(from, to, normalizedSort, safeLimit);
    }

    @Override
    public List<ProductSaleDetailResponse> getProductSaleDetails(LocalDate from, LocalDate to, Long productId) {
        validateRange(from, to);
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("productId es obligatorio.");
        }
        return reportsRepository.findProductSaleDetails(from, to, productId);
    }

    @Override
    public DashboardSalesProfitResponse getDashboardSalesProfit(LocalDate from, LocalDate to, ReportGroupBy groupBy) {
        validateRange(from, to);
        Map<String, List<SalesProfitChannelPointResponse>> pointsByChannel =
                reportsRepository.findSalesProfitByChannel(from, to, groupBy);

        List<SalesProfitChannelResponse> channels = new ArrayList<>();
        for (ChannelDefinition channel : CHANNELS) {
            List<SalesProfitChannelPointResponse> points = pointsByChannel.getOrDefault(channel.source(), List.of());
            channels.add(SalesProfitChannelResponse.builder()
                    .source(channel.source())
                    .label(channel.label())
                    .totals(sum(points))
                    .points(points)
                    .build());
        }

        return DashboardSalesProfitResponse.builder()
                .from(from)
                .to(to)
                .groupBy(groupBy.name())
                .channels(channels)
                .build();
    }

    @Override
    public List<SunatComparisonResponse> getSunatComparison(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return reportsRepository.findSunatComparison(from, to);
    }

    @Override
    public SellerPerformanceResponse getSellerPerformance(LocalDate from, LocalDate to, ReportGroupBy groupBy) {
        validateRange(from, to);
        return SellerPerformanceResponse.builder()
                .from(from)
                .to(to)
                .groupBy(groupBy.name())
                .incentiveThreshold(SELLER_INCENTIVE_THRESHOLD)
                .rows(reportsRepository.findSellerPerformance(from, to, groupBy))
                .build();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from y to son obligatorios.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from no puede ser mayor que to.");
        }
    }

    private SalesProfitChannelPointResponse sum(List<SalesProfitChannelPointResponse> points) {
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal grossProfit = BigDecimal.ZERO;
        long countSales = 0;

        for (SalesProfitChannelPointResponse point : points) {
            totalSales = totalSales.add(nz(point.getTotalSales()));
            totalCost = totalCost.add(nz(point.getTotalCost()));
            grossProfit = grossProfit.add(nz(point.getGrossProfit()));
            countSales += point.getCountSales() == null ? 0 : point.getCountSales();
        }

        return SalesProfitChannelPointResponse.builder()
                .periodStart(null)
                .totalSales(totalSales)
                .totalCost(totalCost)
                .grossProfit(grossProfit)
                .countSales(countSales)
                .build();
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record ChannelDefinition(String source, String label) {
    }
}
