package com.adhdpdf.study.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class AuthSessionFilter extends OncePerRequestFilter {

    private final AuthCookieService authCookieService;
    private final AuthSessionService authSessionService;

    public AuthSessionFilter(AuthCookieService authCookieService, AuthSessionService authSessionService) {
        this.authCookieService = authCookieService;
        this.authSessionService = authSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rawToken = authCookieService.extract(request.getCookies());
        authSessionService.resolve(rawToken, Instant.now())
                .ifPresent(user -> CurrentUserService.attach(request, user, rawToken));
        filterChain.doFilter(request, response);
    }
}
