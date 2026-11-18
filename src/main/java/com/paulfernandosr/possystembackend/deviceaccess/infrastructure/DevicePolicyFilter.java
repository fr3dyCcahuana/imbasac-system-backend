package com.paulfernandosr.possystembackend.deviceaccess.infrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.deviceaccess.application.DeviceAccessPolicyService;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DevicePolicyException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
@RequiredArgsConstructor
public class DevicePolicyFilter extends OncePerRequestFilter {
    private final DeviceAccessPolicyService policy;
    private final ObjectMapper mapper;
    private final com.paulfernandosr.possystembackend.trustedclient.ControlSessionGuard controlSessions;
    public DevicePolicyFilter(DeviceAccessPolicyService policy,ObjectMapper mapper) { this(policy,mapper,null); }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        // Login checks the new identity inside LoginService; logout must remain available.
        return "POST".equals(request.getMethod()) &&
                ("/auth/login".equals(request.getServletPath()) || "/auth/logout".equals(request.getServletPath()));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if (policy.isEnabled() && authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            try {
                policy.checkUsername(authentication.getName());
                if(controlSessions!=null && request.getAttribute("imbasac.accessSessionKey") instanceof String sid
                        && !controlSessions.active(sid,authentication.getName(),true))
                    throw new com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException(
                            com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException.Code.ACCESS_DEVICE_NOT_AUTHORIZED);
            } catch(com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException exception) {
                SecurityContextHolder.clearContext();
                var error=com.paulfernandosr.possystembackend.trustedclient.AccessControlError.from(exception);
                response.setStatus(error.status());response.setContentType("application/json");response.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-store");
                mapper.writeValue(response.getWriter(),error);return;
            } catch (DevicePolicyException exception) {
                SecurityContextHolder.clearContext();
                DevicePolicyError error=DevicePolicyError.from(exception);
                response.setStatus(error.status());
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-store");
                mapper.writeValue(response.getWriter(),error);
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
