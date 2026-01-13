package com.paulfernandosr.possystembackend.sale.domain;

import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.user.domain.User;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleSession {
    private Long id;
    private User user;
    private Station station;
    private BigDecimal initialAmount;
    private BigDecimal salesIncome;
    private BigDecimal totalDiscount;
    private BigDecimal totalExpenses;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
}
