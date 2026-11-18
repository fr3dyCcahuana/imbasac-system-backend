package com.paulfernandosr.possystembackend.accesscontrol.infrastructure;

import com.paulfernandosr.possystembackend.accesscontrol.application.AccessControlService;
import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlModels.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/security/access-control")
public class AccessControlRestController {
    private final AccessControlService control;
    public AccessControlRestController(AccessControlService control) { this.control=control; }
    @GetMapping("/status") public ResponseEntity<Status> status() { require(); return response(control.status()); }
    @GetMapping("/users") public ResponseEntity<Page<UserControl>> users(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        require(); return response(control.users(search,page,size));
    }
    @GetMapping("/users/{userId}") public ResponseEntity<UserControl> user(@PathVariable long userId) { require(); return response(control.user(userId)); }
    @GetMapping("/devices") public ResponseEntity<Page<DeviceRow>> devices(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        require(); return response(control.devices(search,page,size));
    }
    @GetMapping("/users/{userId}/bindings") public ResponseEntity<List<Binding>> bindings(@PathVariable long userId) { require(); return response(control.bindings(userId)); }
    @GetMapping("/users/{userId}/strikes") public ResponseEntity<Page<Strike>> strikes(@PathVariable long userId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        require(); return response(control.strikes(userId,page,size));
    }
    @GetMapping("/users/{userId}/history") public ResponseEntity<Page<Change>> history(@PathVariable long userId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        require(); return response(control.history(userId,page,size));
    }
    public record SettingsRequest(int blockDays,String reason) {}
    public record QuotaRequest(int deviceQuota,String reason) {}
    public record BindingRequest(UUID deviceId,String reason) {}
    public record ReasonRequest(String reason) {}
    public record CertificatePolicy(boolean certificateRequired) {}
    public record CertificatePolicyRequest(Boolean certificateRequired,String reason) {}
    @GetMapping("/users/{userId}/certificate-policy") public ResponseEntity<CertificatePolicy> certificatePolicy(@PathVariable long userId) {
        require(); return response(new CertificatePolicy(control.certificateRequired(userId)));
    }
    @PutMapping("/users/{userId}/certificate-policy") public ResponseEntity<CertificatePolicy> certificatePolicy(
            @PathVariable long userId,@RequestBody CertificatePolicyRequest body,Principal principal) {
        require("MANAGE_SECURITY_POLICIES");
        control.setCertificateRequired(userId,body.certificateRequired(),principal.getName(),body.reason());
        return response(new CertificatePolicy(control.certificateRequired(userId)));
    }
    @PutMapping("/settings") public ResponseEntity<Status> settings(@RequestBody SettingsRequest body,Principal principal) {
        require("MANAGE_SECURITY_BLOCKS"); control.setBlockDays(body.blockDays(),principal.getName(),body.reason()); return response(control.status());
    }
    @PutMapping("/users/{userId}/quota") public ResponseEntity<UserControl> quota(@PathVariable long userId,@RequestBody QuotaRequest body,Principal principal) {
        require("MANAGE_SECURITY_DEVICES"); control.setQuota(userId,body.deviceQuota(),principal.getName(),body.reason()); return response(control.user(userId));
    }
    @PostMapping("/users/{userId}/bindings") public ResponseEntity<List<Binding>> bind(@PathVariable long userId,@RequestBody BindingRequest body,Principal principal) {
        require("MANAGE_SECURITY_DEVICES"); control.bind(userId,body.deviceId(),principal.getName(),body.reason()); return response(control.bindings(userId));
    }
    @DeleteMapping("/users/{userId}/bindings/{bindingId}") public ResponseEntity<Void> unbind(@PathVariable long userId,@PathVariable UUID bindingId,@RequestBody ReasonRequest body,Principal principal) {
        require("MANAGE_SECURITY_DEVICES"); control.unbind(userId,bindingId,principal.getName(),body.reason()); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/users/{userId}/unblock") public ResponseEntity<UserControl> unblock(@PathVariable long userId,@RequestBody ReasonRequest body,Principal principal) {
        require("MANAGE_SECURITY_BLOCKS"); control.unblock(userId,principal.getName(),body.reason()); return response(control.user(userId));
    }
    private static void require(String... additional) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()) throw new AccessDeniedException("Security control permission required");
        Set<String> permissions=new HashSet<>(); auth.getAuthorities().forEach(a->permissions.add(a.getAuthority()));
        if(!permissions.contains("VIEW_SECURITY_CONTROL")||!permissions.containsAll(Arrays.asList(additional))) throw new AccessDeniedException("Security control permission required");
    }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
