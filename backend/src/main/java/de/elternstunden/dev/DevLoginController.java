package de.elternstunden.dev;

import de.elternstunden.api.DevApi;
import de.elternstunden.api.model.DevLoginRequestDto;
import de.elternstunden.api.model.MeDto;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.auth.service.AuthService;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.web.CurrentRequest;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role switcher for manual tests (D-26, FS-DEV 7.12). The bean exists only with the dev profile; without it the
 * endpoint has no handler and answers 404 (AC-DEV-01).
 */
@RestController
@Profile("dev")
public class DevLoginController implements DevApi {

    private final AppUserRepository users;
    private final AuthService auth;

    public DevLoginController(AppUserRepository users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    @Override
    public ResponseEntity<MeDto> devLoginAs(DevLoginRequestDto request) {
        AppUser user = account(request.getAccount()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return ResponseEntity.ok(auth.loginAs(user, CurrentRequest.request(), CurrentRequest.response()));
    }

    private Optional<AppUser> account(DevLoginRequestDto.AccountEnum account) {
        return switch (account) {
            case FAMILY -> users.findByEmail(DevAccounts.FAMILY);
            case PARTNER -> users.findByEmail(DevAccounts.PARTNER);
            case UNASSIGNED -> users.findByEmail(DevAccounts.UNASSIGNED);
            case ADMIN -> users.findByEmail(DevAccounts.ADMIN);
            case MASTER -> users.findMaster();
        };
    }
}
