package com.example.yfin;

import com.example.yfin.model.QuoteDto;
import com.example.yfin.service.QuoteService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
public class StreamController {

    private final QuoteService quoteService;

    public StreamController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping(value = "/stream/quotes", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<QuoteDto>> streamQuotes(@RequestParam String tickers,
                                                        @RequestParam(required = false) String exchange,
                                                        @RequestParam(name = "intervalSec", defaultValue = "5") long intervalSec) {
        if (intervalSec < 2) intervalSec = 2; // 최소 간격 가드
        List<String> list = Arrays.stream(tickers.split(","))
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isBlank())
                .toList();

        Flux<List<QuoteDto>> poll = Flux.interval(Duration.ZERO, Duration.ofSeconds(intervalSec))
                .flatMap(i -> exchange == null ? quoteService.quotes(list) : quoteService.quotesEx(list, exchange))
                .onErrorResume(e -> Flux.empty());

        Flux<ServerSentEvent<QuoteDto>> heartbeat = Flux.interval(Duration.ofSeconds(10))
                .map(i -> ServerSentEvent.<QuoteDto>builder()
                        .event("heartbeat")
                        .comment("ping")
                        .build());

        Flux<ServerSentEvent<QuoteDto>> data = poll.flatMap(quotes -> Flux.fromIterable(quotes))
                .map(q -> ServerSentEvent.<QuoteDto>builder(q)
                        .id(q.getSymbol() + ":" + Instant.now().toEpochMilli())
                        .event("quote")
                        .build());

        return Flux.merge(heartbeat, data);
    }
}


