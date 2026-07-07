package com.enterprise.boilerplate.application.dto;

/**
 * Outcome of atomically redeeming a refresh token via
 * {@code TokenServicePort.redeemRefreshToken}. See that method's Javadoc for the
 * full contract — this type exists outside {@code application.port} because port
 * packages are restricted to interfaces only (enforced by
 * {@code LayeredArchitectureTest.ports_must_be_interfaces}).
 */
public sealed interface RefreshTokenRedemption {
    record Redeemed(String userId) implements RefreshTokenRedemption {}
    record Reused(String userId) implements RefreshTokenRedemption {}
    record Invalid() implements RefreshTokenRedemption {}
}
