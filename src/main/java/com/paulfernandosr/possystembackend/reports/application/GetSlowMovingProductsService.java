package com.paulfernandosr.possystembackend.reports.application;

import com.paulfernandosr.possystembackend.reports.domain.SlowMovingFilter;
import com.paulfernandosr.possystembackend.reports.domain.SlowMovingProduct;
import com.paulfernandosr.possystembackend.reports.domain.port.input.GetSlowMovingProductsUseCase;
import com.paulfernandosr.possystembackend.reports.domain.port.output.SlowMovingProductsRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetSlowMovingProductsService implements GetSlowMovingProductsUseCase {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final SlowMovingProductsRepository repository;

    @Override
    public Preview preview(SlowMovingFilter filter, int limit) {
        validate(filter);
        if (limit < 1 || limit > 100) throw badRequest("Mostrar debe estar entre 1 y 100 productos.");
        List<SlowMovingProduct> rows = repository.find(filter, limit + 1);
        return new Preview(List.copyOf(rows.subList(0, Math.min(rows.size(), limit))),
                rows.size() > limit, OffsetDateTime.now(LIMA));
    }

    @Override
    public List<String> models() {
        return repository.findModels();
    }

    @Override
    public byte[] excel(SlowMovingFilter filter) {
        validate(filter);
        List<SlowMovingProduct> products = repository.find(filter, null);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Poca rotación");
            CellStyle header = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(font);
            header.setWrapText(true);
            header.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle number = workbook.createCellStyle();
            number.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            CellStyle currency = workbook.createCellStyle();
            currency.setDataFormat(workbook.createDataFormat().getFormat("\"S/\" #,##0.00"));
            writeRow(sheet, 0, "Productos de poca rotación");
            writeRow(sheet, 1, "Período de ventas", filter.from(), filter.to(), "Modelo",
                    filter.model().isBlank() ? "Todos" : filter.model());
            writeRow(sheet, 2, "Estado", statusLabel(filter.status()), "Orden",
                    filter.sort() == SlowMovingFilter.Sort.QTY ? "Menos unidades netas" : "Menos ventas",
                    "Stock", filter.stockOnly() ? "Solo con existencias" : "Todos");
            writeRow(sheet, 3, "Generado (Lima)", OffsetDateTime.now(LIMA).toString(),
                    "Productos", products.size());
            writeRow(sheet, 4, "Stock y precios: actuales al generar. Ventas: confirmadas hasta la fecha final, sin exigir aceptación SUNAT.");
            writeRow(sheet, 5, "Sin ventas registradas se refiere al historial disponible. Primera entrada: compra o ajuste positivo registrado.");
            writeRow(sheet, 6, "Unidades netas = vendidas en el período menos devoluciones al stock registradas en el período (pueden ser negativas).");
            writeRow(sheet, 7, "Un ingreso reciente o días sin stock no prueban baja demanda. El precio por sí solo no explica la poca rotación.");
            String[] headers = {"Posición", "SKU", "Producto", "Modelo", "Estado", "Stock actual",
                    "N.º ventas período", "Unidades vendidas", "Unidades devueltas", "Unidades netas",
                    "N.º ventas históricas", "Última venta", "Días sin vender al cierre", "Primera entrada registrada",
                    "Días desde primera entrada al cierre", "Primera entrada en período", "Precio A S/", "Precio B S/",
                    "Precio C S/", "Precio D S/", "Precio promedio vendido S/"};
            Row heading = writeRow(sheet, 9, (Object[]) headers);
            heading.setHeightInPoints(42);
            for (Cell cell : heading) cell.setCellStyle(header);
            int index = 10;
            for (SlowMovingProduct p : products) {
                Row row = writeRow(sheet, index, index - 9, p.sku(), p.name(), p.model(), productStatus(p), p.stock(),
                        p.countSales(), p.soldQty(), p.returnedQty(), p.netQty(), p.historicalSales(), p.lastSaleDate(),
                        p.daysWithoutSale(), p.firstStockDate(), p.daysSinceFirstStock(), p.newInPeriod() ? "Sí" : "No",
                        p.priceA(), p.priceB(), p.priceC(), p.priceD(), p.averageSalePrice());
                for (int col : new int[]{5, 7, 8, 9}) row.getCell(col).setCellStyle(number);
                for (int col = 16; col <= 20; col++) row.getCell(col).setCellStyle(currency);
                index++;
            }
            sheet.createFreezePane(4, 10);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(9, Math.max(9, index - 1), 0, headers.length - 1));
            for (int col = 0; col < headers.length; col++) sheet.setColumnWidth(col, (col == 2 ? 52 : col == 4 ? 30 : 24) * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el Excel de poca rotación.", ex);
        }
    }

    private Row writeRow(Sheet sheet, int index, Object... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            Object value = values[i];
            if (value instanceof Number n) cell.setCellValue(n.doubleValue());
            else if (value != null) cell.setCellValue(value.toString());
        }
        return row;
    }

    private String statusLabel(SlowMovingFilter.Status status) {
        return switch (status) {
            case ALL -> "Todos (de menor a mayor rotación)";
            case NO_SALES -> "Sin ventas en el período";
            case NEVER_SOLD -> "Sin ventas registradas en el historial";
        };
    }

    private String productStatus(SlowMovingProduct p) {
        if (p.historicalSales() == 0) return "Sin ventas registradas";
        return p.countSales() == 0 ? "Sin ventas en el período" : "Con ventas en el período";
    }

    private void validate(SlowMovingFilter filter) {
        if (filter.from() == null || filter.to() == null || filter.from().isAfter(filter.to()))
            throw badRequest("Selecciona un rango de fechas válido.");
        if (filter.to().isAfter(LocalDate.now(LIMA))) throw badRequest("La fecha final no puede ser futura.");
        if (filter.model() == null || filter.model().length() > 200 || filter.status() == null || filter.sort() == null)
            throw badRequest("Filtros de poca rotación inválidos.");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
