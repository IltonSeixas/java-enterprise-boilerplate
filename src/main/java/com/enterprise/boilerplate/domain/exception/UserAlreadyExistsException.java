package com.enterprise.boilerplate.domain.exception;

public final class UserAlreadyExistsException extends DomainException {

    private static final String OWNER_ALREADY_EXISTS_MESSAGE = "An owner already exists";

    public UserAlreadyExistsException(String email) {
        super("User already exists with email: " + email);
    }

    private UserAlreadyExistsException() {
        super(OWNER_ALREADY_EXISTS_MESSAGE);
    }

    public static UserAlreadyExistsException ownerAlreadyExists() {
        return new UserAlreadyExistsException();
    }
}
