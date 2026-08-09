package com.paulfernandosr.possystembackend.common.infrastructure;

import com.paulfernandosr.possystembackend.common.domain.exception.DomainException;
import com.paulfernandosr.possystembackend.common.infrastructure.response.ErrorResponse;
import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerException;
import com.paulfernandosr.possystembackend.proformav2.domain.exception.InvalidProformaV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.security.domain.exception.InvalidCredentialsException;
import com.paulfernandosr.possystembackend.security.domain.exception.InvalidSessionException;
import com.paulfernandosr.possystembackend.stockreservation.domain.exception.StockReservationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        log.error("GlobalExceptionHandler:handleResponseStatusException", exception);

        String reason = exception.getReason();
        String message = reason == null || reason.isBlank() ? exception.getMessage() : reason;
        ErrorResponse body = ErrorResponse.builder()
                .status(exception.getStatusCode().value())
                .error(message)
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }

    @ExceptionHandler(InvalidProformaV2Exception.class)
    public ResponseEntity<ErrorResponse> handleInvalidProformaV2Exception(InvalidProformaV2Exception exception) {
        log.error("GlobalExceptionHandler:handleInvalidProformaV2Exception", exception);

        return ResponseEntity.badRequest()
                .body(ErrorResponse.badRequest(exception));
    }

    @ExceptionHandler({
            InvalidSaleV2Exception.class,
            InvalidCounterSaleException.class,
            InvalidCustomerException.class,
            StockReservationException.class
    })
    public ResponseEntity<ErrorResponse> handleBusinessBadRequest(RuntimeException exception) {
        log.error("GlobalExceptionHandler:handleBusinessBadRequest", exception);

        return ResponseEntity.badRequest()
                .body(ErrorResponse.badRequest(exception));
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

    /**
     * El cliente cerró la conexión (seek/buffer de un video, recarga de página o cierre de un stream SSE).
     * Es benigno: la respuesta ya está comprometida, no hay nada que enviar. Se evita el ERROR ruidoso y
     * el HttpMessageNotWritableException de intentar escribir JSON sobre un Content-Type ya fijado (p. ej. video/mp4).
     */
    @ExceptionHandler({ClientAbortException.class, AsyncRequestNotUsableException.class})
    public void handleClientDisconnect(Exception exception) {
        log.debug("GlobalExceptionHandler:handleClientDisconnect - cliente desconectado: {}", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        if (isClientDisconnect(exception)) {
            log.debug("GlobalExceptionHandler:handleException - cliente desconectado: {}", exception.getMessage());
            return null;
        }
        log.error("GlobalExceptionHandler:handleException", exception);

        return ResponseEntity.internalServerError()
                .body(ErrorResponse.internalServerError(exception));
    }

    /** Detecta desconexiones del cliente envueltas en otras excepciones (broken pipe / connection reset). */
    private boolean isClientDisconnect(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ClientAbortException || current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("connection reset by peer")
                        || lower.contains("broken pipe")
                        || lower.contains("connection reset")) {
                    return true;
                }
            }
        }
        return false;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 413);
        body.put("error", "El archivo supera el tamaño máximo permitido para la carga.");
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(413).body(body);
    }
}
