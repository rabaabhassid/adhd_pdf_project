package com.adhdpdf.study.quiz;

import java.util.List;

public final class QuizCachePayload {

    private QuizCachePayload() {}

    public record CachedQuiz(List<CachedQuestion> questions) {}

    public record CachedQuestion(
            String prompt,
            List<String> choices,
            int correctIndex,
            String whyCorrect,
            List<String> whyWrongExplanations) {

        public QuizQuestionPublic toPublic(int index) {
            return new QuizQuestionPublic(index, prompt, choices);
        }
    }
}
