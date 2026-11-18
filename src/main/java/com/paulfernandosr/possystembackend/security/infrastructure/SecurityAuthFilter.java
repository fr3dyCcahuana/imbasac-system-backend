package com.paulfernandosr.possystembackend.security.infrastructure;

import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class SecurityAuthFilter extends OncePerRequestFilter {
    private final JsonWebTokenUtils jsonWebTokenUtils;
    private final UserDetailsService userDetailsService;
    private final SessionRepository sessionRepository;
    private final com.paulfernandosr.possystembackend.trustedclient.ControlSessionGuard controlSessions;
    public SecurityAuthFilter(JsonWebTokenUtils tokens,UserDetailsService users,SessionRepository sessions) { this(tokens,users,sessions,null); }
    @org.springframework.beans.factory.annotation.Autowired
    public SecurityAuthFilter(JsonWebTokenUtils tokens,UserDetailsService users,SessionRepository sessions,
            com.paulfernandosr.possystembackend.trustedclient.ControlSessionGuard guard) {
        this.jsonWebTokenUtils=tokens;this.userDetailsService=users;this.sessionRepository=sessions;this.controlSessions=guard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            var identity = jsonWebTokenUtils.parseAccessToken(token);
            final String username = identity.username();
            // Signed, valid access token identity is diagnostic evidence, not access authorization.
            request.setAttribute("imbasac.verifiedTokenUsername",username);
            request.setAttribute("imbasac.verifiedTokenSessionKey",identity.sessionId());

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                boolean accountAllowed=userDetails.isEnabled() && userDetails.isAccountNonLocked()
                        && userDetails.isAccountNonExpired() && userDetails.isCredentialsNonExpired();
                boolean sessionActive=accountAllowed && sessionRepository.isActive(identity.sessionId(),username);
                boolean controlAllowed=sessionActive && (controlSessions==null || controlSessions.active(identity.sessionId(),username,false));
                if (controlAllowed) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    request.setAttribute("imbasac.authenticatedUsername",username);
                    request.setAttribute("imbasac.accessSessionKey",identity.sessionId());
                    request.setAttribute("imbasac.accessTokenExpiresAt",identity.expiresAt());
                } else {
                    request.setAttribute(com.paulfernandosr.possystembackend.accessaudit.domain.AuditFailure.ATTRIBUTE,
                            !accountAllowed?"SESSION_ACCOUNT_DISABLED":!sessionActive?"SESSION_NOT_ACTIVE":"SESSION_POLICY_REJECTED");
                }
            }
        } catch (Exception exception) {
            request.setAttribute(com.paulfernandosr.possystembackend.accessaudit.domain.AuditFailure.ATTRIBUTE,
                    exception instanceof io.jsonwebtoken.ExpiredJwtException?"SESSION_TOKEN_EXPIRED"
                    : exception instanceof io.jsonwebtoken.JwtException || exception instanceof IllegalArgumentException?"SESSION_TOKEN_INVALID"
                    :"SESSION_VALIDATION_UNAVAILABLE");
            // Parser/Redis messages may contain credentials or connection details.
            log.debug("Authentication rejected ({})", exception.getClass().getSimpleName());
        }

        filterChain.doFilter(request, response);
    }
}
