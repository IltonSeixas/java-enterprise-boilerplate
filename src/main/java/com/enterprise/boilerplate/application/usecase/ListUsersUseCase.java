package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.ListUsersRequest;
import com.enterprise.boilerplate.application.dto.PageResponse;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.ForbiddenException;
import com.enterprise.boilerplate.domain.exception.InvalidRoleException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.PageCriteria;
import com.enterprise.boilerplate.domain.repository.UserFilter;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.UserId;

public class ListUsersUseCase {

    private final UserRepository userRepository;

    public ListUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public PageResponse<UserResponse> execute(String callerId, ListUsersRequest request) {
        User caller = userRepository.findById(UserId.of(callerId))
                .orElseThrow(() -> new UserNotFoundException(callerId));

        if (caller.getRole() != User.Role.ADMIN && caller.getRole() != User.Role.OWNER) {
            throw new ForbiddenException();
        }

        var filter = new UserFilter(parseRole(request.role()), request.active(), request.nameContains());
        var pageCriteria = new PageCriteria(request.page(), request.size());

        var result = userRepository.findAll(filter, pageCriteria);
        var content = result.content().stream().map(UserResponse::from).toList();

        return PageResponse.of(content, request.page(), request.size(), result.totalElements());
    }

    private User.Role parseRole(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return User.Role.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRoleException(value);
        }
    }
}
