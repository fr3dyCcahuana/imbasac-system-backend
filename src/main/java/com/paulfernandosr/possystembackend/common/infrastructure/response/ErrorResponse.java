package com.paulfernandosr.possystembackend.common.infrastructure.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paulfernandosr.possystembackend.common.infrastructure.ExceptionUtils;
import lombok.*;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private int status;
    private String error;
    private String cause;
    private LocalDateTime timestamp;

    public static ErrorResponse badRequest(Exception exception) {
        return buildException(HttpStatus.BAD_REQUEST, exception);
    }

    public static ErrorResponse conflict(Exception exception) {
        return buildException(HttpStatus.CONFLICT, exception);
    }

    public static ErrorResponse conflict(String message) {
        return buildException(HttpStatus.CONFLICT, message);
    }

    public static ErrorResponse unauthorized(Exception exception) {
        return buildException(HttpStatus.UNAUTHORIZED, exception);
    }

    public static ErrorResponse forbidden(Exception exception) {
        return buildException(HttpStatus.FORBIDDEN, exception);
    }

    public static ErrorResponse internalServerError(Exception exception) {
        return buildException(HttpStatus.INTERNAL_SERVER_ERROR, exception);
    }

    private static ErrorResponse buildException(HttpStatus httpStatus, String message) {
        return ErrorResponse.builder()
                .status(httpStatus.value())
                .error(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private static ErrorResponse buildException(HttpStatus httpStatus, Exception exception) {
        return ErrorResponse.builder()
                .status(httpStatus.value())
                .error(ExceptionUtils.getError(exception))
                .cause(ExceptionUtils.getCause(exception))
                .timestamp(LocalDateTime.now())
                .build();
    }
}
