package de.elternstunden.auth.service;

import de.elternstunden.api.model.ChildClaimDto;
import de.elternstunden.api.model.MagicLinkRequestDto;
import de.elternstunden.api.model.RegistrationRequestDto;
import de.elternstunden.api.model.ValidationErrorDto;
import de.elternstunden.auth.repo.AuthRequestRepository;
import de.elternstunden.auth.repo.AuthRequestRepository.Kind;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.crypto.Hashing;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.ratelimit.RateLimiter;
import de.elternstunden.common.time.BusinessTime;
import de.elternstunden.common.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Synchronous part of registration and magic-link requests (7.1.4 steps 1 to 3, 7.2 "Anfordern"): validation,
 * IP and global limits, captcha, then exactly one outbox row. The same operation runs for every case, so status,
 * headers and timing never reveal whether an account exists (AC-AUTH-01, AC-AUTH-16, AC-REG-02).
 */
@Service
public class AuthRequestService {

    private static final int MAX_CHILD_AGE_YEARS = 25;
    private static final String GLOBAL = "all";

    private final AuthRequestRepository requests;
    private final CaptchaService captcha;
    private final BindingCookie bindingCookie;
    private final RateLimiter rateLimiter;
    private final AppProperties.RateLimits limits;
    private final JsonMapper json;
    private final Clock clock;

    public AuthRequestService(
            AuthRequestRepository requests,
            CaptchaService captcha,
            BindingCookie bindingCookie,
            RateLimiter rateLimiter,
            AppProperties properties,
            JsonMapper json,
            Clock clock) {
        this.requests = requests;
        this.captcha = captcha;
        this.bindingCookie = bindingCookie;
        this.rateLimiter = rateLimiter;
        this.limits = properties.rateLimits();
        this.json = json;
        this.clock = clock;
    }

    public void register(RegistrationRequestDto request, HttpServletRequest http, HttpServletResponse response) {
        validateRegistration(request);
        String ip = ClientIp.rateLimitKey(http);
        rateLimiter.acquireOrThrow("register-ip-hour", ip, limits.registerPerIpHour());
        rateLimiter.acquireOrThrow("register-ip-day", ip, limits.registerPerIpDay());
        rateLimiter.acquireOrThrow("register-global", GLOBAL, limits.registerGlobal());
        captcha.verify(request.getCaptcha());

        String bindingHash = bindingCookie.ensureHash(http, response);
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("firstName", request.getChild().getFirstName().trim());
        child.put("lastName", request.getChild().getLastName().trim());
        child.put("birthDate", request.getChild().getBirthDate().toString());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("displayName", request.getDisplayName().trim());
        payload.put("child", child);
        requests.insert(
                Kind.REGISTER,
                Hashing.normalizeEmail(request.getEmail()),
                json.writeValueAsString(payload),
                bindingHash,
                clock.instant());
    }

    public void requestMagicLink(MagicLinkRequestDto request, HttpServletRequest http, HttpServletResponse response) {
        String ip = ClientIp.rateLimitKey(http);
        rateLimiter.acquireOrThrow("magic-link-ip", ip, limits.magicLinkPerIp());
        rateLimiter.acquireOrThrow("magic-link-global", GLOBAL, limits.magicLinkGlobal());
        captcha.verify(request.getCaptcha());

        String bindingHash = bindingCookie.ensureHash(http, response);
        requests.insert(
                Kind.MAGIC_LINK, Hashing.normalizeEmail(request.getEmail()), null, bindingHash, clock.instant());
    }

    /** Rules beyond the contract's schema (7.1.2, DP-03). Checked before anything else, independent of accounts. */
    private void validateRegistration(RegistrationRequestDto request) {
        List<ValidationErrorDto> errors = new ArrayList<>();
        if (request.getDisplayName().isBlank()) {
            errors.add(new ValidationErrorDto("displayName", "NotBlank", "must not be blank"));
        }
        if (!Boolean.TRUE.equals(request.getPrivacyAccepted())) {
            errors.add(new ValidationErrorDto("privacyAccepted", "AssertTrue", "must be true"));
        }
        ChildClaimDto child = request.getChild();
        if (child.getFirstName().isBlank()) {
            errors.add(new ValidationErrorDto("child.firstName", "NotBlank", "must not be blank"));
        }
        if (child.getLastName().isBlank()) {
            errors.add(new ValidationErrorDto("child.lastName", "NotBlank", "must not be blank"));
        }
        LocalDate today = BusinessTime.today(clock);
        LocalDate birthDate = child.getBirthDate();
        if (birthDate.isAfter(today)) {
            errors.add(new ValidationErrorDto("child.birthDate", "PastOrPresent", "must not be in the future"));
        } else if (birthDate.isBefore(today.minusYears(MAX_CHILD_AGE_YEARS))) {
            errors.add(new ValidationErrorDto("child.birthDate", "MaxAge", "must not be older than 25 years"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("errors", List.copyOf(errors)));
        }
    }
}
