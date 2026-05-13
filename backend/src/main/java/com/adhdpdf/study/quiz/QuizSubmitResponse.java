package com.adhdpdf.study.quiz;

import java.util.List;

public record QuizSubmitResponse(int correctCount, int totalCount, List<QuizAnswerResult> results) {}
