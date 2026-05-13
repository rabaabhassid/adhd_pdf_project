package com.adhdpdf.study.auth;

public record AnonymousDeviceAuthRequest(String anonymousId, String deviceSecret, String displayName) {}
