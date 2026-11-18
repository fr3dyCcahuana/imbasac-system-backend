package com.paulfernandosr.possystembackend.accesscontrol.infrastructure;

import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes=AccessControlRestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessControlExceptionHandler {
    @ExceptionHandler(AccessControlException.class) public ResponseEntity<?> control(AccessControlException exception) {
        int status=switch(exception.getCode()) {
            case ACCESS_CONTROL_DISABLED,ACCESS_CONTROL_UNAVAILABLE->503;
            case ACCESS_CONTROL_INVALID_REQUEST->400;
            case ACCESS_CONTROL_NOT_FOUND->404;
            case ACCESS_DEVICE_QUOTA_EXCEEDED,ACCESS_DEVICE_NOT_ACTIVE->409;
            default->403;
        };
        String message=switch(exception.getCode()) {
            case ACCESS_CONTROL_DISABLED->"El control de acceso todavía no está habilitado.";
            case ACCESS_CONTROL_UNAVAILABLE->"No se pudo comprobar el estado del control de acceso.";
            case ACCESS_CONTROL_INVALID_REQUEST->"Revisa los valores y proporciona un motivo de 5 a 300 caracteres.";
            case ACCESS_CONTROL_NOT_FOUND->"No se encontró el usuario o la autorización indicada.";
            case ACCESS_DEVICE_QUOTA_EXCEEDED->"El cupo de equipos no permite esta operación. Revisa las autorizaciones activas.";
            case ACCESS_DEVICE_NOT_ACTIVE->"El equipo debe estar activo y verificado antes de autorizarlo.";
            case ACCESS_ACCOUNT_BLOCKED->"El usuario tiene una restricción de acceso vigente.";
            default->"El equipo no está autorizado para este usuario.";
        };
        return response(status,exception.getCode().name(),message);
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class) public ResponseEntity<?> forbidden() { return response(403,"ACCESS_CONTROL_FORBIDDEN","No tienes permiso para realizar esta acción."); }
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<?> invalid() { return response(400,"ACCESS_CONTROL_INVALID_REQUEST","Revisa los valores enviados."); }
    private static ResponseEntity<?> response(int status,String code,String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("status",status,"code",code,"error",message));
    }
}
