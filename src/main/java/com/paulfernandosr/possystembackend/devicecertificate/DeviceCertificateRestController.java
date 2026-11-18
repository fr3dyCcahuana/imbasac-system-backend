package com.paulfernandosr.possystembackend.devicecertificate;

import com.paulfernandosr.possystembackend.devicecertificate.CertificateModels.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/security/device-certificates")
public class DeviceCertificateRestController {
    private final DeviceCertificateService certificates;
    public DeviceCertificateRestController(DeviceCertificateService certificates) { this.certificates=certificates; }
    @GetMapping("/status") public ResponseEntity<?> status() { require("VIEW_SECURITY_CONTROL");return response(certificates.status()); }
    @PostMapping("/devices") public ResponseEntity<?> device(@RequestBody NewDevice input,Principal principal) { manage();return created(certificates.createDevice(input,principal.getName())); }
    @GetMapping public ResponseEntity<?> certificates(@RequestParam UUID deviceId) { require("VIEW_SECURITY_CONTROL");return response(certificates.certificates(deviceId)); }
    @GetMapping("/enrollments") public ResponseEntity<?> enrollments(@RequestParam UUID deviceId) { manage();return response(certificates.enrollments(deviceId)); }
    @PostMapping("/enrollments") public ResponseEntity<?> enroll(@RequestBody RegisterEnrollment input,Principal principal) { manage();return created(certificates.enroll(input,principal.getName())); }
    @PostMapping("/import") public ResponseEntity<?> importCertificate(@RequestBody ImportCertificate input,Principal principal) { manage();return created(certificates.importCertificate(input,principal.getName())); }
    @PostMapping("/enrollments/{id}/issue") public ResponseEntity<?> issue(@PathVariable UUID id,@RequestBody Reason input,Principal principal) {
        require("VIEW_SECURITY_CONTROL","MANAGE_SECURITY_DEVICES","ISSUE_SECURITY_CERTIFICATES");return created(certificates.issue(id,input.reason(),principal.getName()));
    }
    @GetMapping("/{id}/download") public ResponseEntity<String> download(@PathVariable UUID id) {
        manage();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=\""+id+".cer\"")
                .contentType(MediaType.parseMediaType("application/x-pem-file")).body(certificates.download(id));
    }
    @PostMapping("/{id}/revoke") public ResponseEntity<Void> revoke(@PathVariable UUID id,@RequestBody Reason input,Principal principal) { manage();certificates.revoke(id,principal.getName(),input.reason());return empty(); }
    @PutMapping("/devices/{id}/hardware") public ResponseEntity<Void> hardware(@PathVariable UUID id,@RequestBody HardwareApproval input,Principal principal) {
        require("VIEW_SECURITY_CONTROL","MANAGE_SECURITY_HARDWARE");certificates.hardware(id,input,principal.getName());return empty();
    }
    @GetMapping("/role-policies") public ResponseEntity<?> policies() { require("VIEW_SECURITY_CONTROL");return response(certificates.policies()); }
    @PutMapping("/role-policies/{id}") public ResponseEntity<Void> policy(@PathVariable long id,@RequestBody PolicyChange input,Principal principal) {
        require("VIEW_SECURITY_CONTROL","MANAGE_SECURITY_POLICIES");certificates.policy(id,input,principal.getName());return empty();
    }
    @GetMapping("/event-location/{id}") public ResponseEntity<?> location(@PathVariable UUID id) { require("VIEW_SECURITY_AUDIT");return response(certificates.eventLocation(id)); }
    private static void manage() { require("VIEW_SECURITY_CONTROL","MANAGE_SECURITY_DEVICES"); }
    private static void require(String... required) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated()) throw new AccessDeniedException("Permission required");
        Set<String> authorities=new HashSet<>();auth.getAuthorities().forEach(a->authorities.add(a.getAuthority()));
        if(!authorities.containsAll(Arrays.asList(required))) throw new AccessDeniedException("Permission required");
    }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    private static <T> ResponseEntity<T> created(T body) { return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(body); }
    private static ResponseEntity<Void> empty() { return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build(); }
}
