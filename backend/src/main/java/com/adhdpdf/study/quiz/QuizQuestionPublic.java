package com.adhdpdf.study.quiz;

import java.util.List;

/**
 * One quiz question as returned before the user submits answers (no correct index or explanations).
 */
public record QuizQuestionPublic(int index, String prompt, List<String> choices) {}
