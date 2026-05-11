package com.paulfernandosr.possystembackend.purchase.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.purchase.domain.exception.PurchaseApiException;
import com.paulfernandosr.possystembackend.purchase.domain.exception.DuplicatePurchaseDocumentException;
import com.paulfernandosr.possystembackend.purchase.domain.exception.PurchaseFieldError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PurchaseRestController.class)
public class PurchaseExceptionHandler {

    @ExceptionHandler(PurchaseApiException.class)
    public ResponseEntity<Map<String, Object>> handlePurchaseApiException(PurchaseApiException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", ex.getStatus());
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("errors", ex.getErrors() == null ? List.of() : ex.getErrors());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }


    @ExceptionHandler(DuplicatePurchaseDocumentException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePurchaseDocument(DuplicatePurchaseDocumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", 409);
        body.put("code", "DUPLICATE_PURCHASE_DOCUMENT");
        body.put("message", ex.getMessage());
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("errors", List.of());
        return ResponseEntity.status(409).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", 422);
        body.put("code", "INVALID_PURCHASE_STATE");
        body.put("message", ex.getMessage());
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("errors", List.of(PurchaseFieldError.builder()
                .path("")
                .message(ex.getMessage())
                .build()));
        return ResponseEntity.status(422).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", 422);
        body.put("code", "INVALID_REQUEST");
        body.put("message", ex.getMessage());
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("errors", List.of(PurchaseFieldError.builder()
                .path("")
                .message(ex.getMessage())
                .build()));
        return ResponseEntity.status(422).body(body);
    }
}
