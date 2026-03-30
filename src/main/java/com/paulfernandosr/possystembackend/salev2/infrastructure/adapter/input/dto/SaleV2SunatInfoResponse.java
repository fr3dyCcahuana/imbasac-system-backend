package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2SunatInfoResponse {
    private String status;
    private String responseCode;
    private String responseDescription;
    private String hashCode;
    private String xmlPath;
    private String cdrPath;
    private String pdfPath;
    private LocalDateTime sentAt;
}