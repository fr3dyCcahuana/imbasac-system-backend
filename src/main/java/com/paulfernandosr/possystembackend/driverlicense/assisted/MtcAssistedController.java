package com.paulfernandosr.possystembackend.driverlicense.assisted;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;
import static com.paulfernandosr.possystembackend.driverlicense.assisted.MtcAssistedDtos.*;

@RestController
@RequestMapping("/driver-licenses/mtc/assisted")
public class MtcAssistedController {
    private final MtcAssistedService service;
    public MtcAssistedController(MtcAssistedService service) { this.service = service; }

    @GetMapping("/status") public ResponseEntity<SuccessResponse<Status>> status(Principal principal) {
        owner(principal);
        return ok(service.status());
    }
    @PostMapping("/sessions") public ResponseEntity<SuccessResponse<Challenge>> start(Principal principal, @Valid @RequestBody StartRequest request) {
        return ok(service.start(owner(principal), request));
    }
    @PostMapping("/sessions/{id}/answer") public ResponseEntity<SuccessResponse<Result>> answer(Principal principal, @PathVariable String id, @Valid @RequestBody AnswerRequest request) {
        return ok(service.answer(owner(principal), id, request));
    }
    @DeleteMapping("/sessions/{id}") public ResponseEntity<Void> cancel(Principal principal, @PathVariable String id) {
        service.cancel(owner(principal), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @ExceptionHandler(MtcAssistedException.class) public ResponseEntity<Map<String, Object>> error(MtcAssistedException error) {
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore())
                .body(Map.of("status", error.status(), "code", error.code(), "message", error.getMessage()));
    }
    private String owner(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new MtcAssistedException(401, "MTC_AUTH_REQUIRED", "Inicia sesión para consultar el MTC.");
        }
        return principal.getName();
    }
    private <T> ResponseEntity<SuccessResponse<T>> ok(T payload) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.ok(payload));
    }
}
