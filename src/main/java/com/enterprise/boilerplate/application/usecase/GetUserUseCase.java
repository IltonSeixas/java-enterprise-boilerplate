package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.ForbiddenException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.UserId;

public class GetUserUseCase {

    private final UserRepository userRepository;

    public GetUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse execute(String targetUserId, String callerId) {
        var targetId = UserId.of(targetUserId);
        var callId = UserId.of(callerId);

        User caller = userRepository.findById(callId)
                .orElseThrow(() -> new UserNotFoundException(callerId));

        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        boolean isSelf = callId.equals(targetId);
        boolean isPrivileged = caller.role() == User.Role.ADMIN || caller.role() == User.Role.OWNER;

        if (!isSelf && !isPrivileged) {
            throw new ForbiddenException();
        }

        return UserResponse.from(target);
    }
}
