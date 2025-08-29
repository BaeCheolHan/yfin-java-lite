package com.example.yfin.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class AlphaVantageClient {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageClient.class);

    private final WebClient http;
    private final String apiKey;

    public AlphaVantageClient(@Qualifier("alphaVantageHttp") WebClient http,
                              @Value("${alphaVantage.apiKey:}") String apiKey) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean isEnabled() {
        return !this.apiKey.isBlank();
    }

    /** GLOBAL_QUOTE for a single symbol */
    public Mono<Map<String, Object>> getQuote(String symbol) {
        if (!isEnabled()) return Mono.empty();
        return http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "GLOBAL_QUOTE")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnError(e -> log.warn("AlphaVantage GLOBAL_QUOTE failed for {}: {}", symbol, e.toString()));
    }
}


