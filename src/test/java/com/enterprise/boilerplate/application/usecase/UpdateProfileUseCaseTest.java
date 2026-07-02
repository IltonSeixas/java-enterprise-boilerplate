package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.UpdateProfileRequest;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateProfileUseCaseTest {

    private static final Email EMAIL = Email.of("user@example.com");
    private static final PasswordHash HASH = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA");

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditPort audit;

    private UpdateProfileUseCase newUseCase() {
        return new UpdateProfileUseCase(userRepository, audit);
    }

    private User existingUser() {
        return User.create(EMAIL, HASH, "Original Name", User.Role.USER);
    }

    @Test
    void execute_whenUserNotFound_throwsUserNotFoundException() {
        var useCase = newUseCase();
        UserId id = UserId.generate();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id.toString(), new UpdateProfileRequest("New Name")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void execute_withValidName_updatesAndPersistsUserAndRecordsAuditEvent() {
        var useCase = newUseCase();
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserResponse response = useCase.execute(user.getId().toString(), new UpdateProfileRequest("New Name"));

        assertThat(response.name()).isEqualTo("New Name");
        verify(userRepository).save(user);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AuditEventType.PROFILE_UPDATED);
        assertThat(captor.getValue().actorUserId()).isEqualTo(user.getId().toString());
    }
}
