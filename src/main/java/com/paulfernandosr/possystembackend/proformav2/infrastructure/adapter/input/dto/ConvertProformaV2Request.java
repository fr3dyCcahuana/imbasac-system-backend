package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType;
import com.paulfernandosr.possystembackend.salev2.domain.model.TaxStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConvertProformaV2Request {

    // Documento destino
    private DocType docType;      // BOLETA/FACTURA/SIMPLE
    private String series;        // por station + docType + series

    // Impuesto de la venta (proforma no tiene IGV)
    private TaxStatus taxStatus;  // GRAVADA/NO_GRAVADA
    private BigDecimal igvRate;   // 18.00 (default)

    // Pago
    private PaymentType paymentType;  // CONTADO/CREDITO
    private Integer creditDays;       // requerido si CREDITO
    private String dueDate;           // opcional (yyyy-MM-dd)

    // Para seriales: asignar unidades por línea (si aplica)
    private List<LineSerials> serials;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineSerials {
        private Integer lineNumber;     // coincide con proforma_item.line_number
        private List<Long> serialUnitIds;
    }
}
