package com.paulfernandosr.possystembackend.user.application;

import com.paulfernandosr.possystembackend.user.domain.port.input.UpdateUserInfoUseCase;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateUserInfoService implements UpdateUserInfoUseCase {
    private final UserRepository userRepository;

    @Override
    public void updateUserInfoById(Long userId, User user) {
        userRepository.updateById(userId, user);
    }
}
