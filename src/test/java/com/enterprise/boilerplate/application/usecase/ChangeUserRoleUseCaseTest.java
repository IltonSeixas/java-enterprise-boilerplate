package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.ChangeRoleRequest;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.InsufficientPermissionsException;
import com.enterprise.boilerplate.domain.exception.InvalidRoleException;
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
class ChangeUserRoleUseCaseTest {

    private static final PasswordHash HASH = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA");

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditPort audit;

    private ChangeUserRoleUseCase newUseCase() {
        return new ChangeUserRoleUseCase(userRepository, audit);
    }

    private User userWithRole(User.Role role) {
        return User.create(Email.of("user-" + role.name().toLowerCase() + "@example.com"), HASH, "Alice", role);
    }

    @Test
    void execute_whenCallerNotFound_throwsUserNotFoundException() {
        var useCase = newUseCase();
        UserId callerId = UserId.generate();
        UserId targetId = UserId.generate();
        when(userRepository.findById(callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(callerId.toString(), targetId.toString(),
                new ChangeRoleRequest("admin")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void execute_whenTargetNotFound_throwsUserNotFoundException() {
        var useCase = newUseCase();
        User owner = userWithRole(User.Role.OWNER);
        UserId targetId = UserId.generate();
        when(userRepository.findById(owner.id())).thenReturn(Optional.of(owner));
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(owner.id().toString(), targetId.toString(),
                new ChangeRoleRequest("admin")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void execute_whenActorIsNotOwner_throwsInsufficientPermissionsException() {
        var useCase = newUseCase();
        User admin = userWithRole(User.Role.ADMIN);
        User target = userWithRole(User.Role.USER);
        when(userRepository.findById(admin.id())).thenReturn(Optional.of(admin));
        when(userRepository.findById(target.id())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> useCase.execute(admin.id().toString(), target.id().toString(),
                new ChangeRoleRequest("admin")))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    void execute_whenOwnerChangesOwnRole_throwsInsufficientPermissionsException() {
        var useCase = newUseCase();
        User owner = userWithRole(User.Role.OWNER);
        when(userRepository.findById(owner.id())).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> useCase.execute(owner.id().toString(), owner.id().toString(),
                new ChangeRoleRequest("admin")))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    void execute_withInvalidRole_throwsInvalidRoleException() {
        var useCase = newUseCase();
        User owner = userWithRole(User.Role.OWNER);
        User target = userWithRole(User.Role.USER);
        when(userRepository.findById(owner.id())).thenReturn(Optional.of(owner));
        when(userRepository.findById(target.id())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> useCase.execute(owner.id().toString(), target.id().toString(),
                new ChangeRoleRequest("superuser")))
                .isInstanceOf(InvalidRoleException.class);
    }

    @Test
    void execute_whenOwnerChangesAnotherUsersRole_updatesPersistsAndRecordsAuditEvent() {
        var useCase = newUseCase();
        User owner = userWithRole(User.Role.OWNER);
        User target = userWithRole(User.Role.USER);
        when(userRepository.findById(owner.id())).thenReturn(Optional.of(owner));
        when(userRepository.findById(target.id())).thenReturn(Optional.of(target));

        UserResponse response = useCase.execute(owner.id().toString(), target.id().toString(),
                new ChangeRoleRequest("admin"));

        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(target.role()).isEqualTo(User.Role.ADMIN);
        verify(userRepository).save(target);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AuditEventType.ROLE_CHANGED);
        assertThat(captor.getValue().actorUserId()).isEqualTo(owner.id().toString());
        assertThat(captor.getValue().targetUserId()).isEqualTo(target.id().toString());
    }
}
