package com.adhdpdf.study.auth;

import com.adhdpdf.study.profile.AppUser;
import com.adhdpdf.study.profile.AppUserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class AuthSessionService {

    private final UserSessionRepository userSessionRepository;
    private final AppUserRepository appUserRepository;
    private final TokenCodec tokenCodec;
    private final AuthCookieService authCookieService;

    public AuthSessionService(UserSessionRepository userSessionRepository, AppUserRepository appUserRepository,
                              TokenCodec tokenCodec, AuthCookieService authCookieService) {
        this.userSessionRepository = userSessionRepository;
        this.appUserRepository = appUserRepository;
        this.tokenCodec = tokenCodec;
        this.authCookieService = authCookieService;
    }

    @Transactional
    public String createSession(AppUser user, Instant now) {
        AppUser managedUser = appUserRepository.findById(user.getId()).orElseThrow();
        managedUser.markLogin(now);
        String rawToken = tokenCodec.newOpaqueToken();
        String tokenHash = tokenCodec.hash(rawToken);
        userSessionRepository.save(new UserSession(managedUser, tokenHash, now.plus(authCookieService.sessionDuration()), now));
        return rawToken;
    }

    @Transactional
    public Optional<AppUser> resolve(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return userSessionRepository.findByTokenHashAndRevokedAtIsNull(tokenCodec.hash(rawToken))
                .filter(session -> !session.getExpiresAt().isBefore(now))
                .map(session -> {
                    session.touch(now);
                    AppUser user = session.getUser();
                    user.markActive(now);
                    return user;
                });
    }

    @Transactional
    public void revoke(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        userSessionRepository.findByTokenHashAndRevokedAtIsNull(tokenCodec.hash(rawToken))
                .ifPresent(session -> session.revoke(now));
    }
}
