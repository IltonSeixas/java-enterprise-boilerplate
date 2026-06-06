package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.RegisterUserRequest;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.application.port.out.PasswordHasherPort;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import org.springframework.stereotype.Service;

@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasherPort passwordHasher;

    public RegisterUserUseCase(UserRepository userRepository, PasswordHasherPort passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public UserResponse execute(RegisterUserRequest request) {
        Email email = Email.of(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email.value());
        }

        PasswordHash hash = passwordHasher.hash(request.password());
        User.Role role = determineRole();
        User user = User.create(email, hash, request.name(), role);

        if (role == User.Role.OWNER) {
            userRepository.saveFirstOwner(user);
        } else {
            userRepository.save(user);
        }

        return UserResponse.from(user);
    }

    private User.Role determineRole() {
        return userRepository.hasOwner() ? User.Role.USER : User.Role.OWNER;
    }
}
