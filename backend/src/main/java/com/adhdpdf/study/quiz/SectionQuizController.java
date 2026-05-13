package com.adhdpdf.study.quiz;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/session")
public class SectionQuizController {

    private final SectionQuizService sectionQuizService;

    public SectionQuizController(SectionQuizService sectionQuizService) {
        this.sectionQuizService = sectionQuizService;
    }

    @GetMapping("/{sessionId}/sections/{sectionIndex}/quiz")
    public QuizQuestionsResponse getQuiz(
            @PathVariable String sessionId, @PathVariable int sectionIndex) {
        return sectionQuizService.getQuestions(sessionId, sectionIndex);
    }

    @PostMapping("/{sessionId}/sections/{sectionIndex}/quiz/questions/{questionIndex}/verify")
    public QuizAnswerResult verify(
            @PathVariable String sessionId,
            @PathVariable int sectionIndex,
            @PathVariable int questionIndex,
            @RequestBody QuizVerifyRequest body) {
        return sectionQuizService.verifyOne(sessionId, sectionIndex, questionIndex, body);
    }

    @PostMapping("/{sessionId}/sections/{sectionIndex}/quiz/submit")
    public QuizSubmitResponse submit(
            @PathVariable String sessionId,
            @PathVariable int sectionIndex,
            @RequestBody QuizSubmitRequest body) {
        return sectionQuizService.submit(sessionId, sectionIndex, body);
    }
}
