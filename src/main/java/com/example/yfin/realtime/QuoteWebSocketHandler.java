package com.example.yfin.realtime;

import com.example.yfin.http.FinnhubClient;
import com.example.yfin.model.QuoteDto;
import com.example.yfin.kis.KisWsClient;
import com.example.yfin.kis.KisWebSocketManager;
import com.example.yfin.service.QuoteService;
import com.example.yfin.service.TickerResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Component
public class QuoteWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(QuoteWebSocketHandler.class);

    private final QuoteService quoteService;
    private final FinnhubClient finnhub;
    private final FinnhubWsClient finnhubWs;
    private final TickerResolver resolver;
    private final KisWsClient kisWs;
    private final KisWebSocketManager kisManager;

    public QuoteWebSocketHandler(QuoteService quoteService, FinnhubClient finnhub, FinnhubWsClient finnhubWs, TickerResolver resolver, KisWsClient kisWs, KisWebSocketManager kisManager) {
        this.quoteService = quoteService;
        this.finnhub = finnhub;
        this.finnhubWs = finnhubWs;
        this.resolver = resolver;
        this.kisWs = kisWs;
        this.kisManager = kisManager;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Map<String, String> q = parseQuery(session.getHandshakeInfo().getUri().getQuery());
        String tickersParam = q.getOrDefault("tickers", "");
        List<String> rawTickers = parseTickers(tickersParam);
        int intervalSec = parseInt(q.get("intervalSec"), 2);
        String exchange = q.get("exchange");
        if (rawTickers.isEmpty()) {
            return session.send(Flux.just(session.textMessage("{" + "\"error\":\"tickers required\"}")));
        }

        return normalizeTickers(rawTickers, exchange)
                .flatMap(tickers -> {
                    // 실시간 스트림(JSON) — 스냅샷/캐시 사용 금지
                    Flux<String> stream = buildQuoteStream(session.getId(), tickers, intervalSec)
                            .map(this::toJson)
                            .onErrorResume(e -> Flux.just("{" + "\"error\":\"" + e.getMessage().replace('"',' ') + "\"}"));

                    // 스냅샷 송출 비활성화(WS는 실시간만)
                    Flux<String> initialSnapshot = Flux.empty();

                    // 주기적 하트비트(연결상태 확인용)
                    Flux<String> heartbeat = Flux.interval(Duration.ofSeconds(15))
                            .map(i -> "{\"hb\":1}");

                    return session.send(Flux.merge(initialSnapshot, stream, heartbeat).map(session::textMessage))
                            .doFinally(signal -> {
                                // WebSocket 연결 종료 시 구독 해제
                                kisManager.unsubscribeAll(session.getId());
                            });
                });
    }

    private boolean isAnyWsProviderEnabled() {
        boolean kisEnabled = (kisWs != null && kisWs.isEnabled());
        boolean fhEnabled = (finnhubWs != null && finnhubWs.isEnabled());
        return kisEnabled || fhEnabled;
    }

    private Flux<QuoteDto> buildQuoteStream(String sessionId, List<String> tickers, int intervalSec) {
        boolean useKis = kisWs != null && kisWs.isEnabled();
        boolean useWs = (finnhubWs != null && finnhubWs.isEnabled()) || useKis;
        
        if (useWs) {
            // 심볼별 WS 구독을 머지 (국내 종목은 KIS가 우선)
            List<Flux<QuoteDto>> streams = new ArrayList<>();
            if (useKis) {
                for (String sym : tickers) {
                    String kisSym = toKisSymbol(sym);
                    streams.add(kisManager.subscribe(sessionId, kisSym));
                }
            }
            if (finnhubWs != null && finnhubWs.isEnabled()) {
                for (String sym : tickers) streams.add(finnhubWs.subscribe(sym));
            }
            Flux<QuoteDto> wsFlux = Flux.merge(streams);
            
            return wsFlux.distinctUntilChanged(q -> q.getSymbol() + ":" + q.getRegularMarketPrice());
        }
        // WS 공급자가 없는 경우: 데이터 미송출(하트비트만 유지)
        return Flux.never();
    }

    private Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return m;
        for (String p : raw.split("&")) {
            int i = p.indexOf('=');
            if (i > 0) m.put(urlDecode(p.substring(0, i)), urlDecode(p.substring(i + 1)));
        }
        return m;
    }

    private String urlDecode(String s) {
        try { return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8); } catch (Exception e) { return s; }
    }

    private int parseInt(String s, int def) {
        try { return s == null ? def : Integer.parseInt(s); } catch (Exception ignored) { return def; }
    }

    private List<String> parseTickers(String s) {
        if (s == null || s.isBlank()) return List.of();
        String[] arr = s.split(",");
        List<String> out = new ArrayList<>(arr.length);
        for (String it : arr) {
            String t = it.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private String toJson(QuoteDto q) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{')
                .append("\"symbol\":\"").append(escape(q.getSymbol())).append('\"')
                .append(',').append("\"price\":").append(q.getRegularMarketPrice())
                .append(',').append("\"dp\":").append(q.getRegularMarketChangePercent())
                .append('}');
        return sb.toString();
    }

    // KIS WS는 국내 종목에 접미사(.KS/.KQ)가 없는 6자리 티커를 기대하므로 제거
    private String toKisSymbol(String sym) {
        if (sym == null) return null;
        String u = sym.toUpperCase(Locale.ROOT);
        int dot = u.indexOf('.');
        if (dot > 0 && (u.endsWith(".KS") || u.endsWith(".KQ"))) {
            return u.substring(0, dot);
        }
        return u;
    }

    private String escape(String s) { return s == null ? "" : s.replace("\\","\\\\").replace("\"","\\\""); }

    private Mono<List<String>> normalizeTickers(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) return Mono.just(List.of());
        List<Mono<String>> monos = new ArrayList<>(tickers.size());
        for (String t : tickers) {
            String raw = t;
            monos.add(
                    resolver.normalize(raw)
                            .timeout(Duration.ofSeconds(5))
                            .onErrorResume(e -> {
                                log.warn("normalize timeout/failure for {} -> fallback raw: {}", raw, e.toString());
                                return Mono.just(raw);
                            })
            );
        }
        return Mono.zip(monos, arr -> {
            List<String> out = new ArrayList<>(arr.length);
            for (Object o : arr) out.add(String.valueOf(o));
            return out;
        }).timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    log.warn("normalize all timeout/failure -> using raw list: {}", e.toString());
                    return Mono.just(tickers);
                });
    }

    private Mono<List<String>> normalizeTickers(List<String> tickers, String exchange) {
        if (exchange == null || exchange.isBlank()) return normalizeTickers(tickers);
        if (tickers == null || tickers.isEmpty()) return Mono.just(List.of());
        List<Mono<String>> monos = new ArrayList<>(tickers.size());
        for (String t : tickers) {
            String raw = t;
            monos.add(
                    resolver.normalize(raw, exchange)
                            .timeout(Duration.ofSeconds(5))
                            .onErrorResume(e -> Mono.just(raw))
            );
        }
        return Mono.zip(monos, arr -> {
            List<String> out = new ArrayList<>(arr.length);
            for (Object o : arr) out.add(String.valueOf(o));
            return out;
        }).timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> Mono.just(tickers));
    }
}


