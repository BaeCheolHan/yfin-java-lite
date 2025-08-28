package com.example.yfin.service;

import com.example.yfin.exception.NotFoundException;
import com.example.yfin.http.YahooApiClient;
import com.example.yfin.model.QuoteDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class QuoteService {

    private final YahooApiClient yahoo;
    private final TickerResolver resolver;
    private final com.example.yfin.service.cache.RedisCacheService l2;
    private final DividendsService dividendsService;

    public QuoteService(YahooApiClient yahoo,
                        TickerResolver resolver,
                        com.example.yfin.service.cache.RedisCacheService l2,
                        DividendsService dividendsService) {
        this.yahoo = yahoo;
        this.resolver = resolver;
        this.l2 = l2;
        this.dividendsService = dividendsService;
    }

    @Cacheable(cacheNames = "quote", key = "#ticker")
    public Mono<QuoteDto> quote(String ticker) {
        return resolver.normalize(ticker)
                .flatMap(nt -> quotes(List.of(nt))
                        .flatMap(list -> list.isEmpty()
                                ? Mono.error(new NotFoundException("Ticker not found: " + nt))
                                : Mono.just(list.get(0))));
    }

    public Mono<QuoteDto> quoteEx(String ticker, String exchange) {
        return resolver.normalize(ticker, exchange)
                .flatMap(nt -> quotes(List.of(nt))
                        .flatMap(list -> list.isEmpty()
                                ? Mono.error(new NotFoundException("Ticker not found: " + nt))
                                : Mono.just(list.get(0))));
    }

    public Mono<List<QuoteDto>> quotes(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) return Mono.just(List.of());
        return normalizeTickers(tickers).flatMap(norm -> {
            String symbols = String.join(",", norm);
            String path = "/v7/finance/quote?symbols=" + symbols + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String ref = "/quote/" + norm.get(0);
            String cacheKey = "quotes:" + symbols;
            return l2.get(cacheKey, new com.fasterxml.jackson.core.type.TypeReference<List<QuoteDto>>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, ref).flatMap(quotesBody -> {
                                List<QuoteDto> base = mapQuotes(quotesBody);
                                List<String> needForward = new ArrayList<>();
                                for (QuoteDto q : base) {
                                    if (q.getForwardDividendYield() == null && q.getForwardDividendRate() == null) {
                                        needForward.add(q.getSymbol());
                                    }
                                }
                                if (needForward.isEmpty()) {
                                    return Mono.just(base);
                                }
                                String modules = "summaryDetail";
                                List<Mono<Void>> enrichCalls = new ArrayList<>();
                                for (QuoteDto q : base) {
                                    if (q.getForwardDividendYield() != null || q.getForwardDividendRate() != null) continue;
                                    String sym = q.getSymbol();
                                    String p = "/v10/finance/quoteSummary/" + sym + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
                                    enrichCalls.add(
                                            yahoo.getJson(p, "/quote/" + sym).doOnNext(b -> enrichForward(q, b)).then()
                                    );
                                }
                                return Mono.when(enrichCalls).then(Mono.defer(() -> {
                                    List<Mono<Void>> ttmCalls = new ArrayList<>();
                                    for (QuoteDto q : base) {
                                        if (q.getForwardDividendYield() != null || q.getForwardDividendRate() != null) continue;
                                        String sym = q.getSymbol();
                                        ttmCalls.add(
                                                dividendsService.dividends(sym, "2y").doOnNext(div -> enrichTtm(q, div)).then()
                                        );
                                    }
                                    if (ttmCalls.isEmpty()) return Mono.just(base);
                                    return Mono.when(ttmCalls).thenReturn(base);
                                }));
                            }).flatMap(list -> l2.set(cacheKey, list, java.time.Duration.ofSeconds(15)).thenReturn(list))
                    );
        });
    }

    public Mono<List<QuoteDto>> quotesEx(List<String> tickers, String exchange) {
        if (tickers == null || tickers.isEmpty()) return Mono.just(List.of());
        return normalizeTickers(tickers, exchange).flatMap(norm -> {
            String symbols = String.join(",", norm);
            String path = "/v7/finance/quote?symbols=" + symbols + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String ref = "/quote/" + norm.get(0);
            return yahoo.getJson(path, ref).map(this::mapQuotes);
        });
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
        for (String t : tickers) monos.add(resolver.normalize(t));
        return Mono.zip(monos, arr -> {
            List<String> out = new ArrayList<>(arr.length);
            for (Object o : arr) out.add(String.valueOf(o));
            return out;
        });
    }
    private Mono<List<String>> normalizeTickers(List<String> tickers, String exchange) {
        if (exchange == null || exchange.isBlank()) return normalizeTickers(tickers);
        List<Mono<String>> monos = new ArrayList<>(tickers.size());
        for (String t : tickers) monos.add(resolver.normalize(t, exchange));
        return Mono.zip(monos, arr -> {
            List<String> out = new ArrayList<>(arr.length);
            for (Object o : arr) out.add(String.valueOf(o));
            return out;
        });
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
}


