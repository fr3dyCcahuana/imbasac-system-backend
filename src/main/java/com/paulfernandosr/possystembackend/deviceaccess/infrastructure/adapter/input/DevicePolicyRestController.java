package com.paulfernandosr.possystembackend.deviceaccess.infrastructure.adapter.input;
import com.paulfernandosr.possystembackend.deviceaccess.application.DeviceAccessPolicyService;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DevicePolicyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import java.security.Principal;
@RestController
@RequiredArgsConstructor
@RequestMapping("/security/device-policy")
public class DevicePolicyRestController {
    private final DeviceAccessPolicyService policy;
    @GetMapping("/me") public ResponseEntity<DevicePolicyStatus> current(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(policy.status(principal.getName()));
    }
}
