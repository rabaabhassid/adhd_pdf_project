package com.adhdpdf.study.auth;

import com.adhdpdf.study.profile.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserService {

    static final String USER_ATTRIBUTE = CurrentUserService.class.getName() + ".USER";
    static final String SESSION_TOKEN_ATTRIBUTE = CurrentUserService.class.getName() + ".SESSION_TOKEN";

    private final HttpServletRequest request;

    public CurrentUserService(HttpServletRequest request) {
        this.request = request;
    }

    public AppUser requireUser() {
        Object user = request.getAttribute(USER_ATTRIBUTE);
        if (user instanceof AppUser appUser) {
            return appUser;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }

    public String rawSessionToken() {
        Object token = request.getAttribute(SESSION_TOKEN_ATTRIBUTE);
        return token instanceof String value ? value : null;
    }

    static void attach(HttpServletRequest request, AppUser user, String rawSessionToken) {
        request.setAttribute(USER_ATTRIBUTE, user);
        request.setAttribute(SESSION_TOKEN_ATTRIBUTE, rawSessionToken);
    }
}
