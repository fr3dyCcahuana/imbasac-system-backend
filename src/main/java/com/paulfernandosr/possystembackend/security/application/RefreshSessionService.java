package com.paulfernandosr.possystembackend.security.application;

import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.deviceaccess.application.DeviceAccessPolicyService;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.security.domain.exception.InvalidSessionException;
import com.paulfernandosr.possystembackend.security.domain.port.input.RefreshSessionUseCase;
import com.paulfernandosr.possystembackend.security.domain.port.output.TokenGenerationPort;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RefreshSessionService implements RefreshSessionUseCase {
    private final SessionRepository sessionRepository;
    private final TokenGenerationPort tokenGenerationPort;
    private final UserRepository userRepository;
    private final DeviceAccessPolicyService devicePolicy;
    private final com.paulfernandosr.possystembackend.trustedclient.ControlSessionGuard controlSessions;
    public RefreshSessionService(SessionRepository sessions,TokenGenerationPort tokens,UserRepository users,DeviceAccessPolicyService policy) { this(sessions,tokens,users,policy,null); }
    @org.springframework.beans.factory.annotation.Autowired
    public RefreshSessionService(SessionRepository sessions,TokenGenerationPort tokens,UserRepository users,DeviceAccessPolicyService policy,
            com.paulfernandosr.possystembackend.trustedclient.ControlSessionGuard guard) {
        this.sessionRepository=sessions;this.tokenGenerationPort=tokens;this.userRepository=users;this.devicePolicy=policy;this.controlSessions=guard;
    }

    @Override
    public Session refreshSession(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Invalid session"));

        User user = userRepository.findByUsername(session.getUsername()).orElse(null);
        if (user == null || !user.isEnabled()) {
            sessionRepository.deleteById(sessionId);
            throw new InvalidSessionException("Invalid session");
        }

        devicePolicy.check(user);
        if(controlSessions!=null) controlSessions.checkRefresh(sessionId,session.getUsername());

        if (!sessionRepository.refreshById(sessionId)) {
            throw new InvalidSessionException("Invalid session");
        }

        session.setToken(tokenGenerationPort.generateToken(session.getUsername(), sessionId));

        return session;
    }
}
