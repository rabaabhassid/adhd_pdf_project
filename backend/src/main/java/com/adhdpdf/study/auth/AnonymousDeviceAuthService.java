package com.adhdpdf.study.auth;

import com.adhdpdf.study.profile.AppUser;
import com.adhdpdf.study.profile.AppUserRepository;
import com.adhdpdf.study.profile.ProfileService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class AnonymousDeviceAuthService {

    private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9._~-]{32,160}$");

    private final AppUserRepository appUserRepository;
    private final ProfileService profileService;
    private final TokenCodec tokenCodec;

    public AnonymousDeviceAuthService(AppUserRepository appUserRepository, ProfileService profileService,
                                      TokenCodec tokenCodec) {
        this.appUserRepository = appUserRepository;
        this.profileService = profileService;
        this.tokenCodec = tokenCodec;
    }

    @Transactional
    public AppUser authenticate(AnonymousDeviceAuthRequest request) {
        String anonymousId = request != null ? normalizeToken(request.anonymousId()) : "";
        String deviceSecret = request != null ? normalizeToken(request.deviceSecret()) : "";
        if (!validDeviceToken(anonymousId) || !validDeviceToken(deviceSecret)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anonymous device identity is invalid.");
        }

        String secretHash = tokenCodec.hash(deviceSecret);
        Instant now = Instant.now();
        AppUser user = appUserRepository.findByAnonymousId(anonymousId)
                .map(existing -> verifyExistingDevice(existing, secretHash))
                .orElseGet(() -> appUserRepository.save(AppUser.anonymousDevice(anonymousId, secretHash, now)));

        if (request != null && StringUtils.hasText(request.displayName())) {
            user.setDisplayName(request.displayName());
            user = appUserRepository.save(user);
        }
        profileService.ensurePreferences(user);
        return user;
    }

    private AppUser verifyExistingDevice(AppUser user, String secretHash) {
        if (!secretHash.equals(user.getDeviceSecretHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Anonymous device identity could not be verified.");
        }
        return user;
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean validDeviceToken(String value) {
        return SAFE_TOKEN.matcher(value).matches();
    }
}
