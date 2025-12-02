package com.paulfernandosr.possystembackend.user.domain.port.input;

import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.UserStatus;

import java.util.Collection;

public interface GetAllUsersUseCase {
    Collection<User> getAllUsers(UserStatus status);
}
