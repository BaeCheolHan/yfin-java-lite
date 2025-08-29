package com.example.yfin.realtime;

import com.example.yfin.model.QuoteDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finnhub WebSocket 브리지: 구독/해제와 자동 재연결, 심볼별 스트림 제공.
 * 참고: https://finnhub.io/docs/api/websocket-trades
 */
@Component
public class FinnhubWsClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubWsClient.class);

    private final String apiKey;
    private final ObjectMapper om = new ObjectMapper();
    private final ReactorNettyWebSocketClient wsClient = new ReactorNettyWebSocketClient();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean connected = false;

    private final Sinks.Many<String> outbound = Sinks.many().multicast().onBackpressureBuffer();
    private final ConcurrentMap<String, Integer> subCount = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Sinks.Many<QuoteDto>> symbolSinks = new ConcurrentHashMap<>();

    public FinnhubWsClient(@Value("${finnhub.apiKey:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    public Flux<QuoteDto> subscribe(String symbol) {
        if (!isEnabled()) return Flux.empty();
        ensureConnection();
        int newCount = subCount.merge(symbol, 1, Integer::sum);
        if (newCount == 1) {
            outbound.tryEmitNext("{\"type\":\"subscribe\",\"symbol\":\"" + symbol + "\"}");
        }
        Sinks.Many<QuoteDto> sink = symbolSinks.computeIfAbsent(symbol, k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux().doFinally(sig -> {
            // 구독자 해제 시 카운트 감소, 0되면 unsubscribe 전송
            subCount.compute(symbol, (k, v) -> {
                int n = (v == null ? 0 : v) - 1;
                if (n <= 0) {
                    outbound.tryEmitNext("{\"type\":\"unsubscribe\",\"symbol\":\"" + symbol + "\"}");
                    return null;
                }
                return n;
            });
        });
    }

    private void ensureConnection() {
        if (!running.compareAndSet(false, true)) return;
        connectLoop();
    }

    private void connectLoop() {
        Mono.defer(() -> connectOnce())
                .retryWhen(reactor.util.retry.Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30)))
                .subscribe();
    }

    private Mono<Void> connectOnce() {
        String url = "wss://ws.finnhub.io?token=" + apiKey;
        return wsClient.execute(URI.create(url), session -> {
            connected = true;
            // 송신: outbound sink + ping 을 단일 send 스트림으로 병합
            Flux<org.springframework.web.reactive.socket.WebSocketMessage> out = Flux.merge(
                    outbound.asFlux().map(session::textMessage),
                    Flux.interval(Duration.ofSeconds(15)).map(i -> session.textMessage("{\"type\":\"ping\"}"))
            );
            Mono<Void> send = session.send(out)
                    .doOnError(e -> log.warn("Finnhub WS send error: {}", e.toString()))
                    .then();

            // 수신: trade 메시지 파싱 → 심볼 싱크로 fan-out
            Mono<Void> receive = session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(this::handleIncoming)
                    .doOnError(e -> log.warn("Finnhub WS recv error: {}", e.toString()))
                    .then();

            // 연결 완료 후 현재 구독 목록 재전송 (재연결 대응)
            Mono<Void> resub = Mono.fromRunnable(() -> {
                for (String sym : subCount.keySet()) {
                    outbound.tryEmitNext("{\"type\":\"subscribe\",\"symbol\":\"" + sym + "\"}");
                }
            });

            return Mono.when(send, receive, resub)
                    .doFinally(s -> connected = false);
        }).doOnError(e -> log.warn("Finnhub WS connection error: {}", e.toString()));
    }

    @SuppressWarnings("unchecked")
    private void handleIncoming(String json) {
        try {
            Map<String, Object> m = om.readValue(json, new TypeReference<Map<String, Object>>() {});
            Object type = m.get("type");
            if (type == null) return;
            String t = String.valueOf(type);
            if ("trade".equalsIgnoreCase(t)) {
                Object dataObj = m.get("data");
                if (!(dataObj instanceof List<?> list)) return;
                for (Object it : list) {
                    if (!(it instanceof Map<?, ?> raw)) continue;
                    String sym = String.valueOf(raw.get("s"));
                    Double price = toDouble(raw.get("p"));
                    if (sym == null || price == null) continue;
                    Sinks.Many<QuoteDto> sink = symbolSinks.get(sym);
                    if (sink == null) continue;
                    QuoteDto q = new QuoteDto();
                    q.setSymbol(sym);
                    q.setRegularMarketPrice(price);
                    sink.tryEmitNext(q);
                }
            }
        } catch (Exception ignore) {
        }
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
    }
}


