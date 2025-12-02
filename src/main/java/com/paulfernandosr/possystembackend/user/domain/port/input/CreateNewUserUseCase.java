package com.paulfernandosr.possystembackend.user.domain.port.input;

import com.paulfernandosr.possystembackend.user.domain.User;

public interface CreateNewUserUseCase {
    void createNewUser(User user);
}
