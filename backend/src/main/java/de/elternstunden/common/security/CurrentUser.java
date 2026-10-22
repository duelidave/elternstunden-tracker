package de.elternstunden.common.security;

import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The signed-in principal of the current request. */
public final class CurrentUser {

    private CurrentUser() {}

    public static AppUserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
