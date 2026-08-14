package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import com.paulfernandosr.possystembackend.product.domain.InventoryReportExportRequest;
import com.paulfernandosr.possystembackend.product.domain.InventoryReportFormat;
import com.paulfernandosr.possystembackend.product.domain.ProductExistenceType;
import com.paulfernandosr.possystembackend.product.domain.ProductKardexEntry;
import com.paulfernandosr.possystembackend.product.domain.port.input.ExportInventoryReportUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductKardexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportInventoryReportService implements ExportInventoryReportUseCase {

    private static final int MAX_PRODUCTS_PER_EXPORT = 5000;
    private static final String UNIT_CODE = "07";
    private static final String VALUATION_METHOD = "PROMEDIO PONDERADO";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProductKardexRepository productKardexRepository;
    private final GuideRemissionProperties guideRemissionProperties;

    @Override
    public byte[] export(InventoryReportExportRequest request) {
        validate(request);

        long startNanos = System.nanoTime();
        InventoryReportData reportData = loadReportData(request);
        long dataLoadedNanos = System.nanoTime();

        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        try (workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet(request.format() == InventoryReportFormat.VALUED ? "Formato 13.1" : "Formato 12.1");
            ReportStyles styles = createStyles(workbook);
            configureColumns(sheet, request.format());

            int rowIndex = 0;
            for (ProductKardexEntry product : reportData.products()) {
                rowIndex = appendProductBlock(
                        sheet,
                        rowIndex,
                        request,
                        product,
                        reportData.movementsByProduct().getOrDefault(product.getProductId(), List.of()),
                        styles
                );
                rowIndex += 5;
            }

            workbook.write(out);
            log.info(
                    "[kardex-inventory-export] format={}, from={}, to={}, includeAll={}, products={}, movements={}, loadMs={}, xlsxMs={}, totalMs={}",
                    request.format(),
                    request.dateFrom(),
                    request.dateTo(),
                    request.includeAllProductsWithMovements(),
                    reportData.products().size(),
                    reportData.movementCount(),
                    millisBetween(startNanos, dataLoadedNanos),
                    millisBetween(dataLoadedNanos, System.nanoTime()),
                    millisBetween(startNanos, System.nanoTime())
            );
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el registro de inventario permanente.", ex);
        } finally {
            workbook.dispose();
        }
    }

    private InventoryReportData loadReportData(InventoryReportExportRequest request) {
        if (Boolean.TRUE.equals(request.includeAllProductsWithMovements())) {
            List<ProductKardexEntry> movements = productKardexRepository.findInventoryReportMovementsForAllProducts(
                    request.dateFrom(),
                    request.dateTo()
            );
            return new InventoryReportData(productsFromMovements(movements), groupMovements(movements), movements.size());
        }

        List<Long> productIds = normalizeIds(request.productIds());
        List<ProductKardexEntry> products = productKardexRepository.findInventoryReportProducts(productIds);
        List<Long> movementProductIds = products.stream()
                .map(ProductKardexEntry::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<ProductKardexEntry> movements = productKardexRepository.findInventoryReportMovements(
                movementProductIds,
                request.dateFrom(),
                request.dateTo()
        );
        return new InventoryReportData(products, groupMovements(movements), movements.size());
    }

    private void validate(InventoryReportExportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud es obligatoria.");
        }
        if (request.format() == null) {
            throw new IllegalArgumentException("El formato es obligatorio.");
        }
        if (request.dateFrom() == null || request.dateTo() == null) {
            throw new IllegalArgumentException("El rango de fechas es obligatorio.");
        }
        if (request.dateFrom().isAfter(request.dateTo())) {
            throw new IllegalArgumentException("La fecha inicial no puede ser mayor que la fecha final.");
        }
        if (Boolean.TRUE.equals(request.includeAllProductsWithMovements())) {
            return;
        }
        if (request.productIds() == null || request.productIds().isEmpty()) {
            throw new IllegalArgumentException("Seleccione al menos un producto.");
        }
        if (request.productIds().size() > MAX_PRODUCTS_PER_EXPORT) {
            throw new IllegalArgumentException("El export permite como maximo " + MAX_PRODUCTS_PER_EXPORT + " productos.");
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }

    private Map<Long, List<ProductKardexEntry>> groupMovements(List<ProductKardexEntry> movements) {
        Map<Long, List<ProductKardexEntry>> grouped = new LinkedHashMap<>();
        for (ProductKardexEntry movement : movements) {
            grouped.computeIfAbsent(movement.getProductId(), ignored -> new ArrayList<>()).add(movement);
        }
        return grouped;
    }

    private List<ProductKardexEntry> productsFromMovements(List<ProductKardexEntry> movements) {
        Map<Long, ProductKardexEntry> products = new LinkedHashMap<>();
        for (ProductKardexEntry movement : movements) {
            Long productId = movement.getProductId();
            if (productId == null || products.containsKey(productId)) {
                continue;
            }
            products.put(productId, ProductKardexEntry.builder()
                    .id(productId)
                    .productId(productId)
                    .sku(movement.getSku())
                    .productName(movement.getProductName())
                    .category(movement.getCategory())
                    .brand(movement.getBrand())
                    .model(movement.getModel())
                    .presentation(movement.getPresentation())
                    .manageBySerial(movement.getManageBySerial())
                    .existenceTypeCode(movement.getExistenceTypeCode())
                    .build());
        }
        return new ArrayList<>(products.values());
    }

    private int appendProductBlock(
            Sheet sheet,
            int startRow,
            InventoryReportExportRequest request,
            ProductKardexEntry product,
            List<ProductKardexEntry> movements,
            ReportStyles styles
    ) {
        boolean valued = request.format() == InventoryReportFormat.VALUED;
        int lastColumn = valued ? 13 : 7;
        int rowIndex = startRow;

        Row titleRow = sheet.createRow(rowIndex++);
        titleRow.setHeightInPoints(titleRowHeight(request.format()));
        mergeAndSet(titleRow, 0, lastColumn, reportTitle(request.format()), styles.title);
        rowIndex++;

        rowIndex = appendInfo(sheet, rowIndex, lastColumn, "PERIODO:", periodLabel(request.dateFrom(), request.dateTo()), styles.bold, styles.text);
        rowIndex = appendInfo(sheet, rowIndex, lastColumn, "RUC:", companyRuc(), styles.bold, styles.text);
        rowIndex = appendInfo(sheet, rowIndex, lastColumn, "APELLIDOS Y NOMBRES, DENOMINACION O RAZON SOCIAL:", companyName(), styles.bold, styles.text);
        rowIndex = appendInfo(sheet, rowIndex, lastColumn, "ESTABLECIMIENTO (1):", companyAddress(), styles.bold, styles.text);
        rowIndex = appendInfo(sheet, rowIndex, lastColumn, "CODIGO DE LA EXISTENCIA:", text(product.getSku()), styles.bold, styles.text);
        rowIndex = appendInfo(sheet, rowIndex, lastColumn, "TIPO (TABLA 5):", existenceLabel(product.getExistenceTypeCode()), styles.bold, styles.text);
        rowIndex = appendInfo(sheet, rowIndex, lastColumn, "DESCRIPCION:", text(product.getProductName()), styles.bold, styles.text);
        rowIndex = appendInfo(sheet, rowIndex, lastColumn, "CODIGO DE LA UNIDAD DE MEDIDA (TABLA 6):", UNIT_CODE, styles.bold, styles.text);
        if (valued) {
            rowIndex = appendInfo(sheet, rowIndex, lastColumn, "METODO DE VALUACION:", VALUATION_METHOD, styles.bold, styles.text);
        }

        rowIndex++;
        rowIndex = valued
                ? appendValuedHeader(sheet, rowIndex, styles.header)
                : appendPhysicalHeader(sheet, rowIndex, styles.header);

        if (movements.isEmpty()) {
            Row row = sheet.createRow(rowIndex++);
            mergeAndSet(row, 0, lastColumn, "Sin movimientos en el rango seleccionado.", styles.note);
            rowIndex = valued
                    ? appendValuedTotalsRow(sheet, rowIndex, List.of(), styles)
                    : appendPhysicalTotalsRow(sheet, rowIndex, List.of(), styles);
            return rowIndex;
        }

        for (ProductKardexEntry movement : movements) {
            Row row = sheet.createRow(rowIndex++);
            if (valued) {
                appendValuedMovement(row, movement, styles);
            } else {
                appendPhysicalMovement(row, movement, styles);
            }
        }

        rowIndex = valued
                ? appendValuedTotalsRow(sheet, rowIndex, movements, styles)
                : appendPhysicalTotalsRow(sheet, rowIndex, movements, styles);

        return rowIndex;
    }

    private int appendValuedTotalsRow(Sheet sheet, int rowIndex, List<ProductKardexEntry> movements, ReportStyles styles) {
        Row row = sheet.createRow(rowIndex++);
        row.setHeightInPoints(22f);

        mergeAndSet(row, 0, 4, "TOTALES", styles.totalLabel);
        setNumber(row, 5, sum(movements, ProductKardexEntry::getQuantityIn), styles.totalQuantity, true);
        setCell(row, 6, "", styles.totalMoney);
        setNumber(row, 7, sumEntryTotal(movements), styles.totalMoney, true);
        setNumber(row, 8, sum(movements, ProductKardexEntry::getQuantityOut), styles.totalQuantity, true);
        setCell(row, 9, "", styles.totalMoney);
        setNumber(row, 10, sumExitTotal(movements), styles.totalMoney, true);

        ProductKardexEntry last = lastMovement(movements);
        setNumber(row, 11, last != null ? last.getStockAfter() : BigDecimal.ZERO, styles.totalQuantity, true);
        setNumber(row, 12, last != null ? last.getAverageCostAfter() : BigDecimal.ZERO, styles.totalMoney, true);
        setNumber(row, 13, last != null ? balanceTotal(last) : BigDecimal.ZERO, styles.totalMoney, true);
        return rowIndex;
    }

    private int appendPhysicalTotalsRow(Sheet sheet, int rowIndex, List<ProductKardexEntry> movements, ReportStyles styles) {
        Row row = sheet.createRow(rowIndex++);
        row.setHeightInPoints(22f);

        mergeAndSet(row, 0, 4, "TOTALES", styles.totalLabel);
        setNumber(row, 5, sum(movements, ProductKardexEntry::getQuantityIn), styles.totalQuantity, true);
        setNumber(row, 6, sum(movements, ProductKardexEntry::getQuantityOut), styles.totalQuantity, true);

        ProductKardexEntry last = lastMovement(movements);
        setNumber(row, 7, last != null ? last.getStockAfter() : BigDecimal.ZERO, styles.totalQuantity, true);
        return rowIndex;
    }

    private BigDecimal sum(List<ProductKardexEntry> movements, java.util.function.Function<ProductKardexEntry, BigDecimal> extractor) {
        BigDecimal total = BigDecimal.ZERO;
        for (ProductKardexEntry movement : movements) {
            if (movement == null) continue;
            BigDecimal value = extractor.apply(movement);
            if (value != null) {
                total = total.add(value);
            }
        }
        return total;
    }

    private BigDecimal sumEntryTotal(List<ProductKardexEntry> movements) {
        BigDecimal total = BigDecimal.ZERO;
        for (ProductKardexEntry movement : movements) {
            if (movement != null && positive(movement.getQuantityIn())) {
                BigDecimal value = entryTotalValue(movement);
                if (value != null) {
                    total = total.add(value);
                }
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumExitTotal(List<ProductKardexEntry> movements) {
        BigDecimal total = BigDecimal.ZERO;
        for (ProductKardexEntry movement : movements) {
            if (movement != null && positive(movement.getQuantityOut())) {
                BigDecimal value = firstNonNull(movement.getSourceLineTotal(), movement.getTotalCost());
                if (value != null) {
                    total = total.add(value);
                }
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private ProductKardexEntry lastMovement(List<ProductKardexEntry> movements) {
        if (movements == null || movements.isEmpty()) {
            return null;
        }
        return movements.get(movements.size() - 1);
    }

    private String reportTitle(InventoryReportFormat format) {
        if (format == InventoryReportFormat.VALUED) {
            return "FORMATO 13.1: \"REGISTRO DE INVENTARIO PERMANENTE VALORIZADO - DETALLE DEL INVENTARIO VALORIZADO\"";
        }
        return "FORMATO 12.1: \"REGISTRO DEL INVENTARIO PERMANENTE EN UNIDADES FISICAS - DETALLE DEL\nINVENTARIO PERMANENTE EN UNIDADES FISICAS\"";
    }

    private float titleRowHeight(InventoryReportFormat format) {
        return format == InventoryReportFormat.VALUED ? 24f : 42f;
    }

    private int appendInfo(Sheet sheet, int rowIndex, int lastColumn, String label, String value, CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex++);
        row.setHeightInPoints(estimateInfoRowHeight(label, value, lastColumn));

        int labelLastColumn = Math.min(3, Math.max(0, lastColumn - 1));
        int valueFirstColumn = Math.min(labelLastColumn + 1, lastColumn);

        mergeAndSet(row, 0, labelLastColumn, label, labelStyle);
        mergeAndSet(row, valueFirstColumn, lastColumn, value, valueStyle);
        return rowIndex;
    }

    private float estimateInfoRowHeight(String label, String value, int lastColumn) {
        int labelWidth = 42;
        int valueColumns = Math.max(1, lastColumn - 3);
        int valueWidth = valueColumns * 14;
        int labelLines = Math.max(1, (text(label).length() + labelWidth - 1) / labelWidth);
        int valueLines = Math.max(1, (text(value).length() + valueWidth - 1) / valueWidth);
        int lines = Math.min(3, Math.max(labelLines, valueLines));
        return 18f + ((float) lines - 1f) * 8f;
    }

    private int appendValuedHeader(Sheet sheet, int rowIndex, CellStyle headerStyle) {
        Row group = sheet.createRow(rowIndex++);
        Row columns = sheet.createRow(rowIndex++);
        group.setHeightInPoints(42f);
        columns.setHeightInPoints(34f);

        mergeAndSet(group, 0, 3, "DOCUMENTO DE TRASLADO, COMPROBANTE DE PAGO, DOCUMENTO INTERNO O SIMILAR", headerStyle);
        mergeAndSet(group, 4, 4, "TIPO DE OPERACION", headerStyle);
        mergeAndSet(group, 5, 7, "ENTRADAS", headerStyle);
        mergeAndSet(group, 8, 10, "SALIDAS", headerStyle);
        mergeAndSet(group, 11, 13, "SALDO FINAL", headerStyle);

        String[] headers = {
                "FECHA", "TIPO (TABLA 10)", "SERIE", "NUMERO", "(TABLA 12)",
                "CANTIDAD", "COSTO\nUNITARIO", "COSTO\nTOTAL",
                "CANTIDAD", "PRECIO\nUNITARIO", "PRECIO\nTOTAL",
                "CANTIDAD", "COSTO\nUNITARIO", "COSTO\nTOTAL"
        };
        for (int i = 0; i < headers.length; i++) {
            setCell(columns, i, headers[i], headerStyle);
        }
        return rowIndex;
    }

    private int appendPhysicalHeader(Sheet sheet, int rowIndex, CellStyle headerStyle) {
        Row group = sheet.createRow(rowIndex++);
        Row columns = sheet.createRow(rowIndex++);
        group.setHeightInPoints(36f);
        columns.setHeightInPoints(28f);

        mergeAndSet(group, 0, 3, "DOCUMENTO DE TRASLADO, COMPROBANTE DE PAGO, DOCUMENTO INTERNO O SIMILAR", headerStyle);
        mergeAndSet(group, 4, 4, "TIPO DE OPERACION", headerStyle);
        mergeAndSet(group, 5, 5, "ENTRADAS", headerStyle);
        mergeAndSet(group, 6, 6, "SALIDAS", headerStyle);
        mergeAndSet(group, 7, 7, "SALDO FINAL", headerStyle);

        String[] headers = {"FECHA", "TIPO (TABLA 10)", "SERIE", "NUMERO", "(TABLA 12)", "ENTRADAS", "SALIDAS", "SALDO FINAL"};
        for (int i = 0; i < headers.length; i++) {
            setCell(columns, i, headers[i], headerStyle);
        }
        return rowIndex;
    }

    private void appendValuedMovement(Row row, ProductKardexEntry movement, ReportStyles styles) {
        setCell(row, 0, dateLabel(movement), styles.date);
        setCell(row, 1, documentTypeCode(movement.getSourceDocumentType()), styles.table);
        setCell(row, 2, text(movement.getSourceSeries()), styles.table);
        setCell(row, 3, text(movement.getSourceNumber()), styles.table);
        setCell(row, 4, operationTypeCode(movement), styles.table);

        setNumber(row, 5, movement.getQuantityIn(), styles.quantity);
        setNumber(row, 6, positive(movement.getQuantityIn()) ? entryUnitValue(movement) : null, styles.money);
        setNumber(row, 7, positive(movement.getQuantityIn()) ? entryTotalValue(movement) : null, styles.money);

        setNumber(row, 8, movement.getQuantityOut(), styles.quantity);
        setNumber(row, 9, positive(movement.getQuantityOut()) ? firstNonNull(movement.getSourceUnitPrice(), movement.getUnitCost()) : null, styles.money);
        setNumber(row, 10, positive(movement.getQuantityOut()) ? firstNonNull(movement.getSourceLineTotal(), movement.getTotalCost()) : null, styles.money);

        setNumber(row, 11, movement.getStockAfter(), styles.quantity);
        setNumber(row, 12, movement.getAverageCostAfter(), styles.money);
        setNumber(row, 13, balanceTotal(movement), styles.money);
    }

    private void appendPhysicalMovement(Row row, ProductKardexEntry movement, ReportStyles styles) {
        setCell(row, 0, dateLabel(movement), styles.date);
        setCell(row, 1, documentTypeCode(movement.getSourceDocumentType()), styles.table);
        setCell(row, 2, text(movement.getSourceSeries()), styles.table);
        setCell(row, 3, text(movement.getSourceNumber()), styles.table);
        setCell(row, 4, operationTypeCode(movement), styles.table);
        setNumber(row, 5, movement.getQuantityIn(), styles.quantity);
        setNumber(row, 6, movement.getQuantityOut(), styles.quantity);
        setNumber(row, 7, movement.getStockAfter(), styles.quantity);
    }

    private BigDecimal balanceTotal(ProductKardexEntry movement) {
        BigDecimal stock = movement.getStockAfter();
        BigDecimal averageCost = movement.getAverageCostAfter();
        if (stock == null || averageCost == null) {
            return null;
        }
        return stock.multiply(averageCost).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal firstNonNull(BigDecimal preferred, BigDecimal fallback) {
        return preferred != null ? preferred : fallback;
    }

    private BigDecimal entryUnitValue(ProductKardexEntry movement) {
        if ("credit_note_item".equals(text(movement.getSourceTable()))) {
            return firstNonNull(movement.getSourceUnitPrice(), movement.getUnitCost());
        }
        return movement.getUnitCost();
    }

    private BigDecimal entryTotalValue(ProductKardexEntry movement) {
        if ("credit_note_item".equals(text(movement.getSourceTable()))) {
            return firstNonNull(movement.getSourceLineTotal(), movement.getTotalCost());
        }
        return movement.getTotalCost();
    }

    private String dateLabel(ProductKardexEntry movement) {
        if (movement.getSourceIssueDate() != null) {
            return DATE_FORMAT.format(movement.getSourceIssueDate());
        }
        if (movement.getMovementDate() != null) {
            return DATE_FORMAT.format(movement.getMovementDate().toLocalDate());
        }
        return "";
    }

    private String documentTypeCode(String sourceDocumentType) {
        String value = text(sourceDocumentType).toUpperCase();
        if (value.contains("FACTURA")) return "01";
        if (value.contains("BOLETA")) return "03";
        if (value.contains("NOTA") && value.contains("CRED")) return "07";
        return "";
    }

    private String operationTypeCode(ProductKardexEntry movement) {
        String sourceTable = text(movement.getSourceTable());
        String movementType = text(movement.getMovementType()).toUpperCase();

        if (positive(movement.getQuantityIn()) && movementType.contains("RETURN")) return "05";
        if ("purchase_item".equals(sourceTable)) return "02";
        if ("sale_item".equals(sourceTable) || "counter_sale_item".equals(sourceTable)) return "01";
        if (positive(movement.getQuantityIn()) && movementType.contains("VOID")) return "05";
        return "99";
    }

    private String existenceLabel(String code) {
        String normalized = ProductExistenceType.normalize(code);
        return normalized + " - " + ProductExistenceType.description(normalized);
    }

    private String periodLabel(LocalDate from, LocalDate to) {
        return DATE_FORMAT.format(from) + " AL " + DATE_FORMAT.format(to);
    }

    private String companyRuc() {
        return text(guideRemissionProperties.getCompany().getRuc());
    }

    private String companyName() {
        return text(guideRemissionProperties.getCompany().getRazonSocial());
    }

    private String companyAddress() {
        return text(guideRemissionProperties.getCompany().getDomicilioFiscal());
    }

    private void configureColumns(Sheet sheet, InventoryReportFormat format) {
        int[] widths = format == InventoryReportFormat.VALUED
                ? new int[]{13, 17, 14, 16, 18, 16, 17, 17, 16, 17, 17, 16, 17, 17}
                : new int[]{14, 18, 14, 18, 18, 16, 16, 18};

        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private ReportStyles createStyles(Workbook workbook) {
        DataFormat dataFormat = workbook.createDataFormat();

        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 13);

        Font boldFont = workbook.createFont();
        boldFont.setBold(true);

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setWrapText(true);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle bold = workbook.createCellStyle();
        bold.setFont(boldFont);
        bold.setWrapText(true);
        bold.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle text = workbook.createCellStyle();
        text.setWrapText(true);
        text.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle header = bordered(workbook);
        header.setFont(boldFont);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);

        CellStyle table = bordered(workbook);
        table.setWrapText(true);

        CellStyle date = bordered(workbook);
        date.setAlignment(HorizontalAlignment.CENTER);

        CellStyle quantity = bordered(workbook);
        quantity.setDataFormat(dataFormat.getFormat("#,##0.####"));
        quantity.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle money = bordered(workbook);
        money.setDataFormat(dataFormat.getFormat("#,##0.00"));
        money.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle note = bordered(workbook);
        note.setAlignment(HorizontalAlignment.CENTER);
        note.setWrapText(true);

        CellStyle totalLabel = bordered(workbook);
        totalLabel.setFont(boldFont);
        totalLabel.setAlignment(HorizontalAlignment.RIGHT);
        totalLabel.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle totalQuantity = bordered(workbook);
        totalQuantity.setFont(boldFont);
        totalQuantity.setDataFormat(dataFormat.getFormat("#,##0.####"));
        totalQuantity.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle totalMoney = bordered(workbook);
        totalMoney.setFont(boldFont);
        totalMoney.setDataFormat(dataFormat.getFormat("#,##0.00"));
        totalMoney.setAlignment(HorizontalAlignment.RIGHT);

        return new ReportStyles(title, bold, text, header, table, date, quantity, money, note, totalLabel, totalQuantity, totalMoney);
    }

    private CellStyle bordered(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private void mergeAndSet(Sheet sheet, int rowIndex, int firstColumn, int lastColumn, String value, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        mergeAndSet(row, firstColumn, lastColumn, value, style);
    }

    private void mergeAndSet(Row row, int firstColumn, int lastColumn, String value, CellStyle style) {
        Sheet sheet = row.getSheet();
        if (lastColumn > firstColumn) {
            sheet.addMergedRegionUnsafe(new CellRangeAddress(row.getRowNum(), row.getRowNum(), firstColumn, lastColumn));
        }
        setCell(row, firstColumn, value, style);
        for (int column = firstColumn + 1; column <= lastColumn; column++) {
            setCell(row, column, "", style);
        }
    }

    private void setCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void setNumber(Row row, int column, BigDecimal value, CellStyle style) {
        setNumber(row, column, value, style, false);
    }

    private void setNumber(Row row, int column, BigDecimal value, CellStyle style, boolean showZero) {
        Cell cell = row.createCell(column);
        if (value != null && (showZero || value.compareTo(BigDecimal.ZERO) != 0)) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setBlank();
        }
        cell.setCellStyle(style);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private long millisBetween(long startNanos, long endNanos) {
        return (endNanos - startNanos) / 1_000_000L;
    }

    private record ReportStyles(
            CellStyle title,
            CellStyle bold,
            CellStyle text,
            CellStyle header,
            CellStyle table,
            CellStyle date,
            CellStyle quantity,
            CellStyle money,
            CellStyle note,
            CellStyle totalLabel,
            CellStyle totalQuantity,
            CellStyle totalMoney
    ) {
    }

    private record InventoryReportData(
            List<ProductKardexEntry> products,
            Map<Long, List<ProductKardexEntry>> movementsByProduct,
            int movementCount
    ) {
    }
}
