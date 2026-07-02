package com.enterprise.boilerplate.domain.audit;

public enum AuditEventType {
    USER_REGISTERED,
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    PASSWORD_CHANGED,
    PROFILE_UPDATED,
    ROLE_CHANGED,
    LOGOUT,
    TOKEN_REFRESHED
}
