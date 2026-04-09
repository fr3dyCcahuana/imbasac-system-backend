package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto;

public record SuccessResponse<T>(
        int status,
        T payload
) {
    public static <T> SuccessResponse<T> ok(T payload) {
        return new SuccessResponse<>(200, payload);
    }

    public static <T> SuccessResponse<T> created(T payload) {
        return new SuccessResponse<>(201, payload);
    }
}
