package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2SunatEmissionResponse {
    private Long saleId;
    private String docType;
    private String series;
    private Long number;

    private String sunatStatus;
    private String sunatCode;
    private String sunatDescription;

    private String hashCode;
    private String xmlPath;
    private String cdrPath;
    private String pdfPath;
    private LocalDateTime emittedAt;

    /** TRUE cuando SUNAT respondió código 0 y existe emisión aceptada. */
    private Boolean accepted;

    /** TRUE cuando SUNAT respondió un código de rechazo. */
    private Boolean rejected;

    /** TRUE cuando no hubo respuesta válida de SUNAT/CDR: 502, timeout, respuesta vacía, etc. */
    private Boolean communicationError;

    /** TRUE cuando el comprobante puede volver a enviarse con la misma serie y número. */
    private Boolean retryable;
}
