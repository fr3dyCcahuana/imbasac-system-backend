package com.paulfernandosr.possystembackend.driverlicense.web;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.driverlicense.application.CheckDriverLicenseUseCase;
import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseResult;
import com.paulfernandosr.possystembackend.driverlicense.infrastructure.mtc.MtcCaptchaService;
import com.paulfernandosr.possystembackend.driverlicense.web.dto.DriverLicenseCheckRequest;
import com.paulfernandosr.possystembackend.driverlicense.web.dto.DriverLicenseCheckResponse;
import com.paulfernandosr.possystembackend.driverlicense.web.dto.MtcCaptchaInitResponse;
import com.paulfernandosr.possystembackend.driverlicense.web.mapper.DriverLicenseWebMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/driver-licenses/mtc")
public class DriverLicenseMtcController {

    private final MtcCaptchaService captchaService;
    private final CheckDriverLicenseUseCase checkDriverLicenseUseCase;
    private final DriverLicenseWebMapper mapper;

    public DriverLicenseMtcController(MtcCaptchaService captchaService,
                                      CheckDriverLicenseUseCase checkDriverLicenseUseCase,
                                      DriverLicenseWebMapper mapper) {
        this.captchaService = captchaService;
        this.checkDriverLicenseUseCase = checkDriverLicenseUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/init")
    public ResponseEntity<SuccessResponse<MtcCaptchaInitResponse>> init() {
        var result = captchaService.init();
        return ResponseEntity.ok(
                SuccessResponse.ok(new MtcCaptchaInitResponse(result.sessionId(), result.captchaImage()))
        );
    }

    @PostMapping("/check")
    public ResponseEntity<SuccessResponse<DriverLicenseCheckResponse>> check(@RequestBody DriverLicenseCheckRequest request) {
        DriverLicenseResult result = checkDriverLicenseUseCase.check(mapper.toQuery(request));
        return ResponseEntity.ok(
                SuccessResponse.ok(mapper.toResponse(result))
        );
    }
}