package com.paulfernandosr.possystembackend.user.domain.port.input;

import com.paulfernandosr.possystembackend.user.application.result.UserProfile;
import com.paulfernandosr.possystembackend.user.domain.User;

public interface GetFullUserInfoUseCase {
    User getFullUserInfoById(Long userId);
    UserProfile getFullUserInfoByUsername(String username);
}
