package com.paulfernandosr.possystembackend.user.application;

import com.paulfernandosr.possystembackend.user.domain.port.input.CreateNewUserUseCase;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateNewUserService implements CreateNewUserUseCase {
    private final UserRepository userRepository;

    @Override
    public void createNewUser(User user) {
        userRepository.create(user);
    }
}
