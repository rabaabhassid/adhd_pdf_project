package com.adhdpdf.study.quiz;

/**
 * Body for per-question verification. {@link #selectedIndex} is 0–3.
 */
public record QuizVerifyRequest(int selectedIndex) {}
