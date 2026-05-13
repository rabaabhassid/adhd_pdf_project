package com.adhdpdf.study.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthCookieService {

    private final String cookieName;
    private final boolean secure;
    private final Duration sessionDuration;

    public AuthCookieService(
            @Value("${study.auth.cookie-name:study_session}") String cookieName,
            @Value("${study.auth.cookie-secure:false}") boolean secure,
            @Value("${study.auth.session-days:30}") long sessionDays) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sessionDuration = Duration.ofDays(sessionDays);
    }

    public String cookieName() {
        return cookieName;
    }

    public Duration sessionDuration() {
        return sessionDuration;
    }

    public void addLoginCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, rawToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(sessionDuration)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clearCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public String extract(Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
