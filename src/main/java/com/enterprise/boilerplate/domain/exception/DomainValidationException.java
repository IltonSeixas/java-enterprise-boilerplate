package com.enterprise.boilerplate.domain.exception;

/**
 * Generic invariant violation for value objects and domain records whose set of
 * possible validation messages is not fixed enough to warrant a dedicated
 * exception type (unlike {@link InvalidEmailException}, {@link InvalidNameException},
 * etc., which each represent one specific, fixed-message failure).
 */
public final class DomainValidationException extends DomainException {
    public DomainValidationException(String message) {
        super(message);
    }
}
