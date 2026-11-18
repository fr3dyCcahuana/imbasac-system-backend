package com.paulfernandosr.possystembackend.timeactivity.infrastructure;

import com.paulfernandosr.possystembackend.timeactivity.application.TimeActivityService;
import com.paulfernandosr.possystembackend.timeactivity.application.TimeActivityService.Identity;
import com.paulfernandosr.possystembackend.timeactivity.application.TimeActivityService.Query;
import com.paulfernandosr.possystembackend.timeactivity.domain.TimeActivity.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/time-activity")
@ConditionalOnProperty(prefix = "imbasac.time-activity", name = "enabled", havingValue = "true")
public class TimeActivityController {
    private final TimeActivityService service;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private static final int MAX_BATCH_BYTES=64*1024;
    private final Semaphore reports = new Semaphore(2);
    private final Semaphore capture = new Semaphore(2);

    public TimeActivityController(TimeActivityService service) { this(service,new com.fasterxml.jackson.databind.ObjectMapper()); }
    @org.springframework.beans.factory.annotation.Autowired
    public TimeActivityController(TimeActivityService service,com.fasterxml.jackson.databind.ObjectMapper json) {
        this.service=service;this.json=json;
    }

    @ModelAttribute
    void privateResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping("/context")
    public Context context(Authentication auth, HttpServletRequest request) {
        return limited(capture, () -> service.context(identity(auth, request)));
    }

    @PostMapping("/events/batch")
    public BatchReceipt ingest(Authentication auth, HttpServletRequest request) {
        return limited(capture, () -> {
            Identity identity=identity(auth,request);
            if(request.getContentLengthLong()>MAX_BATCH_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            try {
                byte[] body=request.getInputStream().readNBytes(MAX_BATCH_BYTES+1);
                if(body.length>MAX_BATCH_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
                Batch batch=json.readerFor(Batch.class).with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(body);
                return service.ingest(identity,batch);
            } catch(IOException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        });
    }

    @GetMapping("/summary")
    public Summary summary(Authentication auth, HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "self") String scope, @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String module, @RequestParam(required = false) String role) {
        return limited(reports, () -> service.summary(identity(auth, request), new Query(date, scope, userId, module, role)));
    }

    @GetMapping("/timeline")
    public List<Interval> timeline(Authentication auth, HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long userId, @RequestParam(required = false) String module) {
        return limited(reports, () -> service.timeline(identity(auth, request), new Query(date, "self", userId, module, null)));
    }

    @GetMapping("/operations")
    public Operations operations(Authentication auth, HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "self") String scope, @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String module, @RequestParam(required = false) String role) {
        return limited(reports, () -> service.operations(identity(auth, request), new Query(date, scope, userId, module, role)));
    }

    private static Identity identity(Authentication auth, HttpServletRequest request) {
        // Attributes are supplied by the existing JWT filter after validating the exact session.
        Object session = request.getAttribute("imbasac.accessSessionKey");
        Object username = request.getAttribute("imbasac.authenticatedUsername");
        if (auth == null || !auth.isAuthenticated() || !(session instanceof String key) || key.isBlank()
                || !auth.getName().equals(username)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var permissions = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return new Identity(auth.getName(), key, permissions);
    }

    private static <T> T limited(Semaphore slots, Supplier<T> work) {
        if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        try { return work.get(); } finally { slots.release(); }
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> rejected(ResponseStatusException error) {
        int status=error.getStatusCode().value();
        String message=switch(status) {
            case 401 -> "La sesión no es válida para esta consulta.";
            case 403 -> "No tienes acceso a esa actividad o no estás incluido en la captura.";
            case 409 -> "El contexto, la política o el identificador de la observación cambió. Vuelve a abrir el módulo.";
            case 413 -> "El lote de observaciones supera el tamaño permitido.";
            case 422 -> "Consulta un día de los últimos 31 días y un alcance dentro de los límites del piloto.";
            case 429 -> "El módulo está atendiendo otras consultas. Intenta nuevamente en unos segundos.";
            default -> "Revisa los filtros o las observaciones enviadas.";
        };
        return failure(status,message);
    }
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<?> invalidRequest() { return failure(400,"Revisa la fecha y los datos enviados al módulo."); }

    @ExceptionHandler({org.springframework.dao.DataAccessException.class,org.springframework.transaction.TransactionException.class})
    ResponseEntity<?> unavailable() { return failure(503,"La medición de actividad no está disponible. Intenta nuevamente más tarde."); }

    private static ResponseEntity<?> failure(int status,String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("status",status,"error",message));
    }
}
