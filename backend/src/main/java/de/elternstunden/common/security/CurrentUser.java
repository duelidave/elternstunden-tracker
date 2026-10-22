package de.elternstunden.common.security;

import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The signed-in principal of the current request. */
public final class CurrentUser {

    private CurrentUser() {}

    public static boolean isMfaPending() {
        return current() instanceof MfaPending;
    }

    /** The admin in the MFA_PENDING state (x-roles MP). */
    public static MfaPending mfaPending() {
        if (current() instanceof MfaPending pending) {
            return pending;
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED);
    }

    /** The person in the OIDC_PENDING state (x-roles OP). */
    public static PendingOidc pendingOidc() {
        if (current() instanceof PendingOidc pending) {
            return pending;
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED);
    }

    private static @Nullable Object current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getPrincipal();
    }

    public static AppUserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
