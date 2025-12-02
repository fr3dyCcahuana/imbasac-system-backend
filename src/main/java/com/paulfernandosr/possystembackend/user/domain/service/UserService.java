package com.paulfernandosr.possystembackend.user.domain.service;

import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public void enableUserById(Long userId) {
        userRepository.enableById(userId);
    }

    public void disableUserById(Long userId) {
        userRepository.disableById(userId);
    }
}
