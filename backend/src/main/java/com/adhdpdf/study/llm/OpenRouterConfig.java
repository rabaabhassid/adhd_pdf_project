package com.adhdpdf.study.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenRouterConfig {

    @Bean(name = "openRouterRestClient")
    RestClient openRouterRestClient(
            @Value("${openrouter.connect-timeout-millis:30000}") int connectTimeoutMs,
            @Value("${openrouter.read-timeout-millis:180000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder()
                .baseUrl("https://openrouter.ai")
                .requestFactory(factory)
                .build();
    }
}
