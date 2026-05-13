package com.adhdpdf.study.llm;

import com.adhdpdf.study.text.TextChunk;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenRouter-backed adaptive summarization. Each chunk is first simplified (step 1), then one adaptive
 * formatting call reorganizes all simplified chunks into meaning-based study sections (step 2).
 */
@Service
public class LlmSummarizationService {

    private static final Logger log = LoggerFactory.getLogger(LlmSummarizationService.class);

    private static final AtomicBoolean MISSING_KEY_LOGGED = new AtomicBoolean(false);

    private static final String CHAT_PATH = "/api/v1/chat/completions";

    private final RestClient openRouterRestClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String httpReferer;
    private final String appTitle;
    private final int retryMaxAttempts;
    private final long retryDelayMillis;
    private final long pauseBetweenChunkMillis;
    private final long pauseBetweenPipelineStepsMillis;
    private final double summarizationTemperature;
    private final Double summarizationTopP;
    private final Double summarizationFrequencyPenalty;
    private final Double summarizationPresencePenalty;

    public LlmSummarizationService(
            @Qualifier("openRouterRestClient") RestClient openRouterRestClient,
            ObjectMapper objectMapper,
            @Value("${openrouter.api.key:}") String apiKey,
            @Value("${openrouter.model:openai/gpt-4o-mini}") String model,
            @Value("${openrouter.http-referer:}") String httpReferer,
            @Value("${openrouter.app-title:}") String appTitle,
            @Value("${openrouter.retry.max-attempts:6}") int retryMaxAttempts,
            @Value("${openrouter.retry.delay-millis:2500}") long retryDelayMillis,
            @Value("${openrouter.pause-between-chunk-millis:0}") long pauseBetweenChunkMillis,
            @Value("${openrouter.pause-between-pipeline-steps-millis:0}") long pauseBetweenPipelineStepsMillis,
            @Value("${openrouter.summarization.temperature:0.2}") double summarizationTemperature,
            @Value("${openrouter.summarization.top-p:#{null}}") Double summarizationTopP,
            @Value("${openrouter.summarization.frequency-penalty:#{null}}") Double summarizationFrequencyPenalty,
            @Value("${openrouter.summarization.presence-penalty:#{null}}") Double summarizationPresencePenalty) {
        this.openRouterRestClient = openRouterRestClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey != null ? apiKey.strip() : "";
        this.model = model != null ? model.strip() : "";
        this.httpReferer = httpReferer != null ? httpReferer.strip() : "";
        this.appTitle = appTitle != null ? appTitle.strip() : "";
        this.retryMaxAttempts = Math.max(1, retryMaxAttempts);
        this.retryDelayMillis = Math.max(0L, retryDelayMillis);
        this.pauseBetweenChunkMillis = Math.max(0L, pauseBetweenChunkMillis);
        this.pauseBetweenPipelineStepsMillis = Math.max(0L, pauseBetweenPipelineStepsMillis);
        this.summarizationTemperature = clamp(summarizationTemperature, 0.0, 2.0);
        this.summarizationTopP = summarizationTopP != null ? clamp(summarizationTopP, 0.0, 1.0) : null;
        this.summarizationFrequencyPenalty =
                summarizationFrequencyPenalty != null ? clamp(summarizationFrequencyPenalty, -2.0, 2.0) : null;
        this.summarizationPresencePenalty =
                summarizationPresencePenalty != null ? clamp(summarizationPresencePenalty, -2.0, 2.0) : null;
    }

    private static double clamp(double v, double min, double max) {
        return Math.min(max, Math.max(min, v));
    }

    /**
     * Adaptive session mode: step 1 simplifies each chunk, then one step-2 call reorganizes content into
     * meaning-based sections (not chunk boundaries).
     */
    public Optional<AdaptiveSessionSummarizationResult> summarizeAdaptiveSession(List<TextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Optional.of(new AdaptiveSessionSummarizationResult(List.of(), List.of()));
        }
        if (!StringUtils.hasText(apiKey)) {
            return Optional.of(new AdaptiveSessionSummarizationResult(
                    List.of(),
                    failAllOutcomes(chunks.size(), SummarizationFailureReason.MISSING_API_KEY)));
        }
        if (!StringUtils.hasText(model)) {
            return Optional.of(new AdaptiveSessionSummarizationResult(
                    List.of(),
                    failAllOutcomes(chunks.size(), SummarizationFailureReason.MODEL_NOT_CONFIGURED)));
        }

        int total = chunks.size();
        List<ChunkSummarizationOutcome> chunkOutcomes =
                failAllOutcomes(total, SummarizationFailureReason.BATCH_INTERRUPTED);
        List<String> simplifiedByChunk = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            simplifiedByChunk.add(null);
        }

        for (int i = 0; i < total; i++) {
            if (i > 0 && pauseBetweenChunkMillis > 0 && !sleepPause("OpenRouter adaptive step-1 chunks")) {
                return Optional.of(new AdaptiveSessionSummarizationResult(List.of(), chunkOutcomes));
            }
            TextChunk chunk = chunks.get(i);
            Step1ChunkOutcome step1 = simplifyChunkStep1(chunk);
            if (step1.failureReason() != SummarizationFailureReason.NONE) {
                chunkOutcomes.set(i, ChunkSummarizationOutcome.fail(step1.failureReason()));
                continue;
            }
            String simplified = step1.simplifiedText().orElse("");
            simplifiedByChunk.set(i, simplified);
            chunkOutcomes.set(i, ChunkSummarizationOutcome.ok(simplified));
        }

        String simplifiedBlock = buildStep2SimplifiedBatchBlock(simplifiedByChunk);
        if (!StringUtils.hasText(simplifiedBlock)) {
            return Optional.of(new AdaptiveSessionSummarizationResult(List.of(), chunkOutcomes));
        }

        if (pauseBetweenPipelineStepsMillis > 0) {
            try {
                Thread.sleep(pauseBetweenPipelineStepsMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.of(new AdaptiveSessionSummarizationResult(List.of(), chunkOutcomes));
            }
        }

        String step2User =
                PIPELINE_STEP2_ADAPTIVE_USER_TEMPLATE
                        .replace("{{SIMPLIFIED_BLOCK}}", simplifiedBlock);

        AssistantMessageResult step2 =
                completeChat(List.of(
                        new OpenRouterMessage("system", PIPELINE_STEP2_ADAPTIVE_SYSTEM_PROMPT),
                        new OpenRouterMessage("user", step2User)));
        if (step2.failureReason() != SummarizationFailureReason.NONE) {
            for (int i = 0; i < total; i++) {
                if (StringUtils.hasText(simplifiedByChunk.get(i))) {
                    chunkOutcomes.set(i, ChunkSummarizationOutcome.fail(step2.failureReason()));
                }
            }
            return Optional.of(new AdaptiveSessionSummarizationResult(List.of(), chunkOutcomes));
        }

        List<String> sections = parseAdaptiveSections(step2.messageText().orElse(""));
        if (sections.isEmpty()) {
            for (int i = 0; i < total; i++) {
                if (StringUtils.hasText(simplifiedByChunk.get(i))) {
                    chunkOutcomes.set(i, ChunkSummarizationOutcome.fail(SummarizationFailureReason.BATCH_OUTPUT_MISSING_SLOT));
                }
            }
        }
        return Optional.of(new AdaptiveSessionSummarizationResult(sections, chunkOutcomes));
    }

    private boolean sleepPause(String context) {
        try {
            Thread.sleep(pauseBetweenChunkMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted during pause between {}; remaining chunks not summarized", context);
            return false;
        }
    }

    private Step1ChunkOutcome simplifyChunkStep1(TextChunk chunk) {
        String step1User =
                PIPELINE_STEP1_USER_TEMPLATE
                        .replace("{{INDEX}}", String.valueOf(chunk.index()))
                        .replace("{{CHUNK}}", chunk.content());

        AssistantMessageResult step1 =
                completeChat(List.of(
                        new OpenRouterMessage("system", PIPELINE_STEP1_SYSTEM_PROMPT),
                        new OpenRouterMessage("user", step1User)));
        if (step1.failureReason() != SummarizationFailureReason.NONE) {
            return Step1ChunkOutcome.fail(step1.failureReason());
        }
        String simplifiedRaw = step1.messageText().orElse("");
        String simplified = normalizeSummaryWhitespace(stripOptionalMarkdownCodeFence(simplifiedRaw));
        if (!StringUtils.hasText(simplified)) {
            return Step1ChunkOutcome.fail(SummarizationFailureReason.EMPTY_ASSISTANT_MESSAGE);
        }
        return Step1ChunkOutcome.success(simplified);
    }

    private String buildStep2SimplifiedBatchBlock(List<String> simplifiedByChunk) {
        StringBuilder block = new StringBuilder();
        for (int i = 0; i < simplifiedByChunk.size(); i++) {
            String simplified = simplifiedByChunk.get(i);
            if (!StringUtils.hasText(simplified)) {
                continue;
            }
            block.append("### SIMPLIFIED ")
                    .append(i)
                    .append(" ###\n---\n")
                    .append(simplified)
                    .append("\n---\n\n");
        }
        return block.toString();
    }

    private AssistantMessageResult completeChat(List<OpenRouterMessage> messages) {
        OpenRouterChatRequest request =
                new OpenRouterChatRequest(
                        model,
                        messages,
                        false,
                        summarizationTemperature,
                        summarizationTopP,
                        summarizationFrequencyPenalty,
                        summarizationPresencePenalty);
        return postChatCompletionAndExtractMessage(request);
    }

    /**
     * Single completion for auxiliary features (e.g. quizzes). Same OpenRouter client, model, and HTTP retry behavior
     * as summarization; uses the given temperature for this call only.
     */
    public Optional<String> completeAuxiliaryChat(List<OpenRouterMessage> messages, double temperature) {
        OpenRouterChatRequest request =
                new OpenRouterChatRequest(
                        model,
                        messages,
                        false,
                        clamp(temperature, 0.0, 2.0),
                        summarizationTopP,
                        summarizationFrequencyPenalty,
                        summarizationPresencePenalty);
        AssistantMessageResult r = postChatCompletionAndExtractMessage(request);
        if (r.failureReason() != SummarizationFailureReason.NONE) {
            return Optional.empty();
        }
        return r.messageText();
    }

    private static List<ChunkSummarizationOutcome> failAllOutcomes(int n, SummarizationFailureReason reason) {
        List<ChunkSummarizationOutcome> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(ChunkSummarizationOutcome.fail(reason));
        }
        return list;
    }

    private AssistantMessageResult postChatCompletionAndExtractMessage(OpenRouterChatRequest request) {
        String raw = null;
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                raw = openRouterRestClient
                        .post()
                        .uri(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(h -> {
                            h.setBearerAuth(apiKey);
                            if (StringUtils.hasText(httpReferer)) {
                                h.add("HTTP-Referer", httpReferer);
                            }
                            if (StringUtils.hasText(appTitle)) {
                                h.add("X-Title", appTitle);
                            }
                        })
                        .body(request)
                        .retrieve()
                        .body(String.class);
                break;
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                String body = ex.getResponseBodyAsString(StandardCharsets.UTF_8);
                if (status == 429 && attempt < retryMaxAttempts) {
                    long waitMs = retryDelayMillis * attempt;
                    log.info(
                            "OpenRouter 429 (rate limit), waiting {} ms — retry {}/{}",
                            waitMs,
                            attempt + 1,
                            retryMaxAttempts);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return AssistantMessageResult.failure(SummarizationFailureReason.BATCH_INTERRUPTED);
                    }
                    continue;
                }
                log.warn(
                        "OpenRouter HTTP {}: {}",
                        status,
                        truncate(body, 1000));
                if (status == 401 || status == 403) {
                    return AssistantMessageResult.failure(SummarizationFailureReason.API_KEY_REJECTED);
                }
                if (status == 429) {
                    return AssistantMessageResult.failure(SummarizationFailureReason.RATE_LIMITED);
                }
                return AssistantMessageResult.failure(SummarizationFailureReason.OPENROUTER_HTTP_ERROR);
            } catch (RestClientException ex) {
                log.warn("OpenRouter request failed: {}", ex.getMessage());
                return AssistantMessageResult.failure(SummarizationFailureReason.NETWORK_OR_TIMEOUT);
            }
        }

        if (!StringUtils.hasText(raw)) {
            return AssistantMessageResult.failure(SummarizationFailureReason.EMPTY_HTTP_BODY);
        }
        return extractAssistantMessageText(raw);
    }

    private AssistantMessageResult extractAssistantMessageText(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.has("error")) {
                log.warn("OpenRouter error field: {}", root.get("error"));
                return AssistantMessageResult.failure(SummarizationFailureReason.OPENROUTER_ERROR_FIELD);
            }
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                log.warn("OpenRouter response missing choices[0].message.content");
                return AssistantMessageResult.failure(SummarizationFailureReason.MISSING_MESSAGE_CONTENT);
            }
            if (contentNode.isTextual()) {
                String t = contentNode.asText();
                return StringUtils.hasText(t)
                        ? AssistantMessageResult.success(t)
                        : AssistantMessageResult.failure(SummarizationFailureReason.EMPTY_ASSISTANT_MESSAGE);
            }
            if (contentNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : contentNode) {
                    if (part.isTextual()) {
                        sb.append(part.asText());
                    } else if (part.has("text")) {
                        sb.append(part.get("text").asText(""));
                    }
                }
                String t = sb.toString();
                return StringUtils.hasText(t)
                        ? AssistantMessageResult.success(t)
                        : AssistantMessageResult.failure(SummarizationFailureReason.EMPTY_ASSISTANT_MESSAGE);
            }
            log.warn("OpenRouter message.content has unexpected JSON shape");
            return AssistantMessageResult.failure(SummarizationFailureReason.UNEXPECTED_MESSAGE_CONTENT_SHAPE);
        } catch (JsonProcessingException ex) {
            log.warn("OpenRouter response JSON parse error: {}", ex.getOriginalMessage());
            return AssistantMessageResult.failure(SummarizationFailureReason.RESPONSE_JSON_PARSE_ERROR);
        }
    }

    private record AssistantMessageResult(Optional<String> messageText, SummarizationFailureReason failureReason) {
        static AssistantMessageResult success(String text) {
            return new AssistantMessageResult(Optional.of(text), SummarizationFailureReason.NONE);
        }

        static AssistantMessageResult failure(SummarizationFailureReason reason) {
            return new AssistantMessageResult(Optional.empty(), reason);
        }
    }

    private record Step1ChunkOutcome(Optional<String> simplifiedText, SummarizationFailureReason failureReason) {
        static Step1ChunkOutcome success(String text) {
            return new Step1ChunkOutcome(Optional.of(text), SummarizationFailureReason.NONE);
        }

        static Step1ChunkOutcome fail(SummarizationFailureReason reason) {
            return new Step1ChunkOutcome(Optional.empty(), reason);
        }
    }

    public record AdaptiveSessionSummarizationResult(
            List<String> sectionSummaries,
            List<ChunkSummarizationOutcome> chunkOutcomes) {}


    private List<String> parseAdaptiveSections(String assistantMessage) {
        if (!StringUtils.hasText(assistantMessage)) {
            return List.of();
        }
        String s = stripOptionalMarkdownCodeFence(assistantMessage.strip());
        s = s.replace("\r\n", "\n").replace('\r', '\n');

        Matcher m = ADAPTIVE_SECTION_HEADER.matcher(s);
        List<Integer> headerStarts = new ArrayList<>();
        List<Integer> contentStarts = new ArrayList<>();
        while (m.find()) {
            headerStarts.add(m.start());
            contentStarts.add(skipNewlinesAfter(s, m.end()));
        }
        if (headerStarts.isEmpty()) {
            String normalized = normalizeSummaryWhitespace(s);
            return StringUtils.hasText(normalized) ? List.of(normalized) : List.of();
        }

        List<String> sections = new ArrayList<>();
        for (int i = 0; i < headerStarts.size(); i++) {
            int from = contentStarts.get(i);
            int to = i + 1 < headerStarts.size() ? headerStarts.get(i + 1) : s.length();
            String body = normalizeSummaryWhitespace(s.substring(from, to).strip());
            if (StringUtils.hasText(body)) {
                sections.add(body);
            }
        }
        return List.copyOf(sections);
    }

    private static final Pattern ADAPTIVE_SECTION_HEADER =
            Pattern.compile("^### SECTION(?:\\s+\\d+)? ###\\s*$", Pattern.MULTILINE);

    private static int skipNewlinesAfter(String s, int pos) {
        int i = pos;
        while (i < s.length()) {
            if (s.charAt(i) == '\n') {
                i++;
            } else if (s.charAt(i) == '\r') {
                i++;
                if (i < s.length() && s.charAt(i) == '\n') {
                    i++;
                }
            } else {
                break;
            }
        }
        return i;
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

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * Normalizes line breaks and spacing so summaries read cleanly when joined or shown as plain text.
     */
    private static String normalizeSummaryWhitespace(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\r\n", "\n").replace('\r', '\n').strip();
        String[] lines = s.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean lastWasBlank = false;
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                if (!sb.isEmpty() && !lastWasBlank) {
                    sb.append('\n');
                    lastWasBlank = true;
                }
            } else {
                lastWasBlank = false;
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                sb.append(trimmed.replaceAll("[ \t]{2,}", " "));
                sb.append('\n');
            }
        }
        s = sb.toString().replaceAll("\n{3,}", "\n\n").strip();
        return s;
    }

    private static final String PIPELINE_STEP1_SYSTEM_PROMPT =
            """
You are an expert academic simplifier.

Your task is to transform the given text into a clear, accurate, and simplified version that preserves all essential meaning.

========================
CORE OBJECTIVE
========================
- Preserve the original meaning exactly
- Reduce complexity without losing important details
========================
STRICT RULES
========================
- Do NOT add any new information
- Do NOT remove essential concepts
- Do NOT summarize aggressively; retain all key ideas
- Do NOT refer to “the text” or “the author”
========================
SIMPLIFICATION RULES
========================
- Rewrite complex sentences into shorter, clearer ones
- Replace difficult vocabulary with simpler equivalents where possible
- Break long sentences into multiple sentences
- Resolve implicit meaning when possible (make ideas explicit if they are clearly implied)
- Keep terminology if it is important (especially in math, programming, biology, etc.)

========================
STRUCTURE RULES
========================
- Output in clean, readable paragraphs
- Use bullet points ONLY if they improve clarity (not mandatory)
- Keep logical flow between ideas
- Avoid repetition
- When you contrast two or more distinct ideas (e.g. A vs B, discrete vs continuous, two different models), put them on **separate lines** (e.g. two short lines or two bullets) instead of one long "A = … B = …" run-on sentence
- For mathematics, put display math on its **own lines** using `$$` … `$$` (or equivalent). Use `$...$` for short inline math. Preserve formulas and symbols from the source exactly
- Do NOT add "checklists" or self-quiz content for the reader: no "Can you...?", "Do you know...?", "Quick checklist", "Test yourself", or other rhetorical questions. Output is study notes only; the app provides quizzes separately

========================
CONTENT ADAPTATION
========================
Adapt based on the type of content:

- If mathematical:
  - Preserve formulas exactly
  - Use clear `$$` / `$` delimiters for LaTeX-style math as above
  - Clarify what each symbol represents (briefly if needed)
  - Keep logical steps intact

- If programming:
  - Preserve code exactly
  - Add brief explanation in plain language if needed

- If conceptual/theoretical:
  - Focus on clarity of ideas
  - Use simple explanations without losing precision

- If process or sequence:
  - Make steps explicit and ordered

========================
OUTPUT REQUIREMENTS
========================
- Clear, simplified version of the input text
- Faithful to the original content
- Ready to be formatted in a later step
========================
""";

    private static final String PIPELINE_STEP1_USER_TEMPLATE =
            """
Chunk index (0-based): {{INDEX}}

Input text:
---
{{CHUNK}}
---
""";

    private static final String PIPELINE_STEP2_ADAPTIVE_SYSTEM_PROMPT =
            """

You will receive multiple chunks. Your job is to: 
organize them into a coherent study session based on meaning
Merge related chunk ideas and split overloaded topics when needed.
Preserve technical accuracy and do not add new facts.
For each section, start with: ### SECTION k ### (k starts at 1)
act as you are an ADHD Learning Architect.
avoid Long sentences and dense blocks or don't flatten everything into a single paragraph
transform the sections into a clear, accurate, and simplified version that preserves all essential meaning.
When contrasting two ideas (e.g. two distributions, discrete vs continuous), use separate lines or bullet lines so the reader can scan; do not cram them into one long sentence.
For math, put display equations on their own lines using `$$` … `$$` and inline with `$...$` when short.
Do not include "checklists" or reader-directed quiz prompts such as "Can you...?", "Do you know...?", "Quick checklist before moving on", or "Test yourself" — output reference-style notes only.
Use of Images to Support and Break Up Text
""";

    private static final String PIPELINE_STEP2_ADAPTIVE_USER_TEMPLATE =
            """
INPUT (simplified chunks):
{{SIMPLIFIED_BLOCK}}
""";

}
