package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.input.rest;

import com.paulfernandosr.possystembackend.guideremission.domain.exception.GuideRemissionIntegrationException;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.GuideRemissionNotFoundException;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.InvalidGuideRemissionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GuideRemissionExceptionHandler {

    @ExceptionHandler(InvalidGuideRemissionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidGuide(InvalidGuideRemissionException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(GuideRemissionIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegration(GuideRemissionIntegrationException ex) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(GuideRemissionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(GuideRemissionNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request inválido.");

        return build(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
