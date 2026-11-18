package com.paulfernandosr.possystembackend.accessaudit.infrastructure;

import com.paulfernandosr.possystembackend.accessaudit.domain.AuditException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessAuditExceptionHandler {
    @ExceptionHandler(AuditException.class)
    public ResponseEntity<?> handle(AuditException error) {
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore())
                .body(Map.of("status",error.status(),"code",error.code(),"error",error.getMessage()));
    }
}
