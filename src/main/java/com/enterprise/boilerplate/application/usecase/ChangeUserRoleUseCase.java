package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.ChangeRoleRequest;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.InvalidRoleException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import org.springframework.stereotype.Service;

@Service
public class ChangeUserRoleUseCase {

    private final UserRepository userRepository;

    public ChangeUserRoleUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse execute(String callerId, String targetUserId, ChangeRoleRequest request) {
        var callId = UserId.of(callerId);
        var targetId = UserId.of(targetUserId);

        User caller = userRepository.findById(callId)
                .orElseThrow(() -> new UserNotFoundException(callerId));

        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        User.Role newRole = parseRole(request.role());

        target.changeRole(newRole, caller);
        userRepository.save(target);

        return UserResponse.from(target);
    }

    private User.Role parseRole(String value) {
        try {
            return User.Role.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRoleException(value);
        }
    }
}
