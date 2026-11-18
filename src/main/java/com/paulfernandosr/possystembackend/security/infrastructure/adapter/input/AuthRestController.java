package com.paulfernandosr.possystembackend.security.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService;
import com.paulfernandosr.possystembackend.accessaudit.domain.AuditException;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import com.paulfernandosr.possystembackend.security.domain.port.input.LogoutUseCase;
import com.paulfernandosr.possystembackend.security.domain.port.input.RefreshSessionUseCase;
import com.paulfernandosr.possystembackend.security.domain.port.input.LoginUseCase;
import com.paulfernandosr.possystembackend.security.infrastructure.adapter.input.request.LoginRequest;
import com.paulfernandosr.possystembackend.security.infrastructure.adapter.input.request.RefreshTokenRequest;
import com.paulfernandosr.possystembackend.security.infrastructure.adapter.input.response.AuthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthRestController {
    private final LoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RefreshSessionUseCase refreshSessionUseCase;
    private final AccessAuditService accessAudit;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,HttpServletRequest servlet) {
        servlet.setAttribute("imbasac.loginHandled",true);
        Session session;
        try {
            session = loginUseCase.login(request.getUsername(), request.getPassword());
        } catch (RuntimeException error) {
            accessAudit.loginRejected(request.getUsername(),error);
            if (accessAudit.isEnabled() && (error instanceof org.springframework.dao.DataAccessException
                    || error instanceof org.springframework.transaction.TransactionException)) throw AuditException.unavailable();
            throw error;
        }

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(session.getToken().value())
                .refreshToken(session.getId())
                .tokenType(session.getToken().type())
                .expiresIn(session.getToken().expiresIn())
                .build());
    }

    public record ClientValidation(boolean completed) {}
    @PostMapping("/client-validation")
    public ResponseEntity<Void> clientValidation(@RequestBody ClientValidation body,Principal principal,HttpServletRequest request) {
        if (principal==null) throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        accessAudit.browser(principal.getName(),(String)request.getAttribute("imbasac.accessSessionKey"),body.completed());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(@Valid @RequestBody RefreshTokenRequest request) {
        try { logoutUseCase.logout(UUID.fromString(request.getRefreshToken())); }
        catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException error) {
            if (accessAudit.isEnabled()) throw AuditException.unavailable(); throw error;
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        Session session;
        try { session=refreshSessionUseCase.refreshSession(UUID.fromString(request.getRefreshToken())); }
        catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException error) {
            if (accessAudit.isEnabled()) throw AuditException.unavailable(); throw error;
        }

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(session.getToken().value())
                .refreshToken(session.getId())
                .tokenType(session.getToken().type())
                .expiresIn(session.getToken().expiresIn())
                .build());
    }
}
