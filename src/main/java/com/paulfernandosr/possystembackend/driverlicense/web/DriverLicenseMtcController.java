package com.paulfernandosr.possystembackend.driverlicense.web;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** The text captcha portal was retired. Old clients must move to the assisted image flow. */
@RestController
@RequestMapping("/driver-licenses/mtc")
public class DriverLicenseMtcController {
    @GetMapping("/init") public ResponseEntity<Map<String, Object>> init() { return retired(); }
    @PostMapping("/check") public ResponseEntity<Map<String, Object>> check() { return retired(); }
    private ResponseEntity<Map<String, Object>> retired() {
        return ResponseEntity.status(HttpStatus.GONE).cacheControl(CacheControl.noStore()).body(Map.of(
                "status", 410, "code", "MTC_TEXT_CAPTCHA_RETIRED",
                "message", "El MTC cambió de portal. Actualiza la pantalla para utilizar la consulta asistida con elección de imagen."));
    }
}
