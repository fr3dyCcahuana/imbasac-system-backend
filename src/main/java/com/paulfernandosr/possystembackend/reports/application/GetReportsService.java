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
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceDetailResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerPerformanceResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerCommissionConfigRequest;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SellerCommissionConfigResponse;
import com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto.SunatComparisonResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    private static final int PRODUCT_TOP_MAX_LIMIT = 500;
    private static final int PRODUCT_TOP_EXCEL_MAX_LIMIT = 5000;
    private static final BigDecimal SELLER_INCENTIVE_THRESHOLD = new BigDecimal("50000.00");
    private static final BigDecimal DEFAULT_BAJAJ_RATE = new BigDecimal("0.005000");
    private static final BigDecimal DEFAULT_KTM_RATE = new BigDecimal("0.003000");
    private static final BigDecimal DEFAULT_IMBA_RATE = new BigDecimal("0.000200");
    private static final BigDecimal DEFAULT_BON_RATE = new BigDecimal("0.000200");
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
        int safeLimit = normalizeProductTopLimit(limit);
        return reportsRepository.findProductsTop(from, to, normalizedSort, safeLimit);
    }

    @Override
    public byte[] getProductsTopExcel(LocalDate from, LocalDate to, int limit) {
        validateRange(from, to);
        int safeLimit = normalizeProductTopExcelLimit(limit);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle textStyle = workbook.createCellStyle();
            CellStyle integerStyle = createNumberStyle(workbook, "#,##0");
            CellStyle decimalStyle = createNumberStyle(workbook, "#,##0.00");
            CellStyle currencyStyle = createNumberStyle(workbook, "\"S/\" #,##0.00");
            RowStyles defaultStyles = new RowStyles(textStyle, integerStyle, decimalStyle, currencyStyle);
            RowStyles warningStyles = createStockRowStyles(workbook, defaultStyles, IndexedColors.LIGHT_YELLOW);
            RowStyles criticalStyles = createStockRowStyles(workbook, defaultStyles, IndexedColors.ROSE);

            appendProductsSheet(
                    workbook,
                    "Cantidad",
                    "Ranking por cantidad",
                    from,
                    to,
                    reportsRepository.findProductsTop(from, to, "QTY", safeLimit),
                    titleStyle,
                    headerStyle,
                    defaultStyles,
                    warningStyles,
                    criticalStyles
            );
            appendProductsSheet(
                    workbook,
                    "Ventas",
                    "Ranking por ventas",
                    from,
                    to,
                    reportsRepository.findProductsTop(from, to, "REVENUE", safeLimit),
                    titleStyle,
                    headerStyle,
                    defaultStyles,
                    warningStyles,
                    criticalStyles
            );
            appendProductsSheet(
                    workbook,
                    "Ganancia",
                    "Ranking por ganancia",
                    from,
                    to,
                    reportsRepository.findProductsTop(from, to, "PROFIT", safeLimit),
                    titleStyle,
                    headerStyle,
                    defaultStyles,
                    warningStyles,
                    criticalStyles
            );

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el reporte Excel de productos ganadores.", ex);
        }
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

    @Override
    public SellerPerformanceDetailResponse getSellerPerformanceDetail(LocalDate from, LocalDate to, Long sellerId) {
        validateRange(from, to);
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("sellerId es obligatorio.");
        }
        SellerCommissionConfigResponse config = reportsRepository.findSellerCommissionConfig(sellerId);
        SellerPerformanceDetailResponse detail = reportsRepository.findSellerPerformanceDetail(from, to, sellerId, config);
        detail.setCommissionConfig(config);
        return detail;
    }

    @Override
    public SellerCommissionConfigResponse saveSellerCommissionConfig(Long sellerId, SellerCommissionConfigRequest request) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("sellerId es obligatorio.");
        }
        if (request == null) {
            throw new IllegalArgumentException("La configuracion es obligatoria.");
        }

        return reportsRepository.saveSellerCommissionConfig(SellerCommissionConfigResponse.builder()
                .sellerId(sellerId)
                .monthlyGoal(positive(request.monthlyGoal(), SELLER_INCENTIVE_THRESHOLD, "monthlyGoal"))
                .bajajRate(nonNegative(request.bajajRate(), DEFAULT_BAJAJ_RATE, "bajajRate"))
                .ktmRate(nonNegative(request.ktmRate(), DEFAULT_KTM_RATE, "ktmRate"))
                .imbaRate(nonNegative(request.imbaRate(), DEFAULT_IMBA_RATE, "imbaRate"))
                .bonRate(nonNegative(request.bonRate(), DEFAULT_BON_RATE, "bonRate"))
                .customized(true)
                .build());
    }

    private BigDecimal nonNegative(BigDecimal value, BigDecimal fallback, String field) {
        BigDecimal result = value == null ? fallback : value;
        if (result.signum() < 0) {
            throw new IllegalArgumentException(field + " no puede ser negativo.");
        }
        return result;
    }

    private BigDecimal positive(BigDecimal value, BigDecimal fallback, String field) {
        BigDecimal result = value == null ? fallback : value;
        if (result.signum() <= 0) {
            throw new IllegalArgumentException(field + " debe ser mayor que cero.");
        }
        return result;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from y to son obligatorios.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from no puede ser mayor que to.");
        }
    }

    private int normalizeProductTopLimit(int limit) {
        return Math.max(1, Math.min(limit, PRODUCT_TOP_MAX_LIMIT));
    }

    private int normalizeProductTopExcelLimit(int limit) {
        return Math.max(1, Math.min(limit, PRODUCT_TOP_EXCEL_MAX_LIMIT));
    }

    private void appendProductsSheet(Workbook workbook,
                                     String sheetName,
                                     String title,
                                     LocalDate from,
                                     LocalDate to,
                                     List<ProductTopResponse> rows,
                                     CellStyle titleStyle,
                                     CellStyle headerStyle,
                                     RowStyles defaultStyles,
                                     RowStyles warningStyles,
                                     RowStyles criticalStyles) {
        Sheet sheet = workbook.createSheet(sheetName);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(titleStyle);

        Row rangeRow = sheet.createRow(1);
        rangeRow.createCell(0).setCellValue("Periodo");
        rangeRow.createCell(1).setCellValue(from + " al " + to);

        Row header = sheet.createRow(3);
        String[] headers = {
                "Ranking",
                "SKU",
                "Producto",
                "Marca",
                "Categoria",
                "Modelo",
                "Unidades",
                "Stock",
                "Ventas",
                "Ganancia",
                "Ventas asociadas"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int i = 0; i < rows.size(); i++) {
            ProductTopResponse product = rows.get(i);
            Row row = sheet.createRow(i + 4);
            RowStyles rowStyles = resolveStockRowStyles(product.getStockAvailable(), defaultStyles, warningStyles, criticalStyles);

            writeNumber(row, 0, i + 1, rowStyles.integerStyle());
            writeText(row, 1, product.getProductSku(), rowStyles.textStyle());
            writeText(row, 2, product.getProductName(), rowStyles.textStyle());
            writeText(row, 3, product.getBrand(), rowStyles.textStyle());
            writeText(row, 4, product.getCategory(), rowStyles.textStyle());
            writeText(row, 5, product.getModel(), rowStyles.textStyle());
            writeBigDecimal(row, 6, product.getTotalQty(), rowStyles.decimalStyle());
            writeBigDecimal(row, 7, product.getStockAvailable(), rowStyles.decimalStyle());
            writeBigDecimal(row, 8, product.getTotalSales(), rowStyles.currencyStyle());
            writeBigDecimal(row, 9, product.getTotalProfit(), rowStyles.currencyStyle());
            writeNumber(row, 10, product.getCountSales(), rowStyles.integerStyle());
        }

        sheet.createFreezePane(0, 4);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(3, Math.max(4, rows.size() + 3), 0, headers.length - 1));

        int[] widths = {10, 12, 44, 18, 18, 18, 14, 14, 16, 16, 18};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        return style;
    }

    private RowStyles createStockRowStyles(Workbook workbook, RowStyles baseStyles, IndexedColors fillColor) {
        return new RowStyles(
                createFilledStyle(workbook, baseStyles.textStyle(), fillColor),
                createFilledStyle(workbook, baseStyles.integerStyle(), fillColor),
                createFilledStyle(workbook, baseStyles.decimalStyle(), fillColor),
                createFilledStyle(workbook, baseStyles.currencyStyle(), fillColor)
        );
    }

    private CellStyle createFilledStyle(Workbook workbook, CellStyle baseStyle, IndexedColors fillColor) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        style.setFillForegroundColor(fillColor.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private RowStyles resolveStockRowStyles(BigDecimal stockAvailable,
                                            RowStyles defaultStyles,
                                            RowStyles warningStyles,
                                            RowStyles criticalStyles) {
        BigDecimal stock = nz(stockAvailable);
        if (stock.compareTo(new BigDecimal("10")) < 0) {
            return criticalStyles;
        }
        if (stock.compareTo(new BigDecimal("20")) < 0) {
            return warningStyles;
        }
        return defaultStyles;
    }

    private void writeBigDecimal(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(nz(value).doubleValue());
        cell.setCellStyle(style);
    }

    private void writeNumber(Row row, int column, Number value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0 : value.doubleValue());
        cell.setCellStyle(style);
    }

    private void writeText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
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

    private record RowStyles(CellStyle textStyle,
                             CellStyle integerStyle,
                             CellStyle decimalStyle,
                             CellStyle currencyStyle) {
    }
}
