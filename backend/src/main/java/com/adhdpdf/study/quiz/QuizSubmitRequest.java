package com.adhdpdf.study.quiz;

import java.util.List;

/**
 * Selected answer index (0–3) per question, in the same order as {@link QuizQuestionsResponse#questions()}.
 */
public record QuizSubmitRequest(List<Integer> selectedIndexes) {}
