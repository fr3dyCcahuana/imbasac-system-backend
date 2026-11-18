package com.paulfernandosr.possystembackend.reports.application;

import com.paulfernandosr.possystembackend.reports.domain.SlowMovingFilter;
import com.paulfernandosr.possystembackend.reports.domain.SlowMovingProduct;
import com.paulfernandosr.possystembackend.reports.domain.port.output.SlowMovingProductsRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetSlowMovingProductsServiceTest {
    private final SlowMovingProductsRepository repository = mock(SlowMovingProductsRepository.class);
    private final GetSlowMovingProductsService service = new GetSlowMovingProductsService(repository);
    private final SlowMovingFilter filter = new SlowMovingFilter(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31),
            "PULSAR", SlowMovingFilter.Status.NEVER_SOLD, SlowMovingFilter.Sort.SALES, true);

    @Test
    void previewSignalsMoreRowsWithoutLimitingTheExcel() throws Exception {
        var product = product();
        when(repository.find(filter, 2)).thenReturn(List.of(product, product));
        var preview = service.preview(filter, 1);
        assertThat(preview.items()).hasSize(1);
        assertThat(preview.hasMore()).isTrue();
        when(repository.find(filter, null)).thenReturn(List.of(product, product, product));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.excel(filter)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(12);
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("PULSAR");
            assertThat(sheet.getRow(10).getCell(4).getStringCellValue()).isEqualTo("Sin ventas registradas");
            assertThat(sheet.getRow(10).getCell(16).getNumericCellValue()).isEqualTo(10);
            assertThat(sheet.getRow(10).getCell(20).getCellType()).isEqualTo(CellType.BLANK);
            // An untrusted SKU starting with '=' stays text, never an Excel formula.
            assertThat(sheet.getRow(10).getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(10).getCell(1).getStringCellValue()).isEqualTo("=SKU");
        }
        verify(repository).find(filter, null);
    }

    @Test
    void rejectsInvalidDateRangesFutureDatesAndUnboundedPreview() {
        assertThatThrownBy(() -> service.preview(new SlowMovingFilter(filter.to(), filter.from(), "", filter.status(), filter.sort(), true), 20))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.preview(new SlowMovingFilter(filter.from(), LocalDate.now().plusDays(2), "", filter.status(), filter.sort(), true), 20))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.preview(filter, 5000)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    private SlowMovingProduct product() {
        return new SlowMovingProduct(1L, "=SKU", "Nunca vendido", "PULSAR", BigDecimal.TEN, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, null, null, null, null, false,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, null);
    }
}
