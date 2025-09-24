package com.example.yfin.kis;

import com.example.yfin.kis.dto.KisWebSocketRequest;
import com.example.yfin.model.QuoteDto;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Scope;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.yfin.util.SymbolUtils;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Scope("singleton")
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
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private volatile WebSocketSession currentSession = null;
    private volatile String currentApprovalKey = null;
    private final AtomicInteger appkeyConflictRetryCount = new AtomicInteger(0);

    // 구독 요청을 버퍼링하여 재연결 시에도 유실 없이 전송
    private volatile Sinks.Many<String> outbound = Sinks.many().replay().limit(256);
    private final ConcurrentMap<String, Integer> subscriptionRefCount = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Sinks.Many<QuoteDto>> symbolSinks = new ConcurrentHashMap<>();
    // tr_key(예: 005930) -> 원본 심볼들(예: 005930.KS) 매핑
    private final ConcurrentMap<String, Set<String>> transactionKeyToOriginalSymbols = new ConcurrentHashMap<>();

    // KIS WebSocket 연결 상태 관리
    private final AtomicBoolean needsReconnection = new AtomicBoolean(false);
    private final AtomicInteger maxSymbolsPerSession = new AtomicInteger(41); // KIS 제한

    private final boolean wsEnabled;

    public KisWsClient(KisAuthClient auth, 
                       @Value("${api.kis.ws-url:ws://ops.koreainvestment.com:21000}") String wsUrl,
                       @Value("${api.kis.ws-enabled:true}") boolean wsEnabled) {
        this.auth = auth;
        this.wsUrl = wsUrl;
        this.wsEnabled = wsEnabled;
        this.objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        log.info("KisWsClient initialized with wsUrl: {}", wsUrl);
        log.info("KisWsClient wsEnabled: {}", wsEnabled);
    }

    public boolean isEnabled() { return wsEnabled && auth != null && auth.isConfigured(); }

    public Flux<QuoteDto> subscribe(String symbol) {
        if (!isEnabled()) {
            return Flux.empty();
        }
        ensureConnection();

        // 심볼 구독 ref-count 증가
        int newCount = subscriptionRefCount.merge(symbol, 1, Integer::sum);

        // 새로운 종목 추가 시에만 KIS 서버에 구독 요청
        if (newCount == 1) {
            if (subscriptionRefCount.size() > maxSymbolsPerSession.get()) {
                log.warn("KIS symbol limit exceeded ({}), forcing reconnection", subscriptionRefCount.size());
                needsReconnection.set(true);
                disconnect();
            } else {
                // 첫 번째 구독자일 때만 KIS 서버에 구독 요청
                outbound.tryEmitNext(symbol);
            }
        }

        // 심볼별 팬아웃 싱크
        Sinks.Many<QuoteDto> sink = symbolSinks.computeIfAbsent(symbol, k -> {
            Sinks.Many<QuoteDto> newSink = Sinks.many().multicast().onBackpressureBuffer();
            log.info("Created new sink for symbol: {} (ref-count: {})", symbol, newCount);
            return newSink;
        });
        
        log.info("Client subscribing to symbol: {} (ref-count: {})", symbol, newCount);
        
        return sink.asFlux()
                .doOnSubscribe(s -> log.info("Client subscribed to symbol: {} (ref-count: {})", symbol, newCount))
                .doFinally(sig -> {
                    log.info("Client unsubscribing from symbol: {} (signal: {})", symbol, sig);
                    
                    // 구독 해지 감지 시 ref-count 감소
                    Integer remainingCount = subscriptionRefCount.compute(symbol, (k, v) -> {
                        int n = (v == null ? 0 : v) - 1;
                        if (n <= 0) return null;
                        return n;
                    });

                    // 마지막 구독자일 때 KIS 서버에 구독 해제 요청
                    if (remainingCount == null) {
                        log.info("Last client unsubscribed from symbol: {}, sending unsubscribe request", symbol);
                        unsubscribe(symbol);
                    } else {
                        log.info("Symbol {} still has {} subscribers", symbol, remainingCount);
                    }
                });
    }
    
    /**
     * 종목 구독 해제
     */
    public void unsubscribe(String symbol) {
        // ref-count 감소
        Integer newCount = subscriptionRefCount.merge(symbol, -1, (old, delta) -> {
            int result = old + delta;
            return result <= 0 ? null : result;
        });
        
        if (newCount == null) {
            // 싱크 정리
            Sinks.Many<QuoteDto> sink = symbolSinks.remove(symbol);
            if (sink != null) {
                sink.tryEmitComplete();
            }
            // tr_key 매핑 정리
            transactionKeyToOriginalSymbols.values().forEach(set -> set.remove(symbol));
            
            // 종목이 모두 해제되면 연결도 해제
            if (subscriptionRefCount.isEmpty()) {
                disconnect();
            }
        }
    }

    private void ensureConnection() {
        // 연결 중이거나 이미 연결된 경우 중복 방지
        if (connecting.get() || running.get()) {
            return;
        }
        
        // 연결 시작 플래그 설정
        if (!connecting.compareAndSet(false, true)) {
            return;
        }
        
        connectLoop();
    }
    
    /**
     * 애플리케이션 종료 시 Graceful Shutdown
     */
    @PreDestroy
    public void gracefulShutdown() {
        gracefulDisconnect();
    }
    
    /**
     * Graceful Disconnect - 해제 요청 전송 후 연결 종료
     */
    public void gracefulDisconnect() {
        log.info("=== KIS WebSocket Graceful Disconnect Started ===");
        running.set(false);
        
        // 서버에 해제 요청 전송
        if (currentSession != null && !subscriptionRefCount.isEmpty()) {
            try {
                log.info("Sending unsubscribe requests for {} symbols", subscriptionRefCount.size());
                sendUnsubscribeRequests();
                
                // 해제 요청 전송 후 충분한 대기 시간 (10초)
                Mono.delay(Duration.ofMillis(10000))
                    .doOnSuccess(v -> {
                        log.info("Unsubscribe requests completed, closing session");
                        if (currentSession != null) {
                            try {
                                currentSession.close();
                            } catch (Exception e) {
                                log.warn("Error closing KIS WebSocket session: {}", e.getMessage());
                            }
                            currentSession = null;
                            currentApprovalKey = null;
                        }
                    })
                    .doOnError(e -> log.warn("Graceful shutdown delay error: {}", e.getMessage()))
                    .subscribe();
            } catch (Exception e) {
                log.warn("Error sending graceful unsubscribe requests: {}", e.getMessage());
                if (currentSession != null) {
                    try {
                        currentSession.close();
                    } catch (Exception ex) {
                        log.warn("Error closing session after graceful unsubscribe error: {}", ex.getMessage());
                    }
                    currentSession = null;
                    currentApprovalKey = null;
                }
            }
        } else {
            log.info("No active session or subscriptions to unsubscribe");
            currentSession = null;
            currentApprovalKey = null;
        }
        
        // 연결 정리
        symbolSinks.clear();
        subscriptionRefCount.clear();
        outbound.tryEmitComplete();
        outbound = Sinks.many().replay().limit(256);
        
        log.info("=== KIS WebSocket Graceful Disconnect Completed ===");
    }

    public void disconnect() {
        running.set(false);
        
        // 서버에 해제 요청 전송
        if (currentSession != null) {
            try {
                sendUnsubscribeRequests();
                
                // 해제 요청 전송 후 대기 후 세션 종료
                Mono.delay(Duration.ofMillis(1000))
                    .doOnSuccess(v -> {
                        if (currentSession != null) {
                            try {
                                currentSession.close();
                            } catch (Exception e) {
                                log.warn("Error closing KIS WebSocket session: {}", e.getMessage());
                            }
                            currentSession = null;
                            currentApprovalKey = null;
                        }
                    })
                    .doOnError(e -> log.warn("Unsubscribe delay error: {}", e.getMessage()))
                    .subscribe();
            } catch (Exception e) {
                log.warn("Error sending unsubscribe requests: {}", e.getMessage());
                if (currentSession != null) {
                    try {
                        currentSession.close();
                    } catch (Exception ex) {
                        log.warn("Error closing session after unsubscribe error: {}", ex.getMessage());
                    }
                    currentSession = null;
                    currentApprovalKey = null;
                }
            }
        } else {
            currentSession = null;
            currentApprovalKey = null;
        }
        
        // 연결 정리
        symbolSinks.clear();
        outbound.tryEmitComplete();
        outbound = Sinks.many().replay().limit(256);
        
        // 기존 구독 요청들을 다시 큐에 추가
        for (String symbol : subscriptionRefCount.keySet()) {
            outbound.tryEmitNext(symbol);
        }
    }

    private void sendUnsubscribeRequests() {
        if (currentSession == null) {
            log.warn("No active session for unsubscribe requests");
            return;
        }
        
        // 현재 연결에 사용된 승인키 사용 (Non-blocking)
        String approvalKey = currentApprovalKey;
        if (approvalKey == null) {
            log.warn("No current approval key available for unsubscribe requests");
            return;
        }
        
        log.info("Preparing unsubscribe requests for {} symbols", subscriptionRefCount.size());
        
        // 해제 요청을 순차적으로 전송하여 확실하게 처리
        List<String> symbols = new ArrayList<>(subscriptionRefCount.keySet());
        
        Flux.fromIterable(symbols)
            .flatMap(symbol -> {
                try {
                    KisTrId trEnum = SymbolUtils.determineTransactionId(symbol);
                    String trId = trEnum.name();
                    String trKey = SymbolUtils.determineTransactionKey(symbol);
                    
                    // 해제 요청 생성 (tr_type = 2)
                    KisWebSocketRequest unsubscribeRequest = new KisWebSocketRequest(approvalKey, trId, trKey, true);
                    String unsubscribeJson = objectMapper.writeValueAsString(unsubscribeRequest);
                    
                    log.info("Sending unsubscribe request for symbol: {} (tr_id: {}, tr_key: {})", symbol, trId, trKey);
                    log.info("Unsubscribe JSON: {}", unsubscribeJson);
                    
                    // 해제 요청 전송
                    return currentSession.send(Mono.just(currentSession.textMessage(unsubscribeJson)))
                            .doOnSuccess(v -> log.info("Unsubscribe request sent successfully for symbol: {}", symbol))
                            .doOnError(e -> log.error("Error sending unsubscribe request for symbol {}: {}", symbol, e.getMessage()))
                            .then(Mono.delay(Duration.ofMillis(100))); // 각 요청 간 100ms 대기
                    
                } catch (Exception e) {
                    log.error("Error preparing unsubscribe request for symbol {}: {}", symbol, e.getMessage());
                    return Mono.empty();
                }
            })
            .then()
            .doOnSuccess(v -> log.info("All unsubscribe requests completed"))
            .doOnError(e -> log.error("Error in unsubscribe process: {}", e.getMessage()))
            .subscribe(); // Non-blocking 구독
    }

    private void connectLoop() {
        // 승인키 발급 → 접속 시도. 실패 시 지수 백오프로 무한 재시도
        Mono.defer(this::connectOnce)
                .retryWhen(reactor.util.retry.Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30)))
                .subscribe(
                    v -> {
                        log.info("KIS WebSocket connection established");
                        connecting.set(false); // 연결 완료 시 플래그 해제
                    },
                    e -> {
                        log.error("KIS WebSocket connection failed: {}", e.getMessage());
                        connecting.set(false); // 연결 실패 시 플래그 해제
                        running.set(false); // 실행 상태도 해제
                    }
                );
    }

    private Mono<Void> connectOnce() {
        // 1) WS 승인키 발급
        return auth.issueWsApprovalKey()
                // 2) 후보 URL에 순차 접속 시도
                .flatMap(k -> tryConnectWithCandidates(k));
    }

    private Mono<Void> tryConnectWithCandidates(String approval) {
        java.util.List<String> candidates = java.util.List.of(
                wsUrl,  // 설정된 기본 URL
                "ws://ops.koreainvestment.com:21000/tryitout/H0STCNT0",  // 국내 주식 체결가
                "ws://ops.koreainvestment.com:21000/tryitout/H0GSCNI0",  // 국내 주식 시세
                "ws://ops.koreainvestment.com:21000/tryitout/HDFSCNT0",  // 해외 주식 체결가
                "ws://ops.koreainvestment.com:21000/tryitout/HDFSASP0"   // 해외 주식 시세
        );
        Mono<Void> chain = Mono.error(new IllegalStateException("no candidates"));
        for (String url : candidates) {
            chain = chain.onErrorResume(ex -> {
                log.info("Trying KIS WebSocket connection to: {}", url);
                return connectTo(url, approval)
                        .doOnError(error -> {
                            log.warn("웹소켓 도메인 연결 실패: {} - {}", url, error.getMessage());
                        });
            });
        }
        return chain.doOnError(e -> log.warn("All KIS WebSocket connection attempts failed: {}", e.toString()));
    }

    private Mono<Void> connectTo(String url, String approval) {
        // 도메인별 연결 로그 및 헤더 설정
        HttpHeaders headers;
        if (url.contains("openapi.koreainvestment.com:9443")) {
            log.info("Attempting KIS WebSocket connection to 실전 Domain: {}", url);
            headers = buildHandshakeHeadersForProduction(approval);
        } else if (url.contains("openapivts.koreainvestment.com:29443")) {
            log.info("Attempting KIS WebSocket connection to 모의 Domain: {}", url);
            headers = buildHandshakeHeaders(approval);
        } else {
            log.info("Attempting KIS WebSocket connection to 테스트 Domain: {}", url);
            headers = buildHandshakeHeaders(approval);
        }
        
        // 핸드셰이크 헤더 상세 로그
        log.info("=== KIS WebSocket Handshake Headers for {} ===", url);
        headers.forEach((key, values) -> {
            if (key.equals("approval_key")) {
                log.info("{}: {}", key, values.get(0));
            } else {
                log.info("{}: {}", key, values);
            }
        });
        log.info("=== End Handshake Headers ===");
        
        return wsClient.execute(URI.create(url), headers, session -> {
                log.info("KIS WebSocket connected successfully to: {}", url);
                currentSession = session;
                currentApprovalKey = approval; // 현재 승인키 저장
                running.set(true); // 연결 성공 시 실행 상태 설정
                connecting.set(false); // 연결 완료 시 플래그 해제
                appkeyConflictRetryCount.set(0); // 성공 시 재시도 카운터 리셋
                
                // 연결 성공 후 기존 구독 요청들을 다시 큐에 추가
                for (String symbol : subscriptionRefCount.keySet()) {
                    outbound.tryEmitNext(symbol);
                }
                
                // 재연결 필요 플래그 리셋
                needsReconnection.set(false);
            
            // 2) 로그인 단계 생략하고 바로 구독 메시지 전송
            // KIS WebSocket은 로그인 없이 바로 구독 가능할 수 있음
            Flux<WebSocketMessage> out = outbound.asFlux()
                    .delaySubscription(Duration.ofMillis(1000))  // 연결 후 1초 대기
                    .map(sym -> {
                        KisTrId trEnum = SymbolUtils.determineTransactionId(sym);
                        String trId = trEnum.name();
                        String trKey = SymbolUtils.determineTransactionKey(sym);
                        // 구독 라우팅을 위해 tr_key → 원본 심볼 매핑 유지
                        transactionKeyToOriginalSymbols.computeIfAbsent(trKey, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(sym);
                        
                        // KIS WebSocket 구독 메시지 생성
                        KisWebSocketRequest request = new KisWebSocketRequest(approval, trId, trKey);
                        
                        String jsonMessage;
                        try {
                            jsonMessage = objectMapper.writeValueAsString(request);
                        } catch (Exception e) {
                            log.error("Failed to serialize KIS WebSocket request: {}", e.getMessage());
                            jsonMessage = String.format("{\"header\":{\"approval_key\":\"%s\",\"custtype\":\"P\",\"tr_type\":\"1\",\"content-type\":\"utf-8\"},\"body\":{\"input\":{\"tr_id\":\"%s\",\"tr_key\":\"%s\"}}}", 
                                    approval, trId, trKey);
                        }
                        
                        return session.textMessage(jsonMessage);
                    });
            
            // 4) 송수신 파이프라인 구성
            Mono<Void> send = session.send(out)
                    .doOnError(e -> log.error("KIS WebSocket send error: {}", e.getMessage()))
                    .onErrorResume(e -> Mono.empty()).then();
                    
            Mono<Void> recv = session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(msg -> {
                        
                        // KIS 에러 응답 처리
                        if (msg.contains("rt_cd") && msg.contains("msg_cd")) {
                            if (msg.contains("ALREADY IN USE appkey")) {
                                int retryCount = appkeyConflictRetryCount.incrementAndGet();
                                log.warn("KIS appkey already in use - forcing complete disconnection (retry count: {})", retryCount);
                                
                                // 최대 3회까지만 재시도
                                if (retryCount > 3) {
                                    log.error("KIS appkey conflict retry limit exceeded ({}), stopping retry attempts", retryCount);
                                    return;
                                }
                                
                                // 강제 연결 해제 및 더 긴 대기
                                disconnect();
                                running.set(false);
                                connecting.set(false);
                                currentSession = null;
                                currentApprovalKey = null;
                                
                                // 서버 측 정리를 위한 더 긴 대기 시간 (60초)
                                Mono.delay(Duration.ofSeconds(60))
                                    .subscribe(v -> {
                                        // 새로운 승인키 발급 후 연결 시도
                                        auth.issueWsApprovalKey()
                                            .subscribe(key -> ensureConnection());
                                    });
                            } else if (msg.contains("OPSP0000") || msg.contains("rt_cd\":\"0\"")) {
                                log.info("KIS unsubscribe request successful: {}", msg);
                            } else {
                                log.warn("KIS error response: {}", msg);
                            }
                            return;
                        }
                        
                        // 파이프 형식 응답 처리: 0|TR_ID|...|payload
                        if (msg.startsWith("0|H0STCNT0|") || msg.startsWith("0|H0GSCNI0|") || 
                            msg.startsWith("0|HDFSCNT0|") || msg.startsWith("0|HDFSASP0|")) {
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
                                        
                                        Set<String> originals = transactionKeyToOriginalSymbols.getOrDefault(trKey, java.util.Collections.emptySet());
                                        log.debug("KIS data received for trKey: {}, originals: {}", trKey, originals);
                                        for (String original : originals) {
                                            Sinks.Many<QuoteDto> sink = symbolSinks.get(original);
                                            if (sink != null) {
                                                QuoteDto dto = new QuoteDto();
                                                dto.setSymbol(original);
                                                dto.setRegularMarketPrice(price);
                                                if (change != null) dto.setRegularMarketChange(change);
                                                if (rate != null) dto.setRegularMarketChangePercent(rate);
                                                
                                                log.debug("Emitting data to sink for symbol: {} (price: {})", original, price);
                                                sink.tryEmitNext(dto);
                                            } else {
                                                log.warn("No sink found for symbol: {}", original);
                                            }
                                        }
                                    } catch (NumberFormatException e) { 
                                        log.warn("KIS number parsing error: {}", e.getMessage());
                                    }
                                }
                            }
                        }
                    })
                    .doOnError(e -> log.error("KIS WebSocket receive error: {}", e.getMessage()))
                    .then();
                    
            return Mono.when(send, recv)
                    .doFinally(sig -> {
                        running.set(false);
                        connecting.set(false);
                        currentSession = null;
                        currentApprovalKey = null;
                    });
        })
        .doOnError(error -> {
            log.error("=== KIS WebSocket Handshake Error for {} ===", url);
            log.error("Error Type: {}", error.getClass().getSimpleName());
            log.error("Error Message: {}", error.getMessage());
            if (error.getCause() != null) {
                log.error("Cause: {}", error.getCause().getMessage());
            }
            log.error("=== End Handshake Error ===");
        });
    }



    /**
     * 간단 심볼 → TR 매핑
     * - 숫자만: 국내 주식 체결가(H0STCNT0)
     * - 추후 해외/호가 TR 필요 시 확장
     */
    // moved to SymbolUtils

    /**
     * TR 별 tr_key 정규화
     * - 국내: 숫자 6자리만 허용 (예: 005930.KS → 005930)
     * - 해외/기타: 우선 원문 유지 (추후 규칙 확장)
     */
    // moved to SymbolUtils

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
    
    private HttpHeaders buildHandshakeHeadersForProduction(String approval) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Origin", "https://openapi.koreainvestment.com");
        headers.add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
        // 실전 도메인에서도 인증 헤더가 필요함
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
        try {
            WsHeader header = new WsHeader();
            header.appkey = auth.appKey();
            header.approval_key = approval;
            header.custtype = "P";
            header.tr_type = "1";
            header.content_type = "application/json; charset=utf-8";

            WsEnvelope envelope = new WsEnvelope();
            envelope.header = header;
            envelope.body = new WsBody();
            envelope.body.input = new java.util.LinkedHashMap<>();

            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build login json", e);
        }
    }

    /**
     * 구독 JSON 생성 (header/body.input 모두 동일 tr_id 필요)
     */
    private String buildSubscribeJson(String approval, String trId, String symbol) {
        try {
            WsHeader header = new WsHeader();
            header.appkey = auth.appKey();
            header.approval_key = approval;
            header.custtype = "P";
            header.tr_type = "1";
            header.content_type = "application/json; charset=utf-8";
            header.tr_id = trId;

            WsEnvelope envelope = new WsEnvelope();
            envelope.header = header;
            envelope.body = new WsBody();
            java.util.Map<String, Object> input = new java.util.LinkedHashMap<>();
            input.put("tr_id", trId);
            input.put("tr_key", symbol);
            envelope.body.input = input;

            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build subscribe json", e);
        }
    }

    // === DTOs for WS payloads ===
    static class WsEnvelope {
        public WsHeader header;
        public WsBody body;
    }

    static class WsHeader {
        public String appkey;
        public String approval_key;
        public String custtype;
        public String tr_type;
        @JsonProperty("content-type")
        public String content_type;
        public String tr_id;
    }

    static class WsBody {
        public java.util.Map<String, Object> input;
    }
}



