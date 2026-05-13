package com.adhdpdf.study.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenRouterChatRequest(
        String model,
        List<OpenRouterMessage> messages,
        @JsonProperty("stream") boolean stream,
        Double temperature,
        @JsonProperty("top_p") Double topP,
        @JsonProperty("frequency_penalty") Double frequencyPenalty,
        @JsonProperty("presence_penalty") Double presencePenalty
) {}
