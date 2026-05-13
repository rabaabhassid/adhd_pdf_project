package com.adhdpdf.study.auth;

import com.adhdpdf.study.profile.UserProfileResponse;

public record AuthVerifyResponse(UserProfileResponse user, String redirectPath) {}
