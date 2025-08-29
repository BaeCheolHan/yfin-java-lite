package com.example.yfin.realtime;

import com.example.yfin.http.FinnhubClient;
import com.example.yfin.model.QuoteDto;
import com.example.yfin.kis.KisWsClient;
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

    public QuoteWebSocketHandler(QuoteService quoteService, FinnhubClient finnhub, FinnhubWsClient finnhubWs, TickerResolver resolver, KisWsClient kisWs) {
        this.quoteService = quoteService;
        this.finnhub = finnhub;
        this.finnhubWs = finnhubWs;
        this.resolver = resolver;
        this.kisWs = kisWs;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Map<String, String> q = parseQuery(session.getHandshakeInfo().getUri().getQuery());
        String tickersParam = q.getOrDefault("tickers", "");
        List<String> rawTickers = parseTickers(tickersParam);
        int intervalSec = parseInt(q.get("intervalSec"), 2);
        if (rawTickers.isEmpty()) {
            return session.send(Flux.just(session.textMessage("{" + "\"error\":\"tickers required\"}")));
        }

        return normalizeTickers(rawTickers)
                .flatMap(tickers -> {
                    Flux<String> stream = buildQuoteStream(tickers, intervalSec)
                            .map(this::toJson)
                            .onErrorResume(e -> Flux.just("{" + "\"error\":\"" + e.getMessage().replace('"',' ') + "\"}"));
                    return session.send(stream.map(session::textMessage));
                });
    }

    private Flux<QuoteDto> buildQuoteStream(List<String> tickers, int intervalSec) {
        boolean useKis = kisWs != null && kisWs.isEnabled();
        boolean useWs = (finnhubWs != null && finnhubWs.isEnabled()) || useKis;
        if (useWs) {
            // 심볼별 WS 구독을 머지 (국내 종목은 KIS가 우선)
            List<Flux<QuoteDto>> streams = new ArrayList<>();
            if (useKis) for (String sym : tickers) streams.add(kisWs.subscribe(sym));
            if (finnhubWs != null && finnhubWs.isEnabled()) for (String sym : tickers) streams.add(finnhubWs.subscribe(sym));
            Flux<QuoteDto> wsFlux = Flux.merge(streams);

            // 폴링 보강: finnhub 레이트리밋 보호를 위해 Yahoo 전용 스냅샷 + 주기 축소
            int sec = useKis ? Math.max(1, intervalSec) : Math.max(10, Math.max(2, intervalSec));
            Flux<QuoteDto> pollFlux = Flux.interval(Duration.ZERO, Duration.ofSeconds(sec))
                    .flatMap(t -> quoteService.quotesSnapshotSafe(tickers))
                    .flatMapIterable(list -> list)
                    .onErrorResume(e -> Flux.empty());

            return Flux.merge(wsFlux, pollFlux)
                    .distinctUntilChanged(q -> q.getSymbol() + ":" + q.getRegularMarketPrice());
        }
        int sec = (finnhub != null && finnhub.isEnabled()) ? Math.max(1, intervalSec) : Math.max(2, intervalSec);
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(sec))
                .flatMap(t -> quoteService.quotes(tickers))
                .flatMapIterable(list -> list)
                .onErrorResume(e -> Flux.empty());
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

    private String escape(String s) { return s == null ? "" : s.replace("\\","\\\\").replace("\"","\\\""); }

    private Mono<List<String>> normalizeTickers(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) return Mono.just(List.of());
        List<Mono<String>> monos = new ArrayList<>(tickers.size());
        for (String t : tickers) {
            String raw = t;
            monos.add(
                    resolver.normalize(raw)
                            .timeout(Duration.ofSeconds(2))
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
        }).timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("normalize all timeout/failure -> using raw list: {}", e.toString());
                    return Mono.just(tickers);
                });
    }
}


