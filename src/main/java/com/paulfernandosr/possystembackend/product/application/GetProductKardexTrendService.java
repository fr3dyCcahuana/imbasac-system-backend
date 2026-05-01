package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.*;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.exception.ProductNotFoundException;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductKardexTrendUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductKardexTrendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GetProductKardexTrendService implements GetProductKardexTrendUseCase {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final Set<String> ALLOWED_MOVEMENT_TYPES = Set.of(
            "IN_PURCHASE",
            "OUT_SALE",
            "OUT_COUNTER_SALE",
            "IN_COUNTER_SALE_VOID",
            "IN_RETURN",
            "IN_SALE_EDIT",
            "OUT_SALE_EDIT",
            "IN_ADJUST",
            "OUT_ADJUST"
    );

    private static final String[] MONTH_LABELS = {
            "Ene", "Feb", "Mar", "Abr", "May", "Jun",
            "Jul", "Ago", "Set", "Oct", "Nov", "Dic"
    };

    private final ProductKardexTrendRepository repository;

    @Override
    public ProductKardexTrendResponse getTrend(
            Long productId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String groupBy,
            String source,
            String movementType,
            Boolean includeEmptyPeriods
    ) {
        if (productId == null || productId <= 0) {
            throw new InvalidProductException("productId es obligatorio y debe ser mayor que cero.");
        }

        ProductKardexTrendProductResponse product = repository.findProductById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Producto no encontrado. No existe un producto con id " + productId));

        LocalDate effectiveDateTo = dateTo != null ? dateTo : LocalDate.now();
        LocalDate effectiveDateFrom = dateFrom != null ? dateFrom : effectiveDateTo.minusMonths(6);
        KardexTrendGroupBy effectiveGroupBy = parseGroupBy(groupBy);
        KardexSource effectiveSource = parseSource(source);
        String effectiveMovementType = normalizeMovementType(movementType);
        boolean effectiveIncludeEmptyPeriods = includeEmptyPeriods == null || includeEmptyPeriods;

        validateDates(effectiveDateFrom, effectiveDateTo, effectiveGroupBy);

        ProductKardexTrendFilter filter = new ProductKardexTrendFilter(
                productId,
                effectiveDateFrom,
                effectiveDateTo,
                effectiveGroupBy,
                effectiveSource,
                effectiveMovementType,
                effectiveIncludeEmptyPeriods
        );

        List<ProductKardexTrendMovement> movements = repository.findMovements(filter);
        ProductKardexTrendStockSnapshot stockSnapshot = repository
                .findLastStockSnapshotBefore(productId, effectiveDateFrom.atStartOfDay())
                .orElse(new ProductKardexTrendStockSnapshot(ZERO, null));

        List<ProductKardexTrendPointResponse> points = buildPoints(
                movements,
                effectiveDateFrom,
                effectiveDateTo,
                effectiveGroupBy,
                effectiveIncludeEmptyPeriods,
                stockSnapshot
        );

        ProductKardexTrendSummaryResponse summary = buildSummary(points, stockSnapshot);
        ProductKardexTrendChartResponse chart = buildChart(points);

        return new ProductKardexTrendResponse(
                product,
                new ProductKardexTrendFiltersResponse(
                        effectiveDateFrom,
                        effectiveDateTo,
                        effectiveGroupBy,
                        effectiveSource,
                        effectiveMovementType,
                        effectiveIncludeEmptyPeriods
                ),
                summary,
                points,
                chart
        );
    }

    private KardexTrendGroupBy parseGroupBy(String value) {
        if (value == null || value.trim().isEmpty()) {
            return KardexTrendGroupBy.MONTH;
        }
        try {
            return KardexTrendGroupBy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidProductException("Agrupación inválida. groupBy solo permite DAY, MONTH o YEAR.");
        }
    }

    private KardexSource parseSource(String value) {
        if (value == null || value.trim().isEmpty()) {
            return KardexSource.ALL;
        }
        try {
            return KardexSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidProductException("source inválido. Use ALL, PURCHASE, SALE, COUNTER_SALE o ADJUSTMENT.");
        }
    }

    private String normalizeMovementType(String movementType) {
        if (movementType == null || movementType.trim().isEmpty()) {
            return null;
        }
        String normalized = movementType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_MOVEMENT_TYPES.contains(normalized)) {
            throw new InvalidProductException("movementType inválido o no permitido: " + normalized);
        }
        return normalized;
    }

    private void validateDates(LocalDate dateFrom, LocalDate dateTo, KardexTrendGroupBy groupBy) {
        if (dateFrom.isAfter(dateTo)) {
            throw new InvalidProductException("Rango de fechas inválido. dateFrom no puede ser mayor que dateTo.");
        }

        switch (groupBy) {
            case DAY -> {
                long days = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
                if (days > 370) {
                    throw new InvalidProductException("Rango demasiado amplio. Para groupBy=DAY el máximo permitido es 370 días.");
                }
            }
            case MONTH -> {
                long months = ChronoUnit.MONTHS.between(YearMonth.from(dateFrom), YearMonth.from(dateTo)) + 1;
                if (months > 60) {
                    throw new InvalidProductException("Rango demasiado amplio. Para groupBy=MONTH el máximo permitido es 60 meses.");
                }
            }
            case YEAR -> {
                long years = dateTo.getYear() - dateFrom.getYear() + 1L;
                if (years > 10) {
                    throw new InvalidProductException("Rango demasiado amplio. Para groupBy=YEAR el máximo permitido es 10 años.");
                }
            }
        }
    }

    private List<ProductKardexTrendPointResponse> buildPoints(
            List<ProductKardexTrendMovement> movements,
            LocalDate dateFrom,
            LocalDate dateTo,
            KardexTrendGroupBy groupBy,
            boolean includeEmptyPeriods,
            ProductKardexTrendStockSnapshot stockSnapshot
    ) {
        Map<String, TrendPointAccumulator> periods = new LinkedHashMap<>();

        if (includeEmptyPeriods) {
            for (String period : generatePeriods(dateFrom, dateTo, groupBy)) {
                periods.put(period, new TrendPointAccumulator(period, periodLabel(period, groupBy)));
            }
        }

        for (ProductKardexTrendMovement movement : movements) {
            LocalDate movementDate = movement.movementDate().toLocalDate();
            String period = periodKey(movementDate, groupBy);
            TrendPointAccumulator acc = periods.computeIfAbsent(
                    period,
                    key -> new TrendPointAccumulator(key, periodLabel(key, groupBy))
            );
            acc.add(movement);
        }

        BigDecimal carryStock = defaultZero(stockSnapshot.stock());
        BigDecimal carryAverageCost = stockSnapshot.averageCost();

        List<ProductKardexTrendPointResponse> points = new ArrayList<>();
        for (TrendPointAccumulator acc : periods.values()) {
            ProductKardexTrendPointResponse point = acc.toResponse(carryStock, carryAverageCost);

            if (acc.movementCount > 0 || includeEmptyPeriods) {
                points.add(point);
            }

            carryStock = defaultZero(point.finalStock());
            if (point.averageCost() != null) {
                carryAverageCost = point.averageCost();
            }
        }

        points.sort(Comparator.comparing(ProductKardexTrendPointResponse::period));
        return points;
    }

    private List<String> generatePeriods(LocalDate dateFrom, LocalDate dateTo, KardexTrendGroupBy groupBy) {
        List<String> periods = new ArrayList<>();

        switch (groupBy) {
            case DAY -> {
                LocalDate cursor = dateFrom;
                while (!cursor.isAfter(dateTo)) {
                    periods.add(periodKey(cursor, groupBy));
                    cursor = cursor.plusDays(1);
                }
            }
            case MONTH -> {
                YearMonth cursor = YearMonth.from(dateFrom);
                YearMonth end = YearMonth.from(dateTo);
                while (!cursor.isAfter(end)) {
                    periods.add(cursor.toString());
                    cursor = cursor.plusMonths(1);
                }
            }
            case YEAR -> {
                int cursor = dateFrom.getYear();
                int end = dateTo.getYear();
                while (cursor <= end) {
                    periods.add(String.valueOf(cursor));
                    cursor++;
                }
            }
        }

        return periods;
    }

    private String periodKey(LocalDate date, KardexTrendGroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> date.toString();
            case MONTH -> YearMonth.from(date).toString();
            case YEAR -> String.valueOf(date.getYear());
        };
    }

    private String periodLabel(String period, KardexTrendGroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> LocalDate.parse(period).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            case MONTH -> {
                YearMonth ym = YearMonth.parse(period);
                yield MONTH_LABELS[ym.getMonthValue() - 1] + " " + ym.getYear();
            }
            case YEAR -> period;
        };
    }

    private ProductKardexTrendSummaryResponse buildSummary(
            List<ProductKardexTrendPointResponse> points,
            ProductKardexTrendStockSnapshot stockSnapshot
    ) {
        BigDecimal totalIn = ZERO;
        BigDecimal totalOut = ZERO;
        BigDecimal totalPurchaseAmount = ZERO;
        BigDecimal totalSalesAmount = ZERO;
        BigDecimal totalCostIn = ZERO;
        BigDecimal totalCostOut = ZERO;
        BigDecimal lastAverageCost = stockSnapshot.averageCost();

        for (ProductKardexTrendPointResponse point : points) {
            totalIn = totalIn.add(defaultZero(point.quantityIn()));
            totalOut = totalOut.add(defaultZero(point.quantityOut()));
            totalPurchaseAmount = totalPurchaseAmount.add(defaultZero(point.purchaseAmount()));
            totalSalesAmount = totalSalesAmount.add(defaultZero(point.salesAmount()));
            totalCostIn = totalCostIn.add(defaultZero(point.totalCostIn()));
            totalCostOut = totalCostOut.add(defaultZero(point.totalCostOut()));
            if (point.averageCost() != null) {
                lastAverageCost = point.averageCost();
            }
        }

        BigDecimal initialStock = points.isEmpty()
                ? defaultZero(stockSnapshot.stock())
                : defaultZero(points.get(0).initialStock());
        BigDecimal finalStock = points.isEmpty()
                ? initialStock
                : defaultZero(points.get(points.size() - 1).finalStock());

        return new ProductKardexTrendSummaryResponse(
                totalIn,
                totalOut,
                totalIn.subtract(totalOut),
                initialStock,
                finalStock,
                finalStock.subtract(initialStock),
                totalPurchaseAmount,
                totalSalesAmount,
                totalCostIn,
                totalCostOut,
                lastAverageCost,
                points.size()
        );
    }

    private ProductKardexTrendChartResponse buildChart(List<ProductKardexTrendPointResponse> points) {
        List<String> labels = new ArrayList<>();
        List<BigDecimal> quantityIn = new ArrayList<>();
        List<BigDecimal> quantityOut = new ArrayList<>();
        List<BigDecimal> netQuantity = new ArrayList<>();
        List<BigDecimal> initialStock = new ArrayList<>();
        List<BigDecimal> finalStock = new ArrayList<>();
        List<BigDecimal> purchaseAmount = new ArrayList<>();
        List<BigDecimal> salesAmount = new ArrayList<>();
        List<BigDecimal> averageCost = new ArrayList<>();

        for (ProductKardexTrendPointResponse point : points) {
            labels.add(point.periodLabel());
            quantityIn.add(defaultZero(point.quantityIn()));
            quantityOut.add(defaultZero(point.quantityOut()));
            netQuantity.add(defaultZero(point.netQuantity()));
            initialStock.add(defaultZero(point.initialStock()));
            finalStock.add(defaultZero(point.finalStock()));
            purchaseAmount.add(defaultZero(point.purchaseAmount()));
            salesAmount.add(defaultZero(point.salesAmount()));
            averageCost.add(point.averageCost());
        }

        return new ProductKardexTrendChartResponse(
                labels,
                new ProductKardexTrendDatasetsResponse(
                        quantityIn,
                        quantityOut,
                        netQuantity,
                        initialStock,
                        finalStock,
                        purchaseAmount,
                        salesAmount,
                        averageCost
                )
        );
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private static final class TrendPointAccumulator {
        private final String period;
        private final String periodLabel;

        private BigDecimal quantityIn = ZERO;
        private BigDecimal quantityOut = ZERO;
        private BigDecimal purchaseAmount = ZERO;
        private BigDecimal salesAmount = ZERO;
        private BigDecimal totalCostIn = ZERO;
        private BigDecimal totalCostOut = ZERO;

        private BigDecimal firstStockBefore;
        private BigDecimal lastStockAfter;
        private BigDecimal lastAverageCost;
        private int movementCount = 0;

        private TrendPointAccumulator(String period, String periodLabel) {
            this.period = period;
            this.periodLabel = periodLabel;
        }

        private void add(ProductKardexTrendMovement movement) {
            BigDecimal in = movement.quantityIn() == null ? ZERO : movement.quantityIn();
            BigDecimal out = movement.quantityOut() == null ? ZERO : movement.quantityOut();
            BigDecimal totalCost = movement.totalCost() == null ? ZERO : movement.totalCost();
            BigDecimal sourceLineTotal = movement.sourceLineTotal() == null ? ZERO : movement.sourceLineTotal();

            if (movementCount == 0) {
                firstStockBefore = movement.stockBefore();
            }

            quantityIn = quantityIn.add(in);
            quantityOut = quantityOut.add(out);

            if (in.compareTo(ZERO) > 0) {
                totalCostIn = totalCostIn.add(totalCost);
            }
            if (out.compareTo(ZERO) > 0) {
                totalCostOut = totalCostOut.add(totalCost);
            }

            if ("purchase_item".equals(movement.sourceTable())) {
                purchaseAmount = purchaseAmount.add(sourceLineTotal);
            }
            if ("sale_item".equals(movement.sourceTable()) || "counter_sale_item".equals(movement.sourceTable())) {
                salesAmount = salesAmount.add(sourceLineTotal);
            }

            if (movement.stockAfter() != null) {
                lastStockAfter = movement.stockAfter();
            }
            if (movement.averageCostAfter() != null) {
                lastAverageCost = movement.averageCostAfter();
            }

            movementCount++;
        }

        private ProductKardexTrendPointResponse toResponse(BigDecimal carryStock, BigDecimal carryAverageCost) {
            BigDecimal initialStock = firstStockBefore != null ? firstStockBefore : carryStock;
            BigDecimal finalStock = lastStockAfter != null
                    ? lastStockAfter
                    : initialStock.add(quantityIn).subtract(quantityOut);
            BigDecimal averageCost = lastAverageCost != null ? lastAverageCost : carryAverageCost;

            return new ProductKardexTrendPointResponse(
                    period,
                    periodLabel,
                    quantityIn,
                    quantityOut,
                    quantityIn.subtract(quantityOut),
                    initialStock,
                    finalStock,
                    averageCost,
                    purchaseAmount,
                    salesAmount,
                    totalCostIn,
                    totalCostOut,
                    movementCount
            );
        }
    }
}
