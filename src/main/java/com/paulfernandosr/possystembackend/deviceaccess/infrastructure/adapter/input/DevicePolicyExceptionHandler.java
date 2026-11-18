package com.paulfernandosr.possystembackend.deviceaccess.infrastructure.adapter.input;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DevicePolicyException;
import com.paulfernandosr.possystembackend.deviceaccess.infrastructure.DevicePolicyError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DevicePolicyExceptionHandler {
    @ExceptionHandler(com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException.class)
    public ResponseEntity<?> accessControl(com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException exception) {
        var error=com.paulfernandosr.possystembackend.trustedclient.AccessControlError.from(exception);
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore()).body(error);
    }
    @ExceptionHandler(DevicePolicyException.class)
    public ResponseEntity<DevicePolicyError> handle(DevicePolicyException exception) {
        DevicePolicyError error=DevicePolicyError.from(exception);
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore()).body(error);
    }
}
