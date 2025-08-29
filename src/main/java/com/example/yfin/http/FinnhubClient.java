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
public class FinnhubClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubClient.class);

    private final WebClient http;
    private final String apiKey;

    public FinnhubClient(@Qualifier("finnhubHttp") WebClient http,
                         @Value("${finnhub.apiKey:}") String apiKey) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean isEnabled() { return !apiKey.isBlank(); }

    /** Quote endpoint: https://finnhub.io/api/v1/quote?symbol=AAPL&token=... */
    public Mono<Map<String, Object>> getQuote(String symbol) {
        if (!isEnabled()) return Mono.empty();
        return http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/quote")
                        .queryParam("symbol", symbol)
                        .queryParam("token", apiKey)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnError(e -> log.warn("Finnhub quote failed for {}: {}", symbol, e.toString()));
    }
}


