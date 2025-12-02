package com.paulfernandosr.possystembackend.user.domain.port.input;

import com.paulfernandosr.possystembackend.user.domain.User;

public interface UpdateUserInfoUseCase {
    void updateUserInfoById(Long userId, User user);
}
