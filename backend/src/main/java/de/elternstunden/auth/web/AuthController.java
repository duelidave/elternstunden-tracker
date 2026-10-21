package de.elternstunden.auth.web;

import de.elternstunden.api.AuthApi;
import de.elternstunden.api.model.CaptchaChallengeDto;
import de.elternstunden.api.model.MagicLinkRedemptionDto;
import de.elternstunden.api.model.MagicLinkRequestDto;
import de.elternstunden.api.model.MasterLoginRequestDto;
import de.elternstunden.api.model.MeDto;
import de.elternstunden.api.model.RegistrationRequestDto;
import de.elternstunden.auth.service.AuthRequestService;
import de.elternstunden.auth.service.AuthService;
import de.elternstunden.auth.service.CaptchaService;
import de.elternstunden.common.web.CurrentRequest;
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
    private final JsonMapper json;

    public AuthController(CaptchaService captcha, AuthRequestService authRequests, AuthService auth, JsonMapper json) {
        this.captcha = captcha;
        this.authRequests = authRequests;
        this.auth = auth;
        this.json = json;
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
    public ResponseEntity<MeDto> redeemMagicLink(MagicLinkRedemptionDto redemption) {
        return ResponseEntity.ok(auth.redeemMagicLink(
                redemption.getToken(),
                Boolean.TRUE.equals(redemption.getConfirmOtherDevice()),
                CurrentRequest.request(),
                CurrentRequest.response()));
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
