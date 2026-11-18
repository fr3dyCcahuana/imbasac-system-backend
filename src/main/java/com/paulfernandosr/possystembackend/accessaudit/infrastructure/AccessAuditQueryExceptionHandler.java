package com.paulfernandosr.possystembackend.accessaudit.infrastructure;
import com.paulfernandosr.possystembackend.accessaudit.domain.AuditException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes=AccessAuditRestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessAuditQueryExceptionHandler {
    @ExceptionHandler({org.springframework.dao.DataAccessException.class,org.springframework.transaction.TransactionException.class})
    public ResponseEntity<?> unavailable() { return response(503,"ACCESS_AUDIT_UNAVAILABLE","El registro de accesos no está disponible."); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> denied() { return response(403,"ACCESS_AUDIT_FORBIDDEN","No tienes permiso para realizar esta acción."); }
    @ExceptionHandler({org.springframework.validation.BindException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<?> invalid() { return response(400,"ACCESS_AUDIT_INVALID_QUERY","Revisa el período y los filtros de la consulta."); }
    private ResponseEntity<?> response(int status,String code,String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("status",status,"code",code,"error",message));
    }
}
