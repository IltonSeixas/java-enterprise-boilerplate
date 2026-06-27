package com.enterprise.boilerplate.interfaces.rest;

import com.enterprise.boilerplate.application.dto.ChangePasswordRequest;
import com.enterprise.boilerplate.application.dto.ChangeRoleRequest;
import com.enterprise.boilerplate.application.dto.ListUsersRequest;
import com.enterprise.boilerplate.application.dto.PageResponse;
import com.enterprise.boilerplate.application.dto.UpdateProfileRequest;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.application.usecase.ChangePasswordUseCase;
import com.enterprise.boilerplate.application.usecase.ChangeUserRoleUseCase;
import com.enterprise.boilerplate.application.usecase.GetUserUseCase;
import com.enterprise.boilerplate.application.usecase.ListUsersUseCase;
import com.enterprise.boilerplate.application.usecase.UpdateProfileUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final GetUserUseCase getUserUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final ChangeUserRoleUseCase changeUserRoleUseCase;
    private final ListUsersUseCase listUsersUseCase;

    public UserController(GetUserUseCase getUserUseCase,
                          UpdateProfileUseCase updateProfileUseCase,
                          ChangePasswordUseCase changePasswordUseCase,
                          ChangeUserRoleUseCase changeUserRoleUseCase,
                          ListUsersUseCase listUsersUseCase) {
        this.getUserUseCase = getUserUseCase;
        this.updateProfileUseCase = updateProfileUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
        this.changeUserRoleUseCase = changeUserRoleUseCase;
        this.listUsersUseCase = listUsersUseCase;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(getUserUseCase.execute(userId, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable String id, Authentication authentication) {
        String requesterId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(getUserUseCase.execute(id, requesterId));
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> list(@RequestParam(required = false) String role,
                                                            @RequestParam(required = false) Boolean active,
                                                            @RequestParam(required = false) String name,
                                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                            Authentication authentication) {
        String callerId = (String) authentication.getPrincipal();
        var request = new ListUsersRequest(role, active, name, page, size);
        return ResponseEntity.ok(listUsersUseCase.execute(callerId, request));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request,
                                                 Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(updateProfileUseCase.execute(userId, request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        changePasswordUseCase.execute(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UserResponse> changeRole(@PathVariable String id,
                                                    @Valid @RequestBody ChangeRoleRequest request,
                                                    Authentication authentication) {
        String callerId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(changeUserRoleUseCase.execute(callerId, id, request));
    }
}
