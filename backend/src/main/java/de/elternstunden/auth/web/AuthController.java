package de.elternstunden.auth.web;

import de.elternstunden.api.AuthApi;
import de.elternstunden.api.model.CaptchaChallengeDto;
import de.elternstunden.api.model.ChildClaimDto;
import de.elternstunden.api.model.LoginProviderDto;
import de.elternstunden.api.model.LoginResultDto;
import de.elternstunden.api.model.MagicLinkRedemptionDto;
import de.elternstunden.api.model.MagicLinkRequestDto;
import de.elternstunden.api.model.MasterLoginRequestDto;
import de.elternstunden.api.model.MeDto;
import de.elternstunden.api.model.MfaVerificationDto;
import de.elternstunden.api.model.OidcCompletionDto;
import de.elternstunden.api.model.OidcPendingDto;
import de.elternstunden.api.model.PasskeyChallengeDto;
import de.elternstunden.api.model.PasskeyLoginDto;
import de.elternstunden.api.model.RegistrationRequestDto;
import de.elternstunden.auth.passkey.PasskeyService;
import de.elternstunden.auth.service.AuthRequestService;
import de.elternstunden.auth.service.AuthService;
import de.elternstunden.auth.service.CaptchaService;
import de.elternstunden.auth.service.LoginFlow;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.common.security.PendingOidc;
import de.elternstunden.common.web.CurrentRequest;
import de.elternstunden.family.domain.ChildData;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Auth endpoints of the contract (FS-REG 7.1.4, FS-AUTH 7.2). */
@RestController
public class AuthController implements AuthApi {

    private final CaptchaService captcha;
    private final AuthRequestService authRequests;
    private final AuthService auth;
    private final PasskeyService passkeys;
    private final AppProperties properties;
    private final JsonMapper json;

    public AuthController(
            CaptchaService captcha,
            AuthRequestService authRequests,
            AuthService auth,
            PasskeyService passkeys,
            AppProperties properties,
            JsonMapper json) {
        this.captcha = captcha;
        this.authRequests = authRequests;
        this.auth = auth;
        this.passkeys = passkeys;
        this.properties = properties;
        this.json = json;
    }

    /** Providers with a client id, in the order of the configuration (7.2, UI-06). */
    @Override
    public ResponseEntity<List<LoginProviderDto>> listLoginProviders() {
        return ResponseEntity.ok(properties.oidc().providers().entrySet().stream()
                .filter(entry -> entry.getValue().enabled())
                .map(entry ->
                        new LoginProviderDto(entry.getKey(), entry.getValue().name()))
                .toList());
    }

    @Override
    public ResponseEntity<PasskeyChallengeDto> startPasskeyLogin() {
        PasskeyService.Challenge challenge = passkeys.startLogin(CurrentRequest.request());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PasskeyChallengeDto(challenge.challengeId(), challenge.options()));
    }

    @Override
    public ResponseEntity<LoginResultDto> loginWithPasskey(PasskeyLoginDto login) {
        return ResponseEntity.ok(result(passkeys.login(
                login.getChallengeId(), login.getCredential(), CurrentRequest.request(), CurrentRequest.response())));
    }

    @Override
    public ResponseEntity<CaptchaChallengeDto> getCaptchaChallenge() {
        CaptchaChallengeDto challenge = json.readValue(captcha.createChallengeJson(), CaptchaChallengeDto.class);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(challenge);
    }

    @Override
    public ResponseEntity<Void> registerAccount(RegistrationRequestDto request) {
        authRequests.register(request, CurrentRequest.request(), CurrentRequest.response());
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> requestMagicLink(MagicLinkRequestDto request) {
        authRequests.requestMagicLink(request, CurrentRequest.request(), CurrentRequest.response());
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<LoginResultDto> redeemMagicLink(MagicLinkRedemptionDto redemption) {
        return ResponseEntity.ok(result(auth.redeemMagicLink(
                redemption.getToken(),
                Boolean.TRUE.equals(redemption.getConfirmOtherDevice()),
                CurrentRequest.request(),
                CurrentRequest.response())));
    }

    @Override
    public ResponseEntity<LoginResultDto> verifyMfa(MfaVerificationDto verification) {
        return ResponseEntity.ok(
                result(auth.verifyMfa(verification.getCode(), CurrentRequest.request(), CurrentRequest.response())));
    }

    @Override
    public ResponseEntity<OidcPendingDto> getOidcPending() {
        PendingOidc pending = CurrentUser.pendingOidc();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new OidcPendingDto(pending.provider(), pending.email(), pending.displayName()));
    }

    @Override
    public ResponseEntity<LoginResultDto> completeOidcRegistration(OidcCompletionDto completion) {
        ChildClaimDto child = completion.getChild();
        return ResponseEntity.ok(result(auth.completeOidc(
                completion.getDisplayName(),
                new ChildData(child.getFirstName(), child.getLastName(), child.getBirthDate()),
                CurrentRequest.request(),
                CurrentRequest.response())));
    }

    private static LoginResultDto result(LoginFlow.Status status) {
        return new LoginResultDto(LoginResultDto.StatusEnum.fromValue(status.name()));
    }

    @Override
    public ResponseEntity<MeDto> loginMaster(MasterLoginRequestDto login) {
        return ResponseEntity.ok(auth.loginMaster(
                login.getEmail(),
                login.getPassword(),
                login.getTotp(),
                CurrentRequest.request(),
                CurrentRequest.response()));
    }

    @Override
    public ResponseEntity<Void> logout() {
        auth.logout(CurrentRequest.request());
        return ResponseEntity.noContent().build();
    }
}
