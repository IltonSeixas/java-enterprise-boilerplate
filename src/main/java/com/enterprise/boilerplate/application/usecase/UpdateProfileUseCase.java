package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.UpdateProfileRequest;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.UserId;

public class UpdateProfileUseCase {

    private final UserRepository userRepository;

    public UpdateProfileUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse execute(String userId, UpdateProfileRequest request) {
        var id = UserId.of(userId);
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.updateProfile(request.name());
        userRepository.save(user);

        return UserResponse.from(user);
    }
}
