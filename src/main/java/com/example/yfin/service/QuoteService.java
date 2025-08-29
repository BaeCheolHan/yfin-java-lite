package com.example.yfin.service;

import com.example.yfin.exception.NotFoundException;
import com.example.yfin.http.YahooApiClient;
import com.example.yfin.http.AlphaVantageClient;
import com.example.yfin.http.FinnhubClient;
import com.example.yfin.model.QuoteDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuoteService {

    private final YahooApiClient yahooApiClient;
    private final TickerResolver tickerResolver;
    private final com.example.yfin.service.cache.RedisCacheService level2Cache;
    private final DividendsService dividendsService;
    private final AlphaVantageClient alphaVantageClient;
    private final FinnhubClient finnhubClient;

    

    @Cacheable(cacheNames = "quote", key = "#ticker")
    public Mono<QuoteDto> quote(String ticker) {
        return tickerResolver.normalize(ticker)
                .flatMap(nt -> quotes(List.of(nt))
                        .flatMap(list -> list.isEmpty()
                                ? Mono.error(new NotFoundException("Ticker not found: " + nt))
                                : Mono.just(list.get(0))));
    }

    public Mono<QuoteDto> quoteEx(String ticker, String exchange) {
        return tickerResolver.normalize(ticker, exchange)
                .flatMap(nt -> quotes(List.of(nt))
                        .flatMap(list -> list.isEmpty()
                                ? Mono.error(new NotFoundException("Ticker not found: " + nt))
                                : Mono.just(list.get(0))));
    }

    public Mono<List<QuoteDto>> quotes(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) return Mono.just(List.of());
        return normalizeTickers(tickers).flatMap(norm -> {
            // 심볼을 6~8개 단위로 배치 분할
            int batchSize = 8;
            java.util.List<java.util.List<String>> batches = new java.util.ArrayList<>();
            for (int i = 0; i < norm.size(); i += batchSize) {
                batches.add(norm.subList(i, Math.min(i + batchSize, norm.size())));
            }

            // 배치를 직렬 처리 + 배치 간 지터로 차단 완화
            return Flux.fromIterable(batches)
                    .concatMap(group -> {
                        long jitter = java.util.concurrent.ThreadLocalRandom.current().nextLong(120, 320);
                        String symbols = String.join(",", group);
                        String path = "/v7/finance/quote?symbols=" + symbols + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
                        String ref = "/quote/" + group.get(0);
                        String cacheKey = "quotes:" + symbols;
                        Mono<java.util.List<QuoteDto>> call = level2Cache.get(cacheKey, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<QuoteDto>>() {})
                                .switchIfEmpty(
                                        (yahooApiClient.isTemporarilyBlocked()
                                                ? fallbackQuotes(group).map(this::mapQuotes)
                                                : yahooApiClient.getJson(path, ref)
                                                        .onErrorResume(e -> fallbackQuotes(group))
                                                        .map(this::mapQuotes)
                                        )
                                                // 캐시 TTL을 조금 늘려 차단 시 재사용 여지 확보
                                                .flatMap(list -> level2Cache.set(cacheKey, list, java.time.Duration.ofSeconds(45)).thenReturn(list))
                                )
                                .timeout(java.time.Duration.ofSeconds(12))
                                .onErrorResume(e -> fallbackQuotes(group).map(this::mapQuotes));
                        return reactor.core.publisher.Mono.delay(java.time.Duration.ofMillis(jitter)).then(call);
                    }, 1)
                    .collectList()
                    .flatMap(parts -> {
                        java.util.List<QuoteDto> all = new java.util.ArrayList<>();
                        for (java.util.List<QuoteDto> p : parts) if (p != null) all.addAll(p);
                        return Mono.just(all);
                    })
                    .flatMap(base -> {
                java.util.List<String> needForward = new java.util.ArrayList<>();
                for (QuoteDto q : base) {
                    if (q.getForwardDividendYield() == null && q.getForwardDividendRate() == null) {
                        needForward.add(q.getSymbol());
                    }
                }
                if (needForward.isEmpty()) {
                    return Mono.just(base);
                }
                String modules = "summaryDetail";
                java.util.List<Mono<Void>> enrichCalls = new java.util.ArrayList<>();
                for (QuoteDto q : base) {
                    if (q.getForwardDividendYield() != null || q.getForwardDividendRate() != null) continue;
                    String sym = q.getSymbol();
                    if (!yahooApiClient.isTemporarilyBlocked()) {
                        String p = "/v10/finance/quoteSummary/" + sym + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
                        enrichCalls.add(
                                yahooApiClient.getJson(p, "/quote/" + sym)
                                        .doOnNext(b -> enrichForward(q, b))
                                        .onErrorResume(err -> Mono.empty())
                                        .then()
                        );
                    }
                }
                return Mono.when(enrichCalls).then(Mono.defer(() -> {
                    java.util.List<Mono<Void>> ttmCalls = new java.util.ArrayList<>();
                    for (QuoteDto q : base) {
                        if (q.getForwardDividendYield() != null || q.getForwardDividendRate() != null) continue;
                        String sym = q.getSymbol();
                        ttmCalls.add(
                                dividendsService.dividends(sym, "2y")
                                        .doOnNext(div -> enrichTtm(q, div))
                                        .onErrorResume(err -> Mono.empty())
                                        .then()
                        );
                    }
                    if (ttmCalls.isEmpty()) return Mono.just(base);
                    return Mono.when(ttmCalls).thenReturn(base);
                }));
            });
        });
    }

    public Mono<List<QuoteDto>> quotesEx(List<String> tickers, String exchange) {
        if (tickers == null || tickers.isEmpty()) return Mono.just(List.of());
        return normalizeTickers(tickers, exchange).flatMap(norm -> {
            String symbols = String.join(",", norm);
            String path = "/v7/finance/quote?symbols=" + symbols + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String ref = "/quote/" + norm.get(0);
            return yahooApiClient.getJson(path, ref).map(this::mapQuotes);
        });
    }

    /**
     * WS 보조 스냅샷 용도: Yahoo 전용 호출만 수행하고, 차단 상태면 빈 리스트 반환.
     * finnhub/alpha 폴백은 여기서 사용하지 않음(레이트리밋 보호).
     */
    public Mono<List<QuoteDto>> quotesSnapshotSafe(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) return Mono.just(List.of());
        if (yahooApiClient.isTemporarilyBlocked()) return Mono.just(List.of());
        return quotesEx(tickers, null)
                .onErrorResume(e -> Mono.just(List.of()));
    }

    // ---------- 내부 매퍼/유틸 ----------
    private List<QuoteDto> mapQuotes(Map<String, Object> body) {
        Map<String, Object> qr = asMap(body.get("quoteResponse"));
        if (qr == null) return List.of();
        List<Map<String, Object>> results = asListOfMap(qr.get("result"));
        if (results == null) return List.of();
        List<QuoteDto> out = new ArrayList<>(results.size());
        for (Map<String, Object> m : results) {
            QuoteDto q = new QuoteDto();
            q.setSymbol(s(m.get("symbol")));
            q.setShortName(s(m.get("shortName")));
            q.setCurrency(s(m.get("currency")));
            q.setRegularMarketPrice(d(m.get("regularMarketPrice")));
            q.setRegularMarketChange(d(m.get("regularMarketChange")));
            q.setRegularMarketChangePercent(d(m.get("regularMarketChangePercent")));
            q.setRegularMarketVolume(l(m.get("regularMarketVolume")));
            q.setPreviousClose(d(m.get("regularMarketPreviousClose")));
            q.setDayHigh(d(m.get("regularMarketDayHigh")));
            q.setDayLow(d(m.get("regularMarketDayLow")));
            q.setFiftyTwoWeekHigh(d(m.get("fiftyTwoWeekHigh")));
            q.setFiftyTwoWeekLow(d(m.get("fiftyTwoWeekLow")));
            q.setTrailingAnnualDividendRate(d(m.get("trailingAnnualDividendRate")));
            q.setTrailingAnnualDividendYield(normalizeYield(d(m.get("trailingAnnualDividendYield"))));
            q.setDividendYield(d(m.get("dividendYield")));
            q.setForwardDividendRate(d(m.get("dividendRate")));
            q.setForwardDividendYield(normalizeYield(d(m.get("dividendYield"))));
            if (q.getForwardDividendYield() != null) q.setForwardDividendYieldPct(q.getForwardDividendYield() * 100.0);
            out.add(q);
        }
        return out;
    }

    private void enrichForward(QuoteDto target, Map<String, Object> body) {
        Map<String, Object> qs = asMap(body.get("quoteSummary"));
        if (qs == null) return;
        List<Map<String, Object>> result = asListOfMap(qs.get("result"));
        if (result == null || result.isEmpty()) return;
        Map<String, Object> r0 = result.get(0);
        Map<String, Object> sd = asMap(r0.get("summaryDetail"));
        if (sd == null) return;
        Object dy = sd.get("dividendYield");
        Object dr = sd.get("dividendRate");
        if (target.getForwardDividendYield() == null) target.setForwardDividendYield(normalizeYield(d(dy)));
        if (target.getForwardDividendRate() == null) target.setForwardDividendRate(d(dr));
        if (target.getForwardDividendYield() != null && target.getForwardDividendYieldPct() == null) {
            target.setForwardDividendYieldPct(target.getForwardDividendYield() * 100.0);
        }
    }

    private static Double normalizeYield(Double v) { if (v == null) return null; return v > 1.0 ? v / 100.0 : v; }
    private void enrichTtm(QuoteDto target, com.example.yfin.model.DividendsResponse div) {
        if (div == null || div.getRows() == null || div.getRows().isEmpty()) return;
        Instant cutoff = Instant.now().minus(365, ChronoUnit.DAYS);
        double ttm = div.getRows().stream()
                .filter(r -> r.getDate() != null && r.getDate().isAfter(cutoff))
                .map(r -> r.getAmount() == null ? 0.0 : r.getAmount())
                .mapToDouble(Double::doubleValue)
                .sum();
        if (ttm <= 0.0) return;
        target.setForwardDividendRate(target.getForwardDividendRate() == null ? ttm : target.getForwardDividendRate());
        if (target.getRegularMarketPrice() != null && target.getRegularMarketPrice() > 0.0) {
            double y = ttm / target.getRegularMarketPrice();
            if (target.getForwardDividendYield() == null) target.setForwardDividendYield(y);
            if (target.getForwardDividendYieldPct() == null) target.setForwardDividendYieldPct(y * 100.0);
        }
    }
    private Mono<List<String>> normalizeTickers(List<String> tickers) {
        List<Mono<String>> monos = new ArrayList<>(tickers.size());
        for (String t : tickers) {
            String raw = t;
            monos.add(
                    tickerResolver.normalize(raw)
                            .timeout(java.time.Duration.ofSeconds(2))
                            .onErrorResume(e -> reactor.core.publisher.Mono.just(raw))
            );
        }
        return reactor.core.publisher.Mono.zip(monos, arr -> {
            List<String> out = new ArrayList<>(arr.length);
            for (Object o : arr) out.add(String.valueOf(o));
            return out;
        }).timeout(java.time.Duration.ofSeconds(5))
                .onErrorResume(e -> reactor.core.publisher.Mono.just(tickers));
    }
    private Mono<List<String>> normalizeTickers(List<String> tickers, String exchange) {
        if (exchange == null || exchange.isBlank()) return normalizeTickers(tickers);
        List<Mono<String>> monos = new ArrayList<>(tickers.size());
        for (String t : tickers) {
            String raw = t;
            monos.add(
                    tickerResolver.normalize(raw, exchange)
                            .timeout(java.time.Duration.ofSeconds(2))
                            .onErrorResume(e -> reactor.core.publisher.Mono.just(raw))
            );
        }
        return reactor.core.publisher.Mono.zip(monos, arr -> {
            List<String> out = new ArrayList<>(arr.length);
            for (Object o : arr) out.add(String.valueOf(o));
            return out;
        }).timeout(java.time.Duration.ofSeconds(5))
                .onErrorResume(e -> reactor.core.publisher.Mono.just(tickers));
    }
    private static Double d(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        Map<String, Object> m = asMap(o);
        if (m != null) return d(m.get("raw"));
        if (o instanceof String s) {
            String t = s.trim();
            if (t.endsWith("%")) t = t.substring(0, t.length() - 1).trim();
            try { return Double.parseDouble(t); } catch (Exception ignored) { return null; }
        }
        return null;
    }
    private static Long l(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        Map<String, Object> m = asMap(o);
        if (m != null) return l(m.get("raw"));
        if (o instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (Exception ignored) { return null; }
        }
        return null;
    }

    private static Map<String, Object> asMap(Object o) {
        if (!(o instanceof Map<?, ?> src)) return null;
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<?, ?> e : src.entrySet()) {
            if (e.getKey() instanceof String k) out.put(k, e.getValue());
        }
        return out;
    }
    private static List<Map<String, Object>> asListOfMap(Object o) {
        if (!(o instanceof List<?> list)) return null;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object it : list) {
            Map<String, Object> m = asMap(it);
            if (m != null) out.add(m);
        }
        return out;
    }
    private static String s(Object o) { return o == null ? null : String.valueOf(o); }

    // --------- Fallback providers ---------
    private reactor.core.publisher.Mono<java.util.Map<String, Object>> fallbackQuotes(java.util.List<String> symbols) {
        // 동시성 제한(최대 4) + 지터로 레이트리밋 완화
        return Flux.fromIterable(symbols)
                .flatMap(sym -> {
                    long jitterMs = java.util.concurrent.ThreadLocalRandom.current().nextLong(50, 200);
                    return reactor.core.publisher.Mono.delay(java.time.Duration.ofMillis(jitterMs))
                            .then(fallbackQuote(sym))
                            .onErrorResume(e -> reactor.core.publisher.Mono.empty());
                }, 4)
                .collectList()
                .map(collected -> {
                    java.util.Map<String, Object> qr = new java.util.LinkedHashMap<>();
                    java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
                    for (com.example.yfin.model.QuoteDto q : collected) {
                        if (q == null) continue;
                        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("symbol", q.getSymbol());
                        m.put("shortName", q.getShortName());
                        m.put("currency", q.getCurrency());
                        m.put("regularMarketPrice", q.getRegularMarketPrice());
                        m.put("regularMarketChange", q.getRegularMarketChange());
                        m.put("regularMarketChangePercent", q.getRegularMarketChangePercent());
                        m.put("regularMarketVolume", q.getRegularMarketVolume());
                        m.put("regularMarketPreviousClose", q.getPreviousClose());
                        m.put("regularMarketDayHigh", q.getDayHigh());
                        m.put("regularMarketDayLow", q.getDayLow());
                        m.put("fiftyTwoWeekHigh", q.getFiftyTwoWeekHigh());
                        m.put("fiftyTwoWeekLow", q.getFiftyTwoWeekLow());
                        m.put("trailingAnnualDividendRate", q.getTrailingAnnualDividendRate());
                        m.put("trailingAnnualDividendYield", q.getTrailingAnnualDividendYield());
                        m.put("dividendYield", q.getDividendYield());
                        m.put("dividendRate", q.getForwardDividendRate());
                        result.add(m);
                    }
                    qr.put("result", result);
                    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
                    body.put("quoteResponse", qr);
                    return body;
                });
    }

    private reactor.core.publisher.Mono<com.example.yfin.model.QuoteDto> fallbackQuote(String symbol) {
        boolean fhEnabled = (finnhubClient != null && finnhubClient.isEnabled());
        boolean avEnabled = (alphaVantageClient != null && alphaVantageClient.isEnabled());

        reactor.core.publisher.Mono<com.example.yfin.model.QuoteDto> finnhCall = fhEnabled
                ? finnhubClient.getQuote(symbol)
                    .map(m -> mapFinnhubQuote(symbol, m))
                : reactor.core.publisher.Mono.empty();
        reactor.core.publisher.Mono<com.example.yfin.model.QuoteDto> avCall = avEnabled
                ? alphaVantageClient.getQuote(symbol)
                    .map(m -> mapAlphaVantageGlobalQuote(symbol, m))
                : reactor.core.publisher.Mono.empty();

        // 심볼 기반 해시 분산: 절반은 Finnhub, 절반은 AlphaVantage 우선
        boolean preferFinnhub = fhEnabled && (!avEnabled || Math.abs(symbol.hashCode()) % 2 == 0);
        reactor.core.publisher.Mono<com.example.yfin.model.QuoteDto> primary = preferFinnhub ? finnhCall : avCall;
        reactor.core.publisher.Mono<com.example.yfin.model.QuoteDto> secondary = preferFinnhub ? avCall : finnhCall;

        long jitterMs = java.util.concurrent.ThreadLocalRandom.current().nextLong(30, 150);

        return reactor.core.publisher.Mono.delay(java.time.Duration.ofMillis(jitterMs))
                .then(primary)
                .onErrorResume(e -> {
                    if (isRateLimited(e)) {
                        long backoff = java.util.concurrent.ThreadLocalRandom.current().nextLong(200, 600);
                        return reactor.core.publisher.Mono.delay(java.time.Duration.ofMillis(backoff)).then(secondary);
                    }
                    return reactor.core.publisher.Mono.empty();
                })
                .switchIfEmpty(secondary.onErrorResume(e -> reactor.core.publisher.Mono.empty()));
    }

    private boolean isRateLimited(Throwable e) {
        if (e == null) return false;
        if (e instanceof WebClientResponseException w) {
            int s = w.getStatusCode().value();
            return s == 429 || s == 403;
        }
        String msg = e.getMessage();
        return msg != null && (msg.contains("429") || msg.contains("Too Many Requests") || msg.contains("403"));
    }

    private com.example.yfin.model.QuoteDto mapFinnhubQuote(String symbol, java.util.Map<String, Object> body) {
        if (body == null || body.isEmpty()) return null;
        com.example.yfin.model.QuoteDto q = new com.example.yfin.model.QuoteDto();
        q.setSymbol(symbol);
        q.setRegularMarketPrice(d(body.get("c")));
        q.setRegularMarketChange(d(body.get("d")));
        Double dp = d(body.get("dp"));
        q.setRegularMarketChangePercent(dp);
        q.setDayHigh(d(body.get("h")));
        q.setDayLow(d(body.get("l")));
        q.setPreviousClose(d(body.get("pc")));
        return q;
    }

    private com.example.yfin.model.QuoteDto mapAlphaVantageGlobalQuote(String symbol, java.util.Map<String, Object> body) {
        if (body == null) return null;
        java.util.Map<String, Object> gq = asMap(body.get("Global Quote"));
        if (gq == null || gq.isEmpty()) return null;
        com.example.yfin.model.QuoteDto q = new com.example.yfin.model.QuoteDto();
        q.setSymbol(symbol);
        q.setRegularMarketPrice(d(gq.get("05. price")));
        q.setPreviousClose(d(gq.get("08. previous close")));
        Double p = q.getRegularMarketPrice();
        Double pc = q.getPreviousClose();
        if (p != null && pc != null) {
            double ch = p - pc;
            q.setRegularMarketChange(ch);
            if (pc != 0.0) q.setRegularMarketChangePercent(ch / pc * 100.0);
        } else {
            String cpp = s(gq.get("10. change percent"));
            if (cpp != null && cpp.endsWith("%")) {
                try { q.setRegularMarketChangePercent(Double.parseDouble(cpp.substring(0, cpp.length()-1))); } catch (Exception ignored) {}
            }
        }
        q.setRegularMarketVolume(l(gq.get("06. volume")));
        return q;
    }
}


