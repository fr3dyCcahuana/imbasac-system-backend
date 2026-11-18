package com.paulfernandosr.possystembackend.sunatfiles.infrastructure;
import com.paulfernandosr.possystembackend.sunatfiles.domain.SunatFileException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes=PrivateSunatFileRestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PrivateSunatFileExceptionHandler {
    @ExceptionHandler(SunatFileException.class) public ResponseEntity<?> error(SunatFileException error) {
        int status=switch(error.getCode()) {
            case SUNAT_FILE_INVALID->400;case SUNAT_FILE_FORBIDDEN->403;case SUNAT_FILE_NOT_FOUND->404;
            case SUNAT_FILE_TOO_LARGE->413;default->503;
        };
        String message=switch(error.getCode()) {
            case SUNAT_FILES_DISABLED->"La descarga privada de comprobantes todavía no está habilitada.";
            case SUNAT_FILE_INVALID->"El documento solicitado no es válido.";
            case SUNAT_FILE_FORBIDDEN->"No tienes permiso para consultar este documento.";
            case SUNAT_FILE_NOT_FOUND->"El documento no está disponible.";
            case SUNAT_FILE_TOO_LARGE->"El documento supera el tamaño permitido.";
            default->"No se pudo consultar el documento. Inténtalo nuevamente.";
        };
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("status",status,"code",error.getCode().name(),"error",message));
    }
}
