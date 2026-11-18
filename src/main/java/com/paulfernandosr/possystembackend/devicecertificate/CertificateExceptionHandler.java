package com.paulfernandosr.possystembackend.devicecertificate;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes=DeviceCertificateRestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CertificateExceptionHandler {
    @ExceptionHandler(CertificateException.class) public ResponseEntity<?> handle(CertificateException ex) {
        int status=switch(ex.getCode()) {
            case CERTIFICATES_DISABLED,DEVICE_REGISTRATION_DISABLED,CERTIFICATE_UNAVAILABLE,CERTIFICATE_PROXY_NOT_READY,CERTIFICATE_SIGNER_UNAVAILABLE->503;
            case CERTIFICATE_NOT_FOUND->404;
            case CERTIFICATE_INVALID,CERTIFICATE_REQUEST_INVALID->400;
            case CERTIFICATE_ENROLLMENT_REQUIRED->409;
            default->403;
        };
        String message=switch(ex.getCode()) {
            case CERTIFICATES_DISABLED->"La administración de certificados todavía está desactivada.";
            case DEVICE_REGISTRATION_DISABLED->"El registro de computadoras requiere habilitar el control de acceso.";
            case CERTIFICATE_UNAVAILABLE->"No se pudo comprobar el registro de certificados.";
            case CERTIFICATE_PROXY_NOT_READY->"Primero debe configurarse y verificarse el canal seguro con Nginx.";
            case CERTIFICATE_SIGNER_UNAVAILABLE->"La CA emisora no está disponible. Consulta el estado antes de volver a solicitar la firma.";
            case CERTIFICATE_ENROLLMENT_REQUIRED->"Registra primero el CSR y el certificado correspondiente antes de aprobar el equipo.";
            case CERTIFICATE_NOT_FOUND->"No se encontró el registro indicado.";
            default->"Revisa el certificado, el equipo y los datos enviados. No se autorizó el acceso.";
        };
        return response(status,ex.getCode().name(),message);
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class) public ResponseEntity<?> forbidden() { return response(403,"CERTIFICATE_FORBIDDEN","No tienes permiso para realizar esta acción."); }
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<?> invalid() { return response(400,"CERTIFICATE_REQUEST_INVALID","Revisa los valores enviados."); }
    private static ResponseEntity<?> response(int status,String code,String error) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("status",status,"code",code,"error",error)); }
}
