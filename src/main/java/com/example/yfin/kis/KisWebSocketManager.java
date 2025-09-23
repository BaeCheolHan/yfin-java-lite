package com.example.yfin.kis;

import com.example.yfin.model.QuoteDto;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KIS WebSocket 통합 관리자
 * - 1개 세션으로 최대 41개 종목 관리
 * - 클라이언트별 구독 요청을 통합하여 KIS에 전송
 * - 종목별 데이터를 구독자에게 팬아웃
 */
@Component
public class KisWebSocketManager {
    
    private static final Logger log = LoggerFactory.getLogger(KisWebSocketManager.class);
    
    private final KisWsClient kisWsClient;
    
    // 클라이언트별 구독 관리
    private final ConcurrentMap<String, ConcurrentMap<String, Integer>> clientSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Sinks.Many<QuoteDto>> symbolSinks = new ConcurrentHashMap<>();
    
    // KIS WebSocket 연결 상태
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicInteger totalSymbols = new AtomicInteger(0);
    private final int MAX_SYMBOLS_PER_SESSION = 41;
    
    public KisWebSocketManager(KisWsClient kisWsClient) {
        this.kisWsClient = kisWsClient;
    }
    
    /**
     * 클라이언트가 종목을 구독 요청
     * @param clientId 클라이언트 ID (세션 ID 등)
     * @param symbol 구독할 종목
     * @return 해당 종목의 실시간 데이터 스트림
     */
    public Flux<QuoteDto> subscribe(String clientId, String symbol) {
        // 클라이언트별 구독 정보 업데이트
        ConcurrentMap<String, Integer> clientSymbols = clientSubscriptions.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>());
        int newCount = clientSymbols.merge(symbol, 1, Integer::sum);
        
        // 전체 종목 수 업데이트
        boolean isNewSymbol = newCount == 1;
        if (isNewSymbol) {
            int totalCount = totalSymbols.incrementAndGet();
            
            // 종목별 싱크 생성
            Sinks.Many<QuoteDto> sink = symbolSinks.computeIfAbsent(symbol, k -> Sinks.many().multicast().onBackpressureBuffer());
            
            // KIS WebSocket에 종목 추가 및 데이터 연결
            kisWsClient.subscribe(symbol)
                    .doOnNext(quote -> sink.tryEmitNext(quote))
                    .doOnError(e -> log.error("Error in KIS data stream for {}: {}", symbol, e.getMessage()))
                    .subscribe();
        }
        
        // 클라이언트에게 데이터 스트림 반환
        Sinks.Many<QuoteDto> sink = symbolSinks.get(symbol);
        if (sink != null) {
            return sink.asFlux()
                    .doOnCancel(() -> unsubscribe(clientId, symbol));
        }
        
        return Flux.empty();
    }
    
    /**
     * 클라이언트가 종목 구독 해제
     * @param clientId 클라이언트 ID
     * @param symbol 해제할 종목
     */
    public void unsubscribe(String clientId, String symbol) {
        // 클라이언트별 구독 정보 업데이트
        ConcurrentMap<String, Integer> clientSymbols = clientSubscriptions.get(clientId);
        if (clientSymbols != null) {
            Integer newCount = clientSymbols.merge(symbol, -1, (old, delta) -> {
                int result = old + delta;
                return result <= 0 ? null : result;
            });
            
            if (newCount == null) {
                // 클라이언트가 해당 종목을 완전히 해제
                clientSymbols.remove(symbol);
                
                // 다른 클라이언트가 구독 중인지 확인
                boolean hasOtherSubscribers = clientSubscriptions.values().stream()
                        .anyMatch(symbols -> symbols.containsKey(symbol));
                
                if (!hasOtherSubscribers) {
                    // 다른 클라이언트가 없으면 KIS WebSocket에서도 해제
                    kisWsClient.unsubscribe(symbol);
                    symbolSinks.remove(symbol);
                    totalSymbols.decrementAndGet();
                }
            }
        }
        
        // 클라이언트가 모든 종목을 해제했으면 정리
        if (clientSymbols != null && clientSymbols.isEmpty()) {
            clientSubscriptions.remove(clientId);
        }
    }
    
    /**
     * 클라이언트가 모든 구독을 해제
     * @param clientId 클라이언트 ID
     */
    public void unsubscribeAll(String clientId) {
        ConcurrentMap<String, Integer> clientSymbols = clientSubscriptions.remove(clientId);
        if (clientSymbols != null) {
            for (String symbol : clientSymbols.keySet()) {
                unsubscribe(clientId, symbol);
            }
        }
    }
    
    /**
     * 현재 구독 상태 정보
     */
    public void logSubscriptionStatus() {
        log.info("=== KIS WebSocket Subscription Status ===");
        log.info("Total symbols: {}", totalSymbols.get());
        log.info("Active clients: {}", clientSubscriptions.size());
        log.info("Symbol sinks: {}", symbolSinks.size());
        
        clientSubscriptions.forEach((clientId, symbols) -> {
            log.info("Client {}: {} symbols", clientId, symbols.size());
        });
        log.info("=== End Subscription Status ===");
    }
    
    /**
     * KIS WebSocket 연결 상태 확인
     */
    public boolean isConnected() {
        return isConnected.get();
    }
    
    /**
     * 현재 구독된 종목 수
     */
    public int getTotalSymbols() {
        return totalSymbols.get();
    }
    
    /**
     * Graceful Shutdown - 모든 클라이언트 구독 해제
     */
    @PreDestroy
    public void gracefulShutdown() {
        log.info("=== KisWebSocketManager Graceful Shutdown Started ===");
        
        // 모든 클라이언트 세션의 구독 해제
        for (String sessionId : clientSubscriptions.keySet()) {
            log.info("Unsubscribing all symbols for session: {}", sessionId);
            unsubscribeAll(sessionId);
        }
        
        // 모든 심볼 싱크 완료
        symbolSinks.values().forEach(sink -> sink.tryEmitComplete());
        
        // 상태 초기화
        clientSubscriptions.clear();
        symbolSinks.clear();
        totalSymbols.set(0);
        
        log.info("=== KisWebSocketManager Graceful Shutdown Completed ===");
    }
}
