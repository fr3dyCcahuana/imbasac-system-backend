package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfBadRequestException;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfConflictException;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfFileNotFoundException;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfNotFoundException;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ErrorResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class ManualPdfExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, ManualPdfBadRequestException.class})
    public ResponseEntity<ErrorResponse> badRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, ex.getMessage(), LocalDateTime.now()));
    }

    @ExceptionHandler(ManualPdfConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ManualPdfConflictException ex) {
        return ResponseEntity.status(409).body(new ErrorResponse(409, ex.getMessage(), LocalDateTime.now()));
    }

    @ExceptionHandler({ManualPdfNotFoundException.class, ManualPdfFileNotFoundException.class})
    public ResponseEntity<ErrorResponse> notFound(RuntimeException ex) {
        return ResponseEntity.status(404).body(new ErrorResponse(404, ex.getMessage(), LocalDateTime.now()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(413).body(new ErrorResponse(413, "El archivo supera el tamaño máximo permitido para la carga.", LocalDateTime.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unhandled(Exception ex) {
        return ResponseEntity.status(500).body(new ErrorResponse(500, ex.getMessage(), LocalDateTime.now()));
    }
}