package com.paulfernandosr.possystembackend.user.domain.port.output;

import com.paulfernandosr.possystembackend.permission.domain.Permission;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.UserStatus;

import java.util.Collection;
import java.util.Optional;

public interface UserRepository {
    void create(User user);

    Optional<User> findById(Long userId);

    Optional<User> findByUsername(String username);

    Collection<Permission> findUserPermissionsByUserId(Long userId);

    Collection<User> findAll(UserStatus status);

    void updateById(Long userId, User user);

    void enableById(Long userId);

    void disableById(Long userId);
}
