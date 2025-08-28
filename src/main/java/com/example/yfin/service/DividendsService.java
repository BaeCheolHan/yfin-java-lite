package com.example.yfin.service;

import com.example.yfin.http.YahooApiClient;
import com.example.yfin.model.DividendsResponse;
import com.example.yfin.model.DivRow;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DividendsService {
    private final YahooApiClient yahoo;
    private final TickerResolver resolver;
    private final com.example.yfin.service.cache.RedisCacheService l2;

    public DividendsService(YahooApiClient yahoo, TickerResolver resolver, com.example.yfin.service.cache.RedisCacheService l2) {
        this.yahoo = yahoo;
        this.resolver = resolver;
        this.l2 = l2;
    }

    @Cacheable(cacheNames = "dividends", key = "#ticker + ':' + #range")
    public Mono<DividendsResponse> dividends(String ticker, String range) {
        String r = (range == null || range.isBlank()) ? "5y" : range;
        return resolver.normalize(ticker).flatMap(nt -> {
            String path = "/v8/finance/chart/" + nt +
                    "?range=" + r + "&interval=1d&events=div%2Csplits&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "dividends:" + nt + ":" + r;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.DividendsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapDividends(nt, r, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(20)).thenReturn(res))
                    );
        });
    }

    public Mono<DividendsResponse> dividendsEx(String ticker, String range, String exchange) {
        String r = (range == null || range.isBlank()) ? "5y" : range;
        return resolver.normalize(ticker, exchange).flatMap(nt -> {
            String path = "/v8/finance/chart/" + nt +
                    "?range=" + r + "&interval=1d&events=div%2Csplits&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "dividends:" + nt + ":" + r;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.DividendsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapDividends(nt, r, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(20)).thenReturn(res))
                    );
        });
    }

    @SuppressWarnings("unchecked")
    private DividendsResponse mapDividends(String ticker, String range, Map<String, Object> body) {
        DividendsResponse res = new DividendsResponse();
        res.setTicker(ticker);
        res.setRange(range);

        Map<String, Object> chart = (Map<String, Object>) body.get("chart");
        if (chart == null) { res.setRows(List.of()); return res; }
        List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");
        if (results == null || results.isEmpty()) { res.setRows(List.of()); return res; }
        Map<String, Object> r = results.get(0);

        Map<String, Object> events = (Map<String, Object>) r.get("events");
        if (events == null) { res.setRows(List.of()); return res; }
        Map<String, Map<String, Object>> div = (Map<String, Map<String, Object>>) events.get("dividends");
        if (div == null || div.isEmpty()) { res.setRows(List.of()); return res; }

        List<DivRow> rows = div.values().stream().map(m -> {
            DivRow d = new DivRow();
            d.setDate(Instant.ofEpochSecond(((Number) m.get("date")).longValue()));
            d.setAmount(d(m.get("amount")));
            return d;
        }).sorted(Comparator.comparing(DivRow::getDate)).collect(Collectors.toList());

        res.setRows(rows);
        return res;
    }

    private static Double d(Object o) { return o == null ? null : ((Number) o).doubleValue(); }
}


