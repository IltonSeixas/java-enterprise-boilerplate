package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.UpdateProfileRequest;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.UserId;

public class UpdateProfileUseCase {

    private final UserRepository userRepository;
    private final AuditPort audit;

    public UpdateProfileUseCase(UserRepository userRepository, AuditPort audit) {
        this.userRepository = userRepository;
        this.audit = audit;
    }

    public UserResponse execute(String userId, UpdateProfileRequest request) {
        var id = UserId.of(userId);
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.updateProfile(request.name());
        userRepository.save(user);

        audit.record(AuditEvent.of(AuditEventType.PROFILE_UPDATED, userId, null));

        return UserResponse.from(user);
    }
}
