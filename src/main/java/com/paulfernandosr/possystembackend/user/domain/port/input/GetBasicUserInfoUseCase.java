package com.paulfernandosr.possystembackend.user.domain.port.input;

import com.paulfernandosr.possystembackend.user.domain.User;

public interface GetBasicUserInfoUseCase {
    User getBasicUserInfoById(Long userId);
}
