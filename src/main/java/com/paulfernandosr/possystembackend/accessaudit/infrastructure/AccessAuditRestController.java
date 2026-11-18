package com.paulfernandosr.possystembackend.accessaudit.infrastructure;

import com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService;
import com.paulfernandosr.possystembackend.accessaudit.domain.*;
import com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/security/access-audit")
@RequiredArgsConstructor
public class AccessAuditRestController {
    private final AccessAuditService audit;

    @GetMapping("/status") public ResponseEntity<Status> status() {
        require("VIEW_SECURITY_AUDIT"); return response(audit.status());
    }
    @GetMapping("/summary") public ResponseEntity<Summary> summary(@ModelAttribute Filters filters, Principal principal) {
        require("VIEW_SECURITY_AUDIT"); AuditQuery query=filters.query();
        return response(audit.summary(query));
    }
    @GetMapping("/events") public ResponseEntity<Page<Event>> events(@ModelAttribute Filters filters) {
        require("VIEW_SECURITY_AUDIT"); return response(audit.events(filters.query()));
    }
    @GetMapping("/events/export") public ResponseEntity<byte[]> export(@ModelAttribute Filters filters,Principal principal) {
        require("VIEW_SECURITY_AUDIT","EXPORT_SECURITY_AUDIT");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Content-Disposition","attachment; filename=\"accesos.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(audit.export(filters.query(),principal.getName()));
    }
    @GetMapping("/events/{id}") public ResponseEntity<Event> detail(@PathVariable UUID id) {
        require("VIEW_SECURITY_AUDIT"); return response(audit.event(id));
    }
    @GetMapping("/sessions") public ResponseEntity<Page<SessionRow>> sessions(@ModelAttribute Filters filters,@RequestParam(required=false) String state) {
        require("VIEW_SECURITY_AUDIT"); return response(audit.sessions(filters.query(),state));
    }
    public record RevokeRequest(String reason) {}
    @PostMapping("/sessions/{id}/revoke") public ResponseEntity<Void> revoke(@PathVariable UUID id,@RequestBody RevokeRequest request,Principal principal) {
        require("VIEW_SECURITY_AUDIT","REVOKE_SECURITY_SESSIONS"); audit.revoke(id,principal.getName(),request.reason());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    private static void require(String... permissions) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth==null || !auth.isAuthenticated()) throw new AccessDeniedException("Access audit permission required");
        Set<String> names=new HashSet<>(); auth.getAuthorities().forEach(a->names.add(a.getAuthority()));
        if (!names.containsAll(Arrays.asList(permissions))) throw new AccessDeniedException("Access audit permission required");
    }
    // Fixed fields only; no caller-selected SQL columns, order clauses or unbounded ranges.
    @lombok.Data
    public static class Filters {
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate from;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate to;
        private String username,role,type,outcome,category;
        private int page=0,size=25;
        public AuditQuery query() { return AuditQuery.of(from,to,username,role,type,outcome,page,size,category); }
    }
}
