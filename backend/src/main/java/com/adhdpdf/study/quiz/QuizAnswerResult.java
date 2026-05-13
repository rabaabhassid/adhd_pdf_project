package com.adhdpdf.study.quiz;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Feedback for one question after submit. {@link #whyChosenWrong()} is null when the user was correct.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizAnswerResult(
        int questionIndex,
        int selectedIndex,
        int correctIndex,
        boolean correct,
        String whyCorrect,
        String whyChosenWrong
) {}
