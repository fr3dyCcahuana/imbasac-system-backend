package com.paulfernandosr.possystembackend.purchase.domain.exception;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseFieldError {
    private String path;
    private String message;
    private Object value;
    private Object expected;
}
