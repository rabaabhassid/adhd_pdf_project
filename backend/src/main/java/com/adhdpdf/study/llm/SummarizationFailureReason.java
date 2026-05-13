package com.adhdpdf.study.llm;

/**
 * Why a chunk did not receive a usable LLM summary. {@link #NONE} means success;
 * {@link #NOT_SUMMARIZED_OVER_CAP} applies to chunks skipped by cost/limit settings (not an API failure).
 */
public enum SummarizationFailureReason {
    NONE,
    NOT_SUMMARIZED_OVER_CAP,

    MISSING_API_KEY,
    MODEL_NOT_CONFIGURED,

    RATE_LIMITED,
    API_KEY_REJECTED,
    OPENROUTER_HTTP_ERROR,
    OPENROUTER_ERROR_FIELD,

    EMPTY_HTTP_BODY,
    MISSING_MESSAGE_CONTENT,
    UNEXPECTED_MESSAGE_CONTENT_SHAPE,
    EMPTY_ASSISTANT_MESSAGE,
    RESPONSE_JSON_PARSE_ERROR,

    NETWORK_OR_TIMEOUT,

    BATCH_INTERRUPTED,
    BATCH_OUTPUT_MISSING_SLOT,

    UNKNOWN
}
