package com.paulfernandosr.possystembackend.common.infrastructure.adapter;

import com.paulfernandosr.possystembackend.common.domain.port.output.PasswordValidationPort;
import com.paulfernandosr.possystembackend.common.infrastructure.CommonProps;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BcryptPasswordValidationPort implements PasswordValidationPort {
    private final BCryptPasswordEncoder passwordEncoder;
    private final CommonProps commonProps;

    @Override
    public boolean isPasswordValid(String password) {
        return passwordEncoder.matches(password, commonProps.getHashedPassword());
    }
}
