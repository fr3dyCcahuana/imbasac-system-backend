package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.reports.domain.ReportGroupBy;
import com.paulfernandosr.possystembackend.reports.domain.port.input.GetReportsUseCase;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.DashboardSalesProfitResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProductSaleDetailResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProductTopResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.ProfitPeriodResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SalesAggResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceDetailResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SunatComparisonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportsRestController {

    private final GetReportsUseCase getReportsUseCase;

    @GetMapping("/profit/weekly")
    public ResponseEntity<List<ProfitPeriodResponse>> getWeeklyProfit(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(getReportsUseCase.getProfit(from, to, ReportGroupBy.WEEKLY));
    }

    @GetMapping("/profit/monthly")
    public ResponseEntity<List<ProfitPeriodResponse>> getMonthlyProfit(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(getReportsUseCase.getProfit(from, to, ReportGroupBy.MONTHLY));
    }

    @GetMapping("/profit/yearly")
    public ResponseEntity<List<ProfitPeriodResponse>> getYearlyProfit(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(getReportsUseCase.getProfit(from, to, ReportGroupBy.YEARLY));
    }

    @GetMapping("/sales/total/daily")
    public ResponseEntity<List<SalesAggResponse>> getDailySales(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(getReportsUseCase.getSalesTotal(from, to, ReportGroupBy.DAILY));
    }

    @GetMapping("/sales/total/weekly")
    public ResponseEntity<List<SalesAggResponse>> getWeeklySales(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(getReportsUseCase.getSalesTotal(from, to, ReportGroupBy.WEEKLY));
    }

    @GetMapping("/sales/total/monthly")
    public ResponseEntity<List<SalesAggResponse>> getMonthlySales(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(getReportsUseCase.getSalesTotal(from, to, ReportGroupBy.MONTHLY));
    }

    @GetMapping("/products/top")
    public ResponseEntity<List<ProductTopResponse>> getProductsTop(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "QTY") String sortBy,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(getReportsUseCase.getProductsTop(from, to, sortBy, limit));
    }

    @GetMapping("/products/top/details")
    public ResponseEntity<List<ProductSaleDetailResponse>> getProductSaleDetails(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam("productId") Long productId
    ) {
        return ResponseEntity.ok(getReportsUseCase.getProductSaleDetails(from, to, productId));
    }

    @GetMapping("/dashboard/sales-profit")
    public ResponseEntity<DashboardSalesProfitResponse> getDashboardSalesProfit(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "MONTHLY") String groupBy
    ) {
        return ResponseEntity.ok(getReportsUseCase.getDashboardSalesProfit(from, to, parseGroupBy(groupBy)));
    }

    @GetMapping("/dashboard/sunat-comparison")
    public ResponseEntity<List<SunatComparisonResponse>> getSunatComparison(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(getReportsUseCase.getSunatComparison(from, to));
    }

    @GetMapping("/dashboard/seller-performance")
    public ResponseEntity<SellerPerformanceResponse> getSellerPerformance(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "MONTHLY") String groupBy
    ) {
        return ResponseEntity.ok(getReportsUseCase.getSellerPerformance(from, to, parseGroupBy(groupBy)));
    }

    @GetMapping("/dashboard/seller-performance/details")
    public ResponseEntity<SellerPerformanceDetailResponse> getSellerPerformanceDetails(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam("sellerId") Long sellerId
    ) {
        return ResponseEntity.ok(getReportsUseCase.getSellerPerformanceDetail(from, to, sellerId));
    }

    private ReportGroupBy parseGroupBy(String value) {
        if (value == null || value.isBlank()) {
            return ReportGroupBy.MONTHLY;
        }
        return ReportGroupBy.valueOf(value.trim().toUpperCase());
    }
}
