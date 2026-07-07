package com.enterprise.boilerplate.application.port.out;

import com.enterprise.boilerplate.application.dto.RefreshTokenRedemption;
import com.enterprise.boilerplate.domain.entity.User;

import java.util.Optional;

public interface TokenServicePort {

    String issueAccessToken(User user);

    String issueRefreshToken(User user);

    Optional<String> validateAccessToken(String token);

    Optional<String> resolveUserIdFromRefreshToken(String refreshToken);

    /**
     * Atomically consumes {@code refreshToken}: if it is live, marks it used and
     * returns the owning user id ({@link RefreshTokenRedemption.Redeemed}); if it was
     * already consumed within the reuse-detection window, returns the owning user id
     * without further side effects ({@link RefreshTokenRedemption.Reused}) so the caller
     * can treat this as a theft signal; otherwise returns
     * {@link RefreshTokenRedemption.Invalid}.
     *
     * This single atomic operation replaces what would otherwise be a check-then-act
     * sequence (check reuse, resolve owner, revoke) across separate calls — a sequence
     * that leaves a race window where two concurrent replays of the same stolen token
     * could both read it as still valid before either finishes revoking it.
     */
    RefreshTokenRedemption redeemRefreshToken(String refreshToken);

    void revokeRefreshToken(String refreshToken);

    void revokeAllRefreshTokens(String userId);
}
