package com.example.yfin.kis;

import com.example.yfin.model.QuoteDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class KisWsClient {

    private static final Logger log = LoggerFactory.getLogger(KisWsClient.class);

    /**
     * KIS 실시간 웹소켓 클라이언트
     * - 단일 연결을 유지하며 심볼별 구독을 팬아웃
     * - 승인키 발급 → 후보 URL 순차 접속 → 로그인 메시지 전송 → 구독 메시지 전송 → 수신 로그/파싱
     * - 실패 시 지수 백오프로 재시도
     */

    private final KisAuthClient auth;
    private final String wsUrl;
    private final ReactorNettyWebSocketClient wsClient = new ReactorNettyWebSocketClient();

    private final AtomicBoolean running = new AtomicBoolean(false);

    // 구독 요청을 버퍼링하여 재연결 시에도 유실 없이 전송
    private final Sinks.Many<String> outbound = Sinks.many().replay().limit(256);
    private final ConcurrentMap<String, Integer> subCount = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Sinks.Many<QuoteDto>> symbolSinks = new ConcurrentHashMap<>();
    // tr_key(예: 005930) -> 원본 심볼들(예: 005930.KS) 매핑
    private final ConcurrentMap<String, Set<String>> trKeyToSymbols = new ConcurrentHashMap<>();

    public KisWsClient(KisAuthClient auth, @Value("${api.kis.ws-url:ws://ops.koreainvestment.com:21000}") String wsUrl) {
        this.auth = auth;
        this.wsUrl = wsUrl;
    }

    public boolean isEnabled() { return auth != null && auth.isConfigured(); }

    public Flux<QuoteDto> subscribe(String symbol) {
        // 1) 연결 보장: 최초 1회만 연결 루프 시작
        if (!isEnabled()) return Flux.empty();
        ensureConnection();

        // 2) 심볼 구독 ref-count 증가 (필요 시 해지 로직에서 감소)
        subCount.merge(symbol, 1, Integer::sum);

        // 3) 심볼을 outbound 큐에 기록 → 실제 구독 메시지는 연결 후 전송 스트림에서 생성
        outbound.tryEmitNext(symbol);

        // 4) 심볼별 팬아웃 싱크. 현재는 로그/파싱만 구현, 추후 파싱 결과를 이 싱크로 전달
        Sinks.Many<QuoteDto> sink = symbolSinks.computeIfAbsent(symbol, k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux().doFinally(sig -> {
            // 5) 구독 해지 감지 시 ref-count 감소
            subCount.compute(symbol, (k, v) -> {
                int n = (v == null ? 0 : v) - 1;
                if (n <= 0) return null;
                return n;
            });
        });
    }

    private void ensureConnection() {
        // 중복 연결 방지 플래그
        if (!running.compareAndSet(false, true)) return;
        connectLoop();
    }

    private void connectLoop() {
        // 승인키 발급 → 접속 시도. 실패 시 지수 백오프로 무한 재시도
        Mono.defer(this::connectOnce)
                .retryWhen(reactor.util.retry.Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30)))
                .subscribe();
    }

    private Mono<Void> connectOnce() {
        // 1) WS 승인키 발급
        return auth.issueWsApprovalKey()
                // 2) 후보 URL에 순차 접속 시도
                .flatMap(k -> tryConnectWithCandidates(k));
    }

    private Mono<Void> tryConnectWithCandidates(String approval) {
        // 운영/모의/테스트 등 후보 URL 순차 시도
        java.util.List<String> candidates = java.util.List.of(
                wsUrl,
                "wss://openapi.koreainvestment.com:9443/websocket",
                "wss://openapivts.koreainvestment.com:29443/websocket",
                "ws://ops.koreainvestment.com:21000/tryitout/H0STCNT0"
        );
        Mono<Void> chain = Mono.error(new IllegalStateException("no candidates"));
        for (String url : candidates) {
            chain = chain.onErrorResume(ex -> connectTo(url, approval));
        }
        return chain.doOnError(e -> log.warn("KIS WS connection error: {}", e.toString()));
    }

    private Mono<Void> connectTo(String url, String approval) {
        // 1) WS 핸드셰이크 헤더 구성 (KIS 요구 헤더)
        HttpHeaders headers = buildHandshakeHeaders(approval);
        return wsClient.execute(URI.create(url), headers, session -> {
            // 2) 구독 메시지를 바로 전송 (로그인 별도 단계 없이, 권장 포맷으로 1-메시지 구독)
            Flux<WebSocketMessage> out = outbound.asFlux()
                    .delaySubscription(Duration.ofMillis(150))
                    .map(sym -> {
                        String trId = determineTrId(sym);
                        String trKey = determineTrKey(sym);
                        // 구독 라우팅을 위해 tr_key → 원본 심볼 매핑 유지
                        trKeyToSymbols.computeIfAbsent(trKey, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(sym);
                        return session.textMessage(buildSubscribeJson(approval, trId, trKey));
                    });
            // 5) 송수신 파이프라인 구성 (에러 무시하고 재연결 루프로 복귀)
            Mono<Void> send = session.send(out).onErrorResume(e -> Mono.empty()).then();
            Mono<Void> recv = session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(msg -> {
                        String s = msg.length() > 300 ? msg.substring(0, 300) + "..." : msg;
                        // 간단 파서: 0|H0STCNT0|... 또는 0|H0UNCNT0|...
                        if (msg.startsWith("0|H0STCNT0|") || msg.startsWith("0|H0UNCNT0|")) {
                            String[] parts = msg.split("\\|", 4);
                            if (parts.length >= 4) {
                                String payload = parts[3];
                                String[] f = payload.split("\\^");
                                if (f.length >= 6) {
                                    String trKey = f[0];
                                    String priceStr = f[2];
                                    String changeStr = f[4];
                                    String ratePctStr = f[5];
                                    try {
                                        double price = Double.parseDouble(priceStr);
                                        Double change = null;
                                        try { change = Double.parseDouble(changeStr); } catch (Exception ignore2) {}
                                        Double rate = null;
                                        try { rate = Double.parseDouble(ratePctStr) / 100.0; } catch (Exception ignore3) {}
                                        Set<String> originals = trKeyToSymbols.getOrDefault(trKey, java.util.Collections.emptySet());
                                        for (String original : originals) {
                                            Sinks.Many<QuoteDto> sink = symbolSinks.get(original);
                                            if (sink != null) {
                                                QuoteDto dto = new QuoteDto();
                                                dto.setSymbol(original);
                                                dto.setRegularMarketPrice(price);
                                                if (change != null) dto.setRegularMarketChange(change);
                                                if (rate != null) dto.setRegularMarketChangePercent(rate);
                                                sink.tryEmitNext(dto);
                                            }
                                        }
                                    } catch (NumberFormatException ignore) { /* skip malformed */ }
                                }
                            }
                        }
                    })
                    .then();
            return Mono.when(send, recv)
                    .doFinally(sig -> running.set(false));
        });
    }

    /**
     * 간단 심볼 → TR 매핑
     * - 숫자만: 국내 주식 체결가(H0STCNT0)
     * - 추후 해외/호가 TR 필요 시 확장
     */
    private String determineTrId(String symbol) {
        // 접미사 기준으로 국내/해외 판별: KS/KQ → 국내 체결, 그 외 → 해외 체결
        if (symbol != null) {
            int dot = symbol.indexOf('.');
            String suffix = dot > 0 && dot + 1 < symbol.length() ? symbol.substring(dot + 1).toUpperCase() : "";
            if ("KS".equals(suffix) || "KQ".equals(suffix)) return "H0STCNT0";
            // 접미사 없으나 완전 숫자(구형 형태)면 국내로 간주
            String left = dot > 0 ? symbol.substring(0, dot) : symbol;
            if (left.matches("\\d+")) return "H0STCNT0";
        }
        return "H0UNCNT0";
    }

    /**
     * TR 별 tr_key 정규화
     * - 국내: 숫자 6자리만 허용 (예: 005930.KS → 005930)
     * - 해외/기타: 우선 원문 유지 (추후 규칙 확장)
     */
    private String determineTrKey(String symbol) {
        if (symbol == null) return "";
        int dot = symbol.indexOf('.');
        String left = dot > 0 ? symbol.substring(0, dot) : symbol;
        String suffix = dot > 0 && dot + 1 < symbol.length() ? symbol.substring(dot + 1).toUpperCase() : "";

        // 국내: 접미사 KS/KQ 또는 숫자-only로 판단 → 숫자만 추출하여 전달
        if ("KS".equals(suffix) || "KQ".equals(suffix) || left.matches("\\d+")) {
            String digits = left.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) return digits.length() >= 6 ? digits.substring(0, 6) : digits;
            // 숫자를 전혀 추출 못하면 최후에는 원문 left 사용
            return left.toUpperCase();
        }
        // 해외: 접미사 제거 후 심볼만(대문자) 사용
        return left.toUpperCase();
    }

    // === Helper builders ===

    /**
     * WS 핸드셰이크에 필요한 헤더 구성
     */
    private HttpHeaders buildHandshakeHeaders(String approval) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Origin", "https://openapi.koreainvestment.com");
        headers.add("Sec-WebSocket-Protocol", "json");
        headers.add("appkey", auth.appKey());
        headers.add("approval_key", approval);
        headers.add("custtype", "P");
        headers.add("tr_type", "1");
        headers.add("content-type", "utf-8");
        return headers;
    }

    /**
     * 로그인 JSON 생성
     */
    private String buildLoginJson(String approval) {
        return "{" +
                "\"header\":{\"appkey\":\"" + auth.appKey() + "\",\"approval_key\":\"" + approval + "\",\"custtype\":\"P\",\"tr_type\":\"1\",\"content-type\":\"application/json; charset=utf-8\"}," +
                "\"body\":{\"input\":{}}}";
    }

    /**
     * 구독 JSON 생성 (header/body.input 모두 동일 tr_id 필요)
     */
    private String buildSubscribeJson(String approval, String trId, String symbol) {
        return "{" +
                "\"header\":{\"appkey\":\"" + auth.appKey() + "\",\"approval_key\":\"" + approval + "\",\"custtype\":\"P\",\"tr_type\":\"1\",\"content-type\":\"application/json; charset=utf-8\",\"tr_id\":\"" + trId + "\"}," +
                "\"body\":{\"input\":{\"tr_id\":\"" + trId + "\",\"tr_key\":\"" + symbol + "\"}}}";
    }
}


