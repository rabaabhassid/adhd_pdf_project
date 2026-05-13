package com.adhdpdf.study.quiz;

import com.adhdpdf.study.auth.CurrentUserService;
import com.adhdpdf.study.llm.LlmSummarizationService;
import com.adhdpdf.study.llm.OpenRouterMessage;
import com.adhdpdf.study.profile.AppUser;
import com.adhdpdf.study.session.StudySection;
import com.adhdpdf.study.session.StudySessionController;
import com.adhdpdf.study.session.StudySectionRepository;
import com.adhdpdf.study.session.StudySessionEntity;
import com.adhdpdf.study.session.StudySessionStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class SectionQuizService {

    private static final int CHOICE_COUNT = 4;
    private static final int GENERATION_ATTEMPTS = 3;

    private static final String QUIZ_SYSTEM_PROMPT =
            """
You write multiple-choice check questions for a student who just read one study section.

STRICT RULES:
- Use ONLY information that appears in the SECTION TEXT. Do not invent facts.
- Output ONLY valid JSON (no markdown fences, no commentary). The JSON must parse.
- Exactly the number of questions requested.
- Each question: exactly 4 choices (strings), one correctIndex from 0 to 3.
- whyCorrect: 1–2 short sentences explaining why the correct choice matches the section.
- whyWrongExplanations: array of 4 strings, parallel to choices. For each WRONG choice (indices other than correctIndex), give a brief reason it does not follow the section. At correctIndex use an empty string "".

Keep language clear and concise (ADHD-friendly). Avoid trick questions.""";

    private final StudySessionStore studySessionStore;
    private final StudySectionRepository studySectionRepository;
    private final QuizCacheRepository quizCacheRepository;
    private final CurrentUserService currentUserService;
    private final LlmSummarizationService llmSummarizationService;
    private final ObjectMapper objectMapper;
    private final double quizTemperature;

    public SectionQuizService(
            StudySessionStore studySessionStore,
            StudySectionRepository studySectionRepository,
            QuizCacheRepository quizCacheRepository,
            CurrentUserService currentUserService,
            LlmSummarizationService llmSummarizationService,
            ObjectMapper objectMapper,
            @Value("${openrouter.quiz.temperature:0.25}") double quizTemperature) {
        this.studySessionStore = studySessionStore;
        this.studySectionRepository = studySectionRepository;
        this.quizCacheRepository = quizCacheRepository;
        this.currentUserService = currentUserService;
        this.llmSummarizationService = llmSummarizationService;
        this.objectMapper = objectMapper;
        this.quizTemperature = quizTemperature;
    }

    public QuizQuestionsResponse getQuestions(String sessionId, int sectionIndex) {
        AppUser user = currentUserService.requireUser();
        SectionRead read = readSectionOrThrow(user, sessionId, sectionIndex);
        StudySection section = read.section();
        if (!StudySessionController.quizAvailableForSection(section)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not available for this section");
        }
        QuizCachePayload.CachedQuiz cached = loadOrGenerateQuiz(user, read.session(), sectionIndex, section);
        return toQuestionsResponse(sectionIndex, cached);
    }

    public QuizAnswerResult verifyOne(
            String sessionId, int sectionIndex, int questionIndex, QuizVerifyRequest request) {
        AppUser user = currentUserService.requireUser();
        Objects.requireNonNull(request, "request");
        if (request.selectedIndex() < 0 || request.selectedIndex() >= CHOICE_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid choice index");
        }
        SectionRead read = readSectionOrThrow(user, sessionId, sectionIndex);
        QuizCachePayload.CachedQuiz cached = readCachedQuiz(read.session(), sectionIndex)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No quiz loaded for this section. Open the quiz first."));
        if (questionIndex < 0 || questionIndex >= cached.questions().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid question index");
        }
        QuizCachePayload.CachedQuestion q = cached.questions().get(questionIndex);
        int selectedIndex = request.selectedIndex();
        boolean ok = selectedIndex == q.correctIndex();
        String whyWrong = null;
        if (!ok) {
            whyWrong = explanationForWrongChoice(q, selectedIndex);
        }
        return new QuizAnswerResult(
                questionIndex, selectedIndex, q.correctIndex(), ok, q.whyCorrect().strip(), whyWrong);
    }

    public QuizSubmitResponse submit(String sessionId, int sectionIndex, QuizSubmitRequest request) {
        AppUser user = currentUserService.requireUser();
        Objects.requireNonNull(request, "request");
        List<Integer> selected = request.selectedIndexes();
        if (selected == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "selectedIndexes is required");
        }
        SectionRead read = readSectionOrThrow(user, sessionId, sectionIndex);
        QuizCache cacheRow = quizCacheRepository.findBySessionAndSectionIndex(read.session(), sectionIndex)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No quiz loaded for this section. Open the quiz first."));
        QuizCachePayload.CachedQuiz cached = parseCachedQuiz(cacheRow.getQuestionsJson())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No quiz loaded for this section. Open the quiz first."));
        if (selected.size() != cached.questions().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong number of answers");
        }
        for (int i = 0; i < selected.size(); i++) {
            int sel = selected.get(i);
            if (sel < 0 || sel >= CHOICE_COUNT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid choice index");
            }
        }

        List<QuizAnswerResult> results = new ArrayList<>(cached.questions().size());
        int correctCount = 0;
        for (int i = 0; i < cached.questions().size(); i++) {
            QuizCachePayload.CachedQuestion q = cached.questions().get(i);
            int selectedIndex = selected.get(i);
            boolean ok = selectedIndex == q.correctIndex();
            if (ok) {
                correctCount++;
            }
            String whyWrong = null;
            if (!ok) {
                whyWrong = explanationForWrongChoice(q, selectedIndex);
            }
            results.add(new QuizAnswerResult(
                    i, selectedIndex, q.correctIndex(), ok, q.whyCorrect().strip(), whyWrong));
        }
        cacheRow.recordScore(correctCount, cached.questions().size(), java.time.Instant.now());
        quizCacheRepository.save(cacheRow);
        return new QuizSubmitResponse(correctCount, cached.questions().size(), results);
    }

    private static String explanationForWrongChoice(QuizCachePayload.CachedQuestion q, int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= q.whyWrongExplanations().size()) {
            return "This option is not supported by the section.";
        }
        String s = q.whyWrongExplanations().get(selectedIndex);
        if (!StringUtils.hasText(s)) {
            return "This option does not match what the section states.";
        }
        return s.strip();
    }

    private SectionRead readSectionOrThrow(AppUser user, String sessionId, int sectionIndex) {
        StudySessionEntity session = studySessionStore
                .findEntityForUser(sessionId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        StudySection section = studySectionRepository.findBySession_IdAndSectionIndex(session.getId(), sectionIndex)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"))
                .toDomain();
        return new SectionRead(session, section);
    }

    private QuizCachePayload.CachedQuiz loadOrGenerateQuiz(
            AppUser user, StudySessionEntity session, int sectionIndex, StudySection section) {
        Optional<QuizCachePayload.CachedQuiz> cached = readCachedQuiz(session, sectionIndex);
        if (cached.isPresent()) {
            return cached.get();
        }
        QuizCachePayload.CachedQuiz generated = generateCachedQuiz(section.summary().strip());
        try {
            String json = objectMapper.writeValueAsString(generated);
            quizCacheRepository.save(new QuizCache(user, session, sectionIndex, json, java.time.Instant.now()));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Quiz could not be saved. Try again.");
        }
        return generated;
    }

    private Optional<QuizCachePayload.CachedQuiz> readCachedQuiz(StudySessionEntity session, int sectionIndex) {
        return quizCacheRepository.findBySessionAndSectionIndex(session, sectionIndex)
                .flatMap(row -> parseCachedQuiz(row.getQuestionsJson()));
    }

    private Optional<QuizCachePayload.CachedQuiz> parseCachedQuiz(String json) {
        try {
            return Optional.of(objectMapper.readValue(json, QuizCachePayload.CachedQuiz.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private QuizCachePayload.CachedQuiz generateCachedQuiz(String sectionText) {
        int n = questionCountForSectionText(sectionText.length());
        for (int attempt = 1; attempt <= GENERATION_ATTEMPTS; attempt++) {
            Optional<QuizCachePayload.CachedQuiz> parsed = tryGenerateOnce(sectionText, n);
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Quiz could not be generated. Try again.");
    }

    private Optional<QuizCachePayload.CachedQuiz> tryGenerateOnce(String sectionText, int questionCount) {
        String userMessage =
                """
                Generate exactly """
                        + questionCount
                        + """
                 questions.

                JSON shape:
                {"questions":[{"prompt":"...","choices":["","","",""],"correctIndex":0,"whyCorrect":"...","whyWrongExplanations":["","","",""]}]}

                SECTION TEXT:
                ---
                """
                        + sectionText
                        + """
                ---
                """;

        Optional<String> raw =
                llmSummarizationService.completeAuxiliaryChat(
                        List.of(
                                new OpenRouterMessage("system", QUIZ_SYSTEM_PROMPT),
                                new OpenRouterMessage("user", userMessage)),
                        quizTemperature);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        String json = stripOptionalMarkdownCodeFence(raw.get());
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        QuizGenerationJson.Root root;
        try {
            root = objectMapper.readValue(json, QuizGenerationJson.Root.class);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        if (root.questions() == null || root.questions().size() != questionCount) {
            return Optional.empty();
        }
        List<QuizCachePayload.CachedQuestion> built = new ArrayList<>(questionCount);
        for (QuizGenerationJson.Question q : root.questions()) {
            Optional<QuizCachePayload.CachedQuestion> cq = validateAndCacheQuestion(q);
            if (cq.isEmpty()) {
                return Optional.empty();
            }
            built.add(cq.get());
        }
        return Optional.of(new QuizCachePayload.CachedQuiz(List.copyOf(built)));
    }

    private static Optional<QuizCachePayload.CachedQuestion> validateAndCacheQuestion(QuizGenerationJson.Question q) {
        if (q == null || !StringUtils.hasText(q.prompt())) {
            return Optional.empty();
        }
        List<String> choices = q.choices();
        if (choices == null || choices.size() != CHOICE_COUNT) {
            return Optional.empty();
        }
        for (String c : choices) {
            if (!StringUtils.hasText(c)) {
                return Optional.empty();
            }
        }
        int correct = q.correctIndex();
        if (correct < 0 || correct >= CHOICE_COUNT) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(q.whyCorrect())) {
            return Optional.empty();
        }
        List<String> wrong = q.whyWrongExplanations();
        if (wrong == null || wrong.size() != CHOICE_COUNT) {
            return Optional.empty();
        }
        List<String> wrongCopy = new ArrayList<>(CHOICE_COUNT);
        for (int i = 0; i < CHOICE_COUNT; i++) {
            wrongCopy.add(wrong.get(i) != null ? wrong.get(i) : "");
        }
        return Optional.of(new QuizCachePayload.CachedQuestion(
                q.prompt().strip(),
                choices.stream().map(String::strip).toList(),
                correct,
                q.whyCorrect().strip(),
                List.copyOf(wrongCopy)));
    }

    private static int questionCountForSectionText(int len) {
        if (len < 1200) {
            return 2;
        }
        if (len < 3200) {
            return 3;
        }
        if (len < 5500) {
            return 4;
        }
        return 5;
    }

    private static QuizQuestionsResponse toQuestionsResponse(int sectionIndex, QuizCachePayload.CachedQuiz cached) {
        List<QuizQuestionPublic> pub = new ArrayList<>(cached.questions().size());
        for (int i = 0; i < cached.questions().size(); i++) {
            QuizCachePayload.CachedQuestion q = cached.questions().get(i);
            pub.add(new QuizQuestionPublic(i, q.prompt(), q.choices()));
        }
        return new QuizQuestionsResponse(sectionIndex, pub.size(), List.copyOf(pub));
    }

    private static String stripOptionalMarkdownCodeFence(String s) {
        String t = s.strip();
        if (!t.startsWith("```")) {
            return t;
        }
        int firstNl = t.indexOf('\n');
        if (firstNl > 0) {
            t = t.substring(firstNl + 1);
        } else {
            t = t.substring(3).strip();
        }
        int close = t.lastIndexOf("```");
        if (close >= 0) {
            t = t.substring(0, close).strip();
        }
        return t;
    }

    private record SectionRead(StudySessionEntity session, StudySection section) {}
}
