package com.example.yfin.service;

import com.example.yfin.http.YahooApiClient;
import com.example.yfin.model.HistoryResponse;
import com.example.yfin.model.HistoryRow;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChartService {
    private final YahooApiClient yahoo;
    private final TickerResolver resolver;
    private final com.example.yfin.service.cache.RedisCacheService l2;

    public ChartService(YahooApiClient yahoo, TickerResolver resolver, com.example.yfin.service.cache.RedisCacheService l2) {
        this.yahoo = yahoo;
        this.resolver = resolver;
        this.l2 = l2;
    }

    @Cacheable(cacheNames = "history", key = "#ticker + ':' + #range + ':' + #interval + ':' + #autoAdjust")
    public Mono<HistoryResponse> history(String ticker, String range, String interval, boolean autoAdjust) {
        return resolver.normalize(ticker).flatMap(nt -> {
            String path = "/v8/finance/chart/" + nt +
                    "?range=" + range + "&interval=" + interval +
                    "&events=div%2Csplits&includePrePost=false&useYfid=true&autoAdj=" + autoAdjust +
                    "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "history:" + nt + ":" + range + ":" + interval + ":" + autoAdjust;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.HistoryResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapHistory(nt, range, interval, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(20)).thenReturn(res))
                    );
        });
    }

    public Mono<HistoryResponse> historyEx(String ticker, String range, String interval, boolean autoAdjust, String exchange) {
        return resolver.normalize(ticker, exchange).flatMap(nt -> {
            String path = "/v8/finance/chart/" + nt +
                    "?range=" + range + "&interval=" + interval +
                    "&events=div%2Csplits&includePrePost=false&useYfid=true&autoAdj=" + autoAdjust +
                    "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "history:" + nt + ":" + range + ":" + interval + ":" + autoAdjust;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.HistoryResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapHistory(nt, range, interval, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(20)).thenReturn(res))
                    );
        });
    }

    @SuppressWarnings("unchecked")
    private HistoryResponse mapHistory(String ticker, String range, String interval, Map<String, Object> body) {
        HistoryResponse res = new HistoryResponse();
        res.setTicker(ticker);
        res.setRange(range);
        res.setInterval(interval);

        Map<String, Object> chart = (Map<String, Object>) body.get("chart");
        if (chart == null) { res.setRows(List.of()); return res; }
        List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");
        if (results == null || results.isEmpty()) { res.setRows(List.of()); return res; }
        Map<String, Object> r = results.get(0);

        List<Long> ts = ((List<Object>) r.getOrDefault("timestamp", List.of()))
                .stream().map(o -> ((Number) o).longValue()).collect(Collectors.toList());

        Map<String, Object> indicators = (Map<String, Object>) r.get("indicators");
        List<Map<String, Object>> qList = indicators == null ? null : (List<Map<String, Object>>) indicators.get("quote");
        Map<String, Object> q = (qList == null || qList.isEmpty()) ? null : qList.get(0);

        if (q == null || ts.isEmpty()) { res.setRows(List.of()); return res; }

        List<Double> opens  = dList(q.get("open"));
        List<Double> highs  = dList(q.get("high"));
        List<Double> lows   = dList(q.get("low"));
        List<Double> closes = dList(q.get("close"));
        List<Long> volumes  = lList(q.get("volume"));

        List<HistoryRow> rows = new ArrayList<>(ts.size());
        for (int i = 0; i < ts.size(); i++) {
            HistoryRow row = new HistoryRow();
            row.setTime(Instant.ofEpochSecond(ts.get(i)));
            row.setOpen(get(opens, i));
            row.setHigh(get(highs, i));
            row.setLow(get(lows, i));
            row.setClose(get(closes, i));
            row.setVolume(get(volumes, i));
            rows.add(row);
        }
        res.setRows(rows);
        return res;
    }

    @SuppressWarnings("unchecked")
    private static List<Double> dList(Object o) {
        if (o == null) return List.of();
        List<Object> src = (List<Object>) o;
        List<Double> out = new ArrayList<>(src.size());
        for (Object v : src) out.add(v == null ? null : ((Number) v).doubleValue());
        return out;
    }
    @SuppressWarnings("unchecked")
    private static List<Long> lList(Object o) {
        if (o == null) return List.of();
        List<Object> src = (List<Object>) o;
        List<Long> out = new ArrayList<>(src.size());
        for (Object v : src) out.add(v == null ? null : ((Number) v).longValue());
        return out;
    }
    private static <T> T get(List<T> list, int i) { return (i < list.size()) ? list.get(i) : null; }
}


