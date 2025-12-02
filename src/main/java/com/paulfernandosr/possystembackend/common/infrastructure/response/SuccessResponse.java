package com.paulfernandosr.possystembackend.common.infrastructure.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.http.HttpStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuccessResponse<T> {
    private int status;
    private T payload;
    private Metadata metadata;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Metadata {
        private int pageNumber;
        private int pageSize;
        private int numberOfElements;
        private int totalPages;
        private long totalElements;
    }

    public static <T> SuccessResponse<T> ok(T payload) {
        return SuccessResponse.<T>builder()
                .status(HttpStatus.OK.value())
                .payload(payload)
                .build();
    }

    public static <T, M> SuccessResponse<T> ok(T payload, Metadata metadata) {
        return SuccessResponse.<T>builder()
                .status(HttpStatus.OK.value())
                .payload(payload)
                .metadata(metadata)
                .build();
    }

    public static <T> SuccessResponse<T> created(T payload) {
        return SuccessResponse.<T>builder()
                .status(HttpStatus.CREATED.value())
                .payload(payload)
                .build();
    }
}
