package com.adhdpdf.study.quiz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * LLM JSON shape for quiz generation (internal).
 */
public final class QuizGenerationJson {

    private QuizGenerationJson() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Root(List<Question> questions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Question(
            String prompt,
            List<String> choices,
            int correctIndex,
            String whyCorrect,
            List<String> whyWrongExplanations
    ) {}
}
