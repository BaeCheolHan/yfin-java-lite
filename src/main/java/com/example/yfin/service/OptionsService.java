package com.example.yfin.service;

import com.example.yfin.http.YahooApiClient;
import com.example.yfin.model.OptionRow;
import com.example.yfin.model.OptionsResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OptionsService {
    private final YahooApiClient yahoo;
    private final TickerResolver resolver;
    private final com.example.yfin.service.cache.RedisCacheService l2;

    public OptionsService(YahooApiClient yahoo, TickerResolver resolver, com.example.yfin.service.cache.RedisCacheService l2) {
        this.yahoo = yahoo;
        this.resolver = resolver;
        this.l2 = l2;
    }

    @Cacheable(cacheNames = "options", key = "#ticker + ':' + (#expiration == null ? 'nearest' : #expiration)")
    public Mono<OptionsResponse> options(String ticker, String expiration) {
        Long epoch = parseExpirationToEpoch(expiration);
        return resolver.normalize(ticker).flatMap(nt -> {
            String referer = "/quote/" + nt + "/options";
            String path = "/v7/finance/options/" + nt;
            if (epoch != null) {
                path += "?date=" + epoch + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            } else {
                path += "?lang=en-US&region=US&corsDomain=finance.yahoo.com";
            }
            String key = "options:" + nt + ":" + (epoch == null ? "nearest" : epoch);
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<OptionsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, referer)
                                    .map(m -> mapOptions(nt, epoch, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(20)).thenReturn(res))
                    );
        });
    }

    public Mono<OptionsResponse> optionsEx(String ticker, String expiration, String exchange) {
        Long epoch = parseExpirationToEpoch(expiration);
        return resolver.normalize(ticker, exchange).flatMap(nt -> {
            String referer = "/quote/" + nt + "/options";
            String path = "/v7/finance/options/" + nt;
            if (epoch != null) {
                path += "?date=" + epoch + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            } else {
                path += "?lang=en-US&region=US&corsDomain=finance.yahoo.com";
            }
            String key = "options:" + nt + ":" + (epoch == null ? "nearest" : epoch);
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<OptionsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, referer)
                                    .map(m -> mapOptions(nt, epoch, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(20)).thenReturn(res))
                    );
        });
    }

    @SuppressWarnings("unchecked")
    private OptionsResponse mapOptions(String ticker, Long epoch, Map<String, Object> body) {
        OptionsResponse res = new OptionsResponse();
        res.setTicker(ticker);
        res.setExpiration(epoch == null ? "nearest" : String.valueOf(epoch));

        Map<String, Object> optionChain = (Map<String, Object>) body.get("optionChain");
        if (optionChain == null) { res.setCalls(List.of()); res.setPuts(List.of()); return res; }
        List<Map<String, Object>> result = (List<Map<String, Object>>) optionChain.get("result");
        if (result == null || result.isEmpty()) { res.setCalls(List.of()); res.setPuts(List.of()); return res; }
        Map<String, Object> r0 = result.get(0);
        List<Map<String, Object>> options = (List<Map<String, Object>>) r0.get("options");
        if (options == null || options.isEmpty()) { res.setCalls(List.of()); res.setPuts(List.of()); return res; }
        Map<String, Object> o0 = options.get(0);
        List<Map<String, Object>> calls = (List<Map<String, Object>>) o0.getOrDefault("calls", List.of());
        List<Map<String, Object>> puts  = (List<Map<String, Object>>) o0.getOrDefault("puts", List.of());

        List<OptionRow> callRows = new ArrayList<>(calls.size());
        for (Map<String, Object> m : calls) callRows.add(mapOptionRow(m, "CALL", o0));
        List<OptionRow> putRows = new ArrayList<>(puts.size());
        for (Map<String, Object> m : puts) putRows.add(mapOptionRow(m, "PUT", o0));

        res.setCalls(callRows);
        res.setPuts(putRows);
        return res;
    }

    @SuppressWarnings("unchecked")
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
    private static Double d(Object o) { return o == null ? null : ((Number) o).doubleValue(); }
    private static Long   l(Object o) { return o == null ? null : ((Number) o).longValue(); }
}


