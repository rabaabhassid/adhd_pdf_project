package com.adhdpdf.study.quiz;

import java.util.List;

public record QuizQuestionsResponse(int sectionIndex, int questionCount, List<QuizQuestionPublic> questions) {}
