package com.paulfernandosr.possystembackend.reports.domain;

import java.time.LocalDate;

public record SlowMovingFilter(LocalDate from, LocalDate to, String model, Status status,
                               Sort sort, boolean stockOnly) {
    public enum Status { ALL, NO_SALES, NEVER_SOLD }
    public enum Sort { SALES, QTY }
}
