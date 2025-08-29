package com.example.yfin.service;

import com.example.yfin.http.YahooApiClient;
import com.example.yfin.model.OptionRow;
import com.example.yfin.model.OptionsResponse;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OptionsService {
    private final YahooApiClient yahooApiClient;
    private final TickerResolver tickerResolver;
    private final com.example.yfin.service.cache.RedisCacheService level2Cache;

    

    @Cacheable(cacheNames = "options", key = "#ticker + ':' + (#expiration == null ? 'nearest' : #expiration)")
    public Mono<OptionsResponse> options(String ticker, String expiration) {
        Long epoch = parseExpirationToEpoch(expiration);
        return tickerResolver.normalize(ticker).flatMap(nt -> {
            String referer = "/quote/" + nt + "/options";
            String path = "/v7/finance/options/" + nt;
            if (epoch != null) {
                path += "?date=" + epoch + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            } else {
                path += "?lang=en-US&region=US&corsDomain=finance.yahoo.com";
            }
            String key = "options:" + nt + ":" + (epoch == null ? "nearest" : epoch);
            return level2Cache.get(key, new com.fasterxml.jackson.core.type.TypeReference<OptionsResponse>() {})
                    .switchIfEmpty(
                            yahooApiClient.getJson(path, referer)
                                    .map(m -> mapOptions(nt, epoch, m))
                                    .flatMap(res -> level2Cache.set(key, res, java.time.Duration.ofSeconds(20)).thenReturn(res))
                    );
        });
    }

    public Mono<OptionsResponse> optionsEx(String ticker, String expiration, String exchange) {
        Long epoch = parseExpirationToEpoch(expiration);
        return tickerResolver.normalize(ticker, exchange).flatMap(nt -> {
            String referer = "/quote/" + nt + "/options";
            String path = "/v7/finance/options/" + nt;
            if (epoch != null) {
                path += "?date=" + epoch + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            } else {
                path += "?lang=en-US&region=US&corsDomain=finance.yahoo.com";
            }
            String key = "options:" + nt + ":" + (epoch == null ? "nearest" : epoch);
            return level2Cache.get(key, new com.fasterxml.jackson.core.type.TypeReference<OptionsResponse>() {})
                    .switchIfEmpty(
                            yahooApiClient.getJson(path, referer)
                                    .map(m -> mapOptions(nt, epoch, m))
                                    .flatMap(res -> level2Cache.set(key, res, java.time.Duration.ofSeconds(20)).thenReturn(res))
                    );
        });
    }

    private OptionsResponse mapOptions(String ticker, Long epoch, Map<String, Object> body) {
        OptionsResponse res = new OptionsResponse();
        res.setTicker(ticker);
        res.setExpiration(epoch == null ? "nearest" : String.valueOf(epoch));

        Map<String, Object> optionChain = asMap(body.get("optionChain"));
        if (optionChain == null) { res.setCalls(List.of()); res.setPuts(List.of()); return res; }
        List<Map<String, Object>> result = asListOfMap(optionChain.get("result"));
        if (result == null || result.isEmpty()) { res.setCalls(List.of()); res.setPuts(List.of()); return res; }
        Map<String, Object> r0 = result.get(0);
        List<Map<String, Object>> options = asListOfMap(r0.get("options"));
        if (options == null || options.isEmpty()) { res.setCalls(List.of()); res.setPuts(List.of()); return res; }
        Map<String, Object> o0 = options.get(0);
        List<Map<String, Object>> calls = asListOfMap(o0.get("calls"));
        if (calls == null) calls = List.of();
        List<Map<String, Object>> puts  = asListOfMap(o0.get("puts"));
        if (puts == null) puts = List.of();

        List<OptionRow> callRows = new ArrayList<>(calls.size());
        for (Map<String, Object> m : calls) callRows.add(mapOptionRow(m, "CALL", o0));
        List<OptionRow> putRows = new ArrayList<>(puts.size());
        for (Map<String, Object> m : puts) putRows.add(mapOptionRow(m, "PUT", o0));

        res.setCalls(callRows);
        res.setPuts(putRows);
        return res;
    }

    private OptionRow mapOptionRow(Map<String, Object> m, String type, Map<String, Object> o0) {
        OptionRow row = new OptionRow();
        Object exp = m.get("expiration");
        if (exp instanceof Number n) row.setExpiration(Instant.ofEpochSecond(n.longValue()));
        else if (o0 != null && o0.get("expirationDate") instanceof Number n2) row.setExpiration(Instant.ofEpochSecond(n2.longValue()));
        row.setType(type);
        row.setStrike(d(m.get("strike")));
        row.setLastPrice(d(m.get("lastPrice")));
        row.setBid(d(m.get("bid")));
        row.setAsk(d(m.get("ask")));
        row.setVolume(l(m.get("volume")));
        row.setOpenInterest(l(m.get("openInterest")));
        row.setImpliedVolatility(d(m.get("impliedVolatility")));
        return row;
    }

    private static Long parseExpirationToEpoch(String expiration) {
        if (expiration == null || expiration.isBlank()) return null;
        try { return Long.parseLong(expiration); }
        catch (NumberFormatException ignore) { return null; }
    }
    private static Double d(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        Map<String, Object> m = asMap(o);
        if (m != null) return d(m.get("raw"));
        return null;
    }
    private static Long   l(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        Map<String, Object> m = asMap(o);
        if (m != null) return l(m.get("raw"));
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
}


