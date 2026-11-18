package com.paulfernandosr.possystembackend.security.application;

import com.paulfernandosr.possystembackend.security.domain.exception.InvalidCredentialsException;
import com.paulfernandosr.possystembackend.deviceaccess.application.DeviceAccessPolicyService;
import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.security.domain.port.input.LoginUseCase;
import com.paulfernandosr.possystembackend.security.domain.port.output.AuthenticationPort;
import com.paulfernandosr.possystembackend.security.domain.port.output.TokenGenerationPort;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.exception.UserNotFoundException;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LoginService implements LoginUseCase {
    private final AuthenticationPort authenticationPort;
    private final TokenGenerationPort tokenGenerationPort;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final DeviceAccessPolicyService devicePolicy;
    private final com.paulfernandosr.possystembackend.trustedclient.ControlSessionGuard controlSessions;
    public LoginService(AuthenticationPort authenticationPort,TokenGenerationPort tokenGenerationPort,SessionRepository sessionRepository,UserRepository userRepository,DeviceAccessPolicyService devicePolicy) {
        this(authenticationPort,tokenGenerationPort,sessionRepository,userRepository,devicePolicy,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public LoginService(AuthenticationPort authenticationPort,TokenGenerationPort tokenGenerationPort,SessionRepository sessionRepository,UserRepository userRepository,DeviceAccessPolicyService devicePolicy,
            com.paulfernandosr.possystembackend.trustedclient.ControlSessionGuard controlSessions) {
        this.authenticationPort=authenticationPort;this.tokenGenerationPort=tokenGenerationPort;this.sessionRepository=sessionRepository;
        this.userRepository=userRepository;this.devicePolicy=devicePolicy;this.controlSessions=controlSessions;
    }

    @Override
    public Session login(String username, String password) {
        // Credential snapshot stays inside the authentication boundary, never in public User DTOs.
        var credentialSnapshot=controlSessions==null?null:controlSessions.capture(username);
        boolean isAuthenticated = authenticationPort.authenticate(username, password);

        if (!isAuthenticated) {
            throw new InvalidCredentialsException("User credentials are invalid");
        }

        User user=userRepository.findByUsername(username).orElseThrow(()->new InvalidCredentialsException("User credentials are invalid"));

        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("User credentials are invalid");
        }

        try { devicePolicy.check(user); }
        catch(com.paulfernandosr.possystembackend.deviceaccess.domain.DevicePolicyException error) {
            devicePolicy.recordUnauthorizedLogin(user,error);throw error;
        }

        if (user.requiresCashSession() && user.isNotOnRegister()) {
            throw new InvalidCredentialsException("User requires cash session and is not on register with identification: " + username);
        }

        UUID sessionId = UUID.randomUUID();
        if(controlSessions!=null) controlSessions.register(user,sessionId,credentialSnapshot);
        Session session = Session.builder()
                .id(sessionId)
                .username(username)
                .token(tokenGenerationPort.generateToken(username, sessionId))
                .build();

        sessionRepository.create(session);

        return session;
    }
}
