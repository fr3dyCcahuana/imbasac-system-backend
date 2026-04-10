package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleSummaryResponse {
    private Long counterSaleId;
    private String series;
    private Long number;
    private LocalDate issueDate;
    private String customerDocNumber;
    private String customerName;
    private BigDecimal total;
    private String status;
    private Boolean canVoid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CounterSaleSummaryItemResponse> items;
}
