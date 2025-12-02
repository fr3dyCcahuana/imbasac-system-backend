package com.paulfernandosr.possystembackend.user.application;

import com.paulfernandosr.possystembackend.user.domain.UserStatus;
import com.paulfernandosr.possystembackend.user.domain.port.input.GetAllUsersUseCase;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class GetAllUsersService implements GetAllUsersUseCase {
    private final UserRepository userRepository;

    @Override
    public Collection<User> getAllUsers(UserStatus status) {
        return userRepository.findAll(status);
    }
}
