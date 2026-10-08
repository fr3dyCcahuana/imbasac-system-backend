package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.product.domain.ProductCatalogExcelExportRequest;
import com.paulfernandosr.possystembackend.product.domain.port.input.ExportProductCatalogExcelUseCase;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExportProductCatalogExcelService implements ExportProductCatalogExcelUseCase {

    private static final Set<String> ALLOWED_PRICE_TYPES = Set.of("A", "B", "C", "D");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public byte[] export(ProductCatalogExcelExportRequest request) {
        ExportOptions options = normalize(request);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(250); ByteArrayOutputStream outputStream = new ByteArrayOutputStream(256 * 1024)) {
            workbook.setCompressTempFiles(true);
            SXSSFSheet sheet = workbook.createSheet("PRODUCTOS");

            ExportStyles styles = createStyles(workbook);
            writeHeader(sheet, options, styles);
            applyColumnWidths(sheet, options);

            int[] rowIndex = {1};
            jdbcTemplate.query(
                    connection -> {
                        PreparedStatement ps = connection.prepareStatement(
                                buildSql(),
                                java.sql.ResultSet.TYPE_FORWARD_ONLY,
                                java.sql.ResultSet.CONCUR_READ_ONLY
                        );
                        ps.setFetchSize(500);
                        bindParams(ps, options);
                        return ps;
                    },
                    rs -> {
                        Row row = sheet.createRow(rowIndex[0]++);
                        int col = 0;
                        writeText(row, col++, rs.getString("sku"), styles.text);
                        writeText(row, col++, rs.getString("name"), styles.text);
                        writeText(row, col++, rs.getString("brand"), styles.text);
                        writeText(row, col++, rs.getString("model"), styles.text);
                        writeText(row, col++, rs.getString("compatibility"), styles.text);
                        writeText(row, col++, rs.getString("category"), styles.text);
                        writeText(row, col++, rs.getString("presentation"), styles.text);

                        if (options.includeStock()) {
                            writeNumber(row, col++, rs.getBigDecimal("stock_on_hand"), styles.quantity);
                        }

                        for (String priceType : options.priceTypes()) {
                            writeNumber(row, col++, rs.getBigDecimal("price_" + priceType.toLowerCase(Locale.ROOT)), styles.priceCell(priceType));
                        }
                    }
            );

            int lastColumn = options.totalColumns() - 1;
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(1, rowIndex[0] - 1), 0, lastColumn));
            workbook.write(outputStream);
            workbook.dispose();
            return outputStream.toByteArray();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el Excel de productos.", e);
        }
    }

    private ExportOptions normalize(ProductCatalogExcelExportRequest request) {
        ProductCatalogExcelExportRequest safe = request == null ? new ProductCatalogExcelExportRequest() : request;
        String stock = text(safe.getStock()).toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "IN", "OUT").contains(stock)) {
            stock = "ALL";
        }

        LinkedHashSet<String> priceTypes = new LinkedHashSet<>();
        for (String item : safe.getPriceTypes() == null ? List.<String>of() : safe.getPriceTypes()) {
            String code = text(item).toUpperCase(Locale.ROOT);
            if (!ALLOWED_PRICE_TYPES.contains(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista de precio no valida: " + item);
            }
            priceTypes.add(code);
        }

        if (priceTypes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar al menos una lista de precio.");
        }

        return new ExportOptions(
                safe.getQuery(),
                safe.getBrand(),
                safe.getModel(),
                safe.getCategory(),
                stock,
                Boolean.TRUE.equals(safe.getIncludeStock()),
                new ArrayList<>(priceTypes)
        );
    }

    private String buildSql() {
        return """
            WITH base AS (
              SELECT
                p.sku,
                p.name,
                p.brand,
                p.model,
                p.compatibility,
                p.category,
                p.presentation,
                p.price_a,
                p.price_b,
                p.price_c,
                p.price_d,
                CASE
                  WHEN p.manage_by_serial = TRUE THEN COALESCE(su_agg.serial_qty, 0)
                  ELSE GREATEST(COALESCE(ps.quantity_on_hand, 0) - COALESCE(res.reserved_qty, 0), 0)
                END AS stock_on_hand
              FROM product p
              LEFT JOIN product_stock ps
                     ON ps.product_id = p.id
              LEFT JOIN (
                SELECT product_id, SUM(quantity) AS reserved_qty
                FROM product_stock_reservation
                WHERE status = 'ACTIVE'
                  AND reserved_date = CURRENT_DATE
                GROUP BY product_id
              ) res
                     ON res.product_id = p.id
              LEFT JOIN (
                SELECT product_id, COUNT(*)::numeric(14,3) AS serial_qty
                FROM product_serial_unit
                WHERE status = 'EN_ALMACEN'
                GROUP BY product_id
              ) su_agg
                     ON su_agg.product_id = p.id
              WHERE
                (
                  p.sku ILIKE ?
                  OR p.barcode ILIKE ?
                  OR p.name ILIKE ?
                  OR COALESCE(p.brand, '') ILIKE ?
                  OR COALESCE(p.model, '') ILIKE ?
                  OR COALESCE(p.compatibility, '') ILIKE ?
                  OR COALESCE(p.factory_code, '') ILIKE ?
                  OR COALESCE(p.warehouse_location, '') ILIKE ?
                )
                AND (?::text IS NULL OR p.brand ILIKE ?::text)
                AND (?::text IS NULL OR p.model ILIKE ?::text)
                AND (?::text IS NULL OR p.category = ?::text)
            )
            SELECT
              sku,
              name,
              brand,
              model,
              compatibility,
              category,
              presentation,
              stock_on_hand,
              price_a,
              price_b,
              price_c,
              price_d
            FROM base
            WHERE
              CASE
                WHEN ? = 'IN' THEN stock_on_hand > 0
                WHEN ? = 'OUT' THEN stock_on_hand <= 0
                ELSE TRUE
              END
            ORDER BY name ASC
            """;
    }

    private void bindParams(PreparedStatement ps, ExportOptions options) throws java.sql.SQLException {
        String likeParam = QueryMapper.formatAsLikeParam(options.query() == null ? "" : options.query().trim());
        String brandLike = blank(options.brand()) ? null : "%" + options.brand().trim() + "%";
        String modelLike = blank(options.model()) ? null : "%" + options.model().trim() + "%";
        String categoryEq = blank(options.category()) ? null : options.category().trim();

        int i = 1;
        for (int n = 0; n < 8; n++) {
            ps.setString(i++, likeParam);
        }
        ps.setString(i++, brandLike);
        ps.setString(i++, brandLike);
        ps.setString(i++, modelLike);
        ps.setString(i++, modelLike);
        ps.setString(i++, categoryEq);
        ps.setString(i++, categoryEq);
        ps.setString(i++, options.stock());
        ps.setString(i++, options.stock());
    }

    private void writeHeader(Sheet sheet, ExportOptions options, ExportStyles styles) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(24);
        List<String> labels = new ArrayList<>(List.of(
                "SKU",
                "NOMBRE",
                "MARCA",
                "MODELO",
                "COMPATIBILIDAD",
                "CATEGORIA",
                "PRESENTACION"
        ));
        if (options.includeStock()) {
            labels.add("STOCK");
        }
        boolean singlePrice = options.priceTypes().size() == 1;
        for (String priceType : options.priceTypes()) {
            labels.add(singlePrice ? "PRECIO" : "PRECIO " + priceType);
        }

        for (int c = 0; c < labels.size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(labels.get(c));
            String priceType = headerPriceType(c, options);
            cell.setCellStyle(priceType == null ? styles.header : styles.priceHeader(priceType));
        }
    }

    private String headerPriceType(int column, ExportOptions options) {
        int firstPriceColumn = 7 + (options.includeStock() ? 1 : 0);
        int index = column - firstPriceColumn;
        return index >= 0 && index < options.priceTypes().size() ? options.priceTypes().get(index) : null;
    }

    private void applyColumnWidths(Sheet sheet, ExportOptions options) {
        int[] fixed = {18, 48, 20, 20, 34, 22, 20};
        int col = 0;
        for (int width : fixed) {
            sheet.setColumnWidth(col++, width * 256);
        }
        if (options.includeStock()) {
            sheet.setColumnWidth(col++, 14 * 256);
        }
        for (int i = 0; i < options.priceTypes().size(); i++) {
            sheet.setColumnWidth(col++, 14 * 256);
        }
    }

    private ExportStyles createStyles(Workbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        CellStyle header = baseHeaderStyle(workbook, headerFont, IndexedColors.DARK_BLUE);
        CellStyle priceAHeader = baseHeaderStyle(workbook, headerFont, IndexedColors.BLUE);
        CellStyle priceBHeader = baseHeaderStyle(workbook, headerFont, IndexedColors.TEAL);
        CellStyle priceCHeader = baseHeaderStyle(workbook, headerFont, IndexedColors.GREY_50_PERCENT);
        CellStyle priceDHeader = baseHeaderStyle(workbook, headerFont, IndexedColors.BLACK);

        CellStyle text = workbook.createCellStyle();
        text.setVerticalAlignment(VerticalAlignment.TOP);
        text.setWrapText(true);

        CellStyle quantity = workbook.createCellStyle();
        quantity.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0.###"));

        CellStyle money = workbook.createCellStyle();
        money.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0.00"));

        CellStyle moneyA = cloneMoneyStyle(workbook, money, IndexedColors.LIGHT_CORNFLOWER_BLUE);
        CellStyle moneyB = cloneMoneyStyle(workbook, money, IndexedColors.LIGHT_TURQUOISE);
        CellStyle moneyC = cloneMoneyStyle(workbook, money, IndexedColors.GREY_25_PERCENT);
        CellStyle moneyD = cloneMoneyStyle(workbook, money, IndexedColors.GREY_40_PERCENT);

        return new ExportStyles(header, priceAHeader, priceBHeader, priceCHeader, priceDHeader, text, quantity, moneyA, moneyB, moneyC, moneyD);
    }

    private CellStyle baseHeaderStyle(Workbook workbook, Font font, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle cloneMoneyStyle(Workbook workbook, CellStyle base, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(base);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeText(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void writeNumber(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ExportOptions(
            String query,
            String brand,
            String model,
            String category,
            String stock,
            boolean includeStock,
            List<String> priceTypes
    ) {
        int totalColumns() {
            return 7 + (includeStock ? 1 : 0) + priceTypes.size();
        }
    }

    private record ExportStyles(
            CellStyle header,
            CellStyle priceAHeader,
            CellStyle priceBHeader,
            CellStyle priceCHeader,
            CellStyle priceDHeader,
            CellStyle text,
            CellStyle quantity,
            CellStyle priceA,
            CellStyle priceB,
            CellStyle priceC,
            CellStyle priceD
    ) {
        CellStyle priceHeader(String priceType) {
            return switch (priceType) {
                case "A" -> priceAHeader;
                case "B" -> priceBHeader;
                case "C" -> priceCHeader;
                case "D" -> priceDHeader;
                default -> header;
            };
        }

        CellStyle priceCell(String priceType) {
            return switch (priceType) {
                case "A" -> priceA;
                case "B" -> priceB;
                case "C" -> priceC;
                case "D" -> priceD;
                default -> priceA;
            };
        }
    }
}
