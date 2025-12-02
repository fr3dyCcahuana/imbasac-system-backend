package com.paulfernandosr.possystembackend.user.application;

import com.paulfernandosr.possystembackend.role.domain.service.RoleService;
import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.station.domain.port.output.StationRepository;
import com.paulfernandosr.possystembackend.user.application.result.UserProfile;
import com.paulfernandosr.possystembackend.user.domain.port.input.GetFullUserInfoUseCase;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetFullUserInfoService implements GetFullUserInfoUseCase {
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final StationRepository stationRepository;

    @Override
    public User getFullUserInfoById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with identification: " + userId));

        user.getRole().setPermissions(roleService.getRolePermissions(user.getRole().getId()));
        user.setPermissions(userRepository.findUserPermissionsByUserId(user.getId()));

        return user;
    }

    @Override
    public UserProfile getFullUserInfoByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

        user.getRole().setPermissions(roleService.getRolePermissions(user.getRole().getId()));
        user.setPermissions(userRepository.findUserPermissionsByUserId(user.getId()));

        if (user.isOnRegister()) {
            return new UserProfile(user, stationRepository.findByUserOnRegister(user).orElse(null));
        }

        return new UserProfile(user, null);
    }
}
