package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.ListUsersRequest;
import com.enterprise.boilerplate.application.dto.PageResponse;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.DomainValidationException;
import com.enterprise.boilerplate.domain.exception.ForbiddenException;
import com.enterprise.boilerplate.domain.exception.InvalidRoleException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.PageCriteria;
import com.enterprise.boilerplate.domain.repository.UserFilter;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.UserId;

import java.util.Set;

public class ListUsersUseCase {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "name", "email", "role");

    private final UserRepository userRepository;

    public ListUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public PageResponse<UserResponse> execute(String callerId, ListUsersRequest request) {
        User caller = userRepository.findById(UserId.of(callerId))
                .orElseThrow(() -> new UserNotFoundException(callerId));

        if (caller.role() != User.Role.ADMIN && caller.role() != User.Role.OWNER) {
            throw new ForbiddenException();
        }

        var filter = new UserFilter(parseRole(request.role()), request.active(), request.nameContains());
        var pageCriteria = buildPageCriteria(request);

        var result = userRepository.findAll(filter, pageCriteria);
        var content = result.content().stream().map(UserResponse::from).toList();

        return PageResponse.of(content, request.page(), request.size(), result.totalElements());
    }

    private PageCriteria buildPageCriteria(ListUsersRequest request) {
        String sortBy = (request.sortBy() == null || request.sortBy().isBlank()) ? "createdAt" : request.sortBy();
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new DomainValidationException("Invalid sortBy field: " + sortBy);
        }

        PageCriteria.SortDirection direction;
        try {
            direction = (request.direction() == null || request.direction().isBlank())
                    ? PageCriteria.SortDirection.ASC
                    : PageCriteria.SortDirection.valueOf(request.direction().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException("Invalid direction: " + request.direction());
        }

        return PageCriteria.of(request.page(), request.size(), sortBy, direction);
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
