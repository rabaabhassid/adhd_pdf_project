package com.adhdpdf.study.auth;

import com.adhdpdf.study.profile.AppUser;
import com.adhdpdf.study.profile.ProfileService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class AuthController {

    private final AnonymousDeviceAuthService anonymousDeviceAuthService;
    private final AuthSessionService authSessionService;
    private final AuthCookieService authCookieService;
    private final CurrentUserService currentUserService;
    private final ProfileService profileService;

    public AuthController(AnonymousDeviceAuthService anonymousDeviceAuthService,
                          AuthSessionService authSessionService,
                          AuthCookieService authCookieService, CurrentUserService currentUserService,
                          ProfileService profileService) {
        this.anonymousDeviceAuthService = anonymousDeviceAuthService;
        this.authSessionService = authSessionService;
        this.authCookieService = authCookieService;
        this.currentUserService = currentUserService;
        this.profileService = profileService;
    }

    @PostMapping("/auth/device")
    public AuthVerifyResponse device(@RequestBody AnonymousDeviceAuthRequest request, HttpServletResponse response) {
        AppUser user = anonymousDeviceAuthService.authenticate(request);
        String rawSessionToken = authSessionService.createSession(user, Instant.now());
        authCookieService.addLoginCookie(response, rawSessionToken);
        return new AuthVerifyResponse(profileService.profile(user), "/upload.html");
    }

    @PostMapping("/auth/logout")
    public Map<String, String> logout(HttpServletResponse response) {
        authSessionService.revoke(currentUserService.rawSessionToken(), Instant.now());
        authCookieService.clearCookie(response);
        return Map.of("status", "signed_out");
    }
}
