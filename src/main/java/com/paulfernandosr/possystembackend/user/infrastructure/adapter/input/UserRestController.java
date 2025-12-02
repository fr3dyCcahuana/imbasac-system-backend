package com.paulfernandosr.possystembackend.user.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.user.application.result.UserProfile;
import com.paulfernandosr.possystembackend.user.domain.UserStatus;
import com.paulfernandosr.possystembackend.user.domain.port.input.CreateNewUserUseCase;
import com.paulfernandosr.possystembackend.user.domain.port.input.GetAllUsersUseCase;
import com.paulfernandosr.possystembackend.user.domain.port.input.GetFullUserInfoUseCase;
import com.paulfernandosr.possystembackend.user.domain.port.input.UpdateUserInfoUseCase;
import com.paulfernandosr.possystembackend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserRestController {
    private final CreateNewUserUseCase createNewUserUseCase;
    private final GetFullUserInfoUseCase getFullUserInfoUseCase;
    private final GetAllUsersUseCase getAllUsersUseCase;
    private final UpdateUserInfoUseCase updateUserInfoUseCase;

    @PostMapping
    public ResponseEntity<Void> createNewUser(@RequestBody User user) {
        createNewUserUseCase.createNewUser(user);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<User>>> getAllUsers(@RequestParam(required = false) UserStatus status) {
        return ResponseEntity.ok(SuccessResponse.ok(getAllUsersUseCase.getAllUsers(status)));
    }

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<UserProfile>> getUserProfile(Authentication authentication) {
        return ResponseEntity.ok(SuccessResponse.ok(getFullUserInfoUseCase.getFullUserInfoByUsername(authentication.getName())));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<SuccessResponse<User>> getFullUserInfo(@PathVariable Long userId) {
        return ResponseEntity.ok(SuccessResponse.ok(getFullUserInfoUseCase.getFullUserInfoById(userId)));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Void> updateUserInfo(@PathVariable Long userId, @RequestBody User user) {
        updateUserInfoUseCase.updateUserInfoById(userId, user);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
