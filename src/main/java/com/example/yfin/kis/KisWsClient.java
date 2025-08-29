package com.example.yfin.kis;

import com.example.yfin.model.QuoteDto;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class KisWsClient {

    private static final Logger log = LoggerFactory.getLogger(KisWsClient.class);

    private final KisAuthClient auth;
    private final String wsUrl;
    private final ReactorNettyWebSocketClient wsClient = new ReactorNettyWebSocketClient();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String approvalKey = null;

    private final Sinks.Many<String> outbound = Sinks.many().multicast().onBackpressureBuffer();
    private final ConcurrentMap<String, Integer> subCount = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Sinks.Many<QuoteDto>> symbolSinks = new ConcurrentHashMap<>();

    public KisWsClient(KisAuthClient auth, @Value("${api.kis.ws-url:wss://openapi.koreainvestment.com:9443/websocket}") String wsUrl) {
        this.auth = auth;
        this.wsUrl = wsUrl;
    }

    public boolean isEnabled() { return auth != null && auth.isConfigured(); }

    public Flux<QuoteDto> subscribe(String symbol) {
        if (!isEnabled()) return Flux.empty();
        ensureConnection();
        subCount.merge(symbol, 1, Integer::sum);
        // 프로토콜 상세는 문서를 따르며, 여기서는 심볼 전달을 큐에 기록(실제 포맷은 후속 보완)
        outbound.tryEmitNext(symbol);
        Sinks.Many<QuoteDto> sink = symbolSinks.computeIfAbsent(symbol, k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux().doFinally(sig -> {
            subCount.compute(symbol, (k, v) -> {
                int n = (v == null ? 0 : v) - 1;
                if (n <= 0) return null;
                return n;
            });
        });
    }

    private void ensureConnection() {
        if (!running.compareAndSet(false, true)) return;
        connectLoop();
    }

    private void connectLoop() {
        Mono.defer(this::connectOnce)
                .retryWhen(reactor.util.retry.Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30)))
                .subscribe();
    }

    private Mono<Void> connectOnce() {
        return auth.issueWsApprovalKey()
                .doOnNext(k -> this.approvalKey = k)
                .flatMap(k -> wsClient.execute(URI.create(wsUrl), session -> {
                    // 송신: approval_key 및 outbound(심볼 큐)
                    Flux<WebSocketMessage> out = Flux.concat(
                                    Flux.just("approval:" + k).map(session::textMessage),
                                    outbound.asFlux().map(s -> session.textMessage("sub:" + s))
                            );
                    Mono<Void> send = session.send(out).onErrorResume(e -> Mono.empty()).then();

                    // 수신: 미구현(프로토콜 확정 후 심볼별 가격 fan-out)
                    Mono<Void> recv = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(msg -> {})
                            .then();

                    return Mono.when(send, recv)
                            .doFinally(sig -> running.set(false));
                }))
                .doOnError(e -> log.warn("KIS WS connection error: {}", e.toString()));
    }
}


