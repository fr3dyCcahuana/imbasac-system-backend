package com.paulfernandosr.possystembackend.common.infrastructure;

import com.paulfernandosr.possystembackend.common.domain.exception.DomainException;
import com.paulfernandosr.possystembackend.common.infrastructure.response.ErrorResponse;
import com.paulfernandosr.possystembackend.security.domain.exception.InvalidCredentialsException;
import com.paulfernandosr.possystembackend.security.domain.exception.InvalidSessionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    public static final String UNEXPECTED_ERROR_MESSAGE = "unexpected.error.message";

    private final MessageSource messageSource;

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException exception, Locale locale) {
        log.error("GlobalExceptionHandler:handleDomainException", exception);

        String message = messageSource.getMessage(exception.getErrorMessage(), null, locale);
        return new ResponseEntity<>(ErrorResponse.conflict(message), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(InvalidCredentialsException exception) {
        log.error("GlobalExceptionHandler:handleInvalidCredentialsException", exception);

        return new ResponseEntity<>(ErrorResponse.unauthorized(exception), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(InvalidSessionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSessionException(InvalidSessionException exception) {
        log.error("GlobalExceptionHandler:handleInvalidSessionException", exception);

        return new ResponseEntity<>(ErrorResponse.unauthorized(exception), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException exception) {
        log.error("GlobalExceptionHandler:handleRuntimeException", exception);

        return ResponseEntity.internalServerError()
                .body(ErrorResponse.internalServerError(exception));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        log.error("GlobalExceptionHandler:handleMethodArgumentNotValidException", exception);

        return ResponseEntity.badRequest()
                .body(ErrorResponse.badRequest(exception));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException exception) {
        log.error("GlobalExceptionHandler:handleNoResourceFoundException", exception);

        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        log.error("GlobalExceptionHandler:handleException", exception);

        return ResponseEntity.internalServerError()
                .body(ErrorResponse.internalServerError(exception));
    }
}
