package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SunatEmissionResult {
    private String status;
    private String code;
    private String description;
    private String hashCode;
    private String xmlPath;
    private String cdrPath;
    private String pdfPath;
    private LocalDateTime emittedAt;

    private boolean accepted;
    private boolean rejected;
    private boolean communicationError;
    private boolean retryable;
}
