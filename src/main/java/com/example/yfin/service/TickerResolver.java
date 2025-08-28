package com.example.yfin.service;

import com.example.yfin.http.YahooApiClient;
import com.example.yfin.model.doc.ListingMetaDoc;
import com.example.yfin.repo.ListingMetaRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class TickerResolver {

    private final YahooApiClient yahoo;
    private final ListingMetaRepository listingRepo;

    public TickerResolver(YahooApiClient yahoo, ListingMetaRepository listingRepo) {
        this.yahoo = yahoo;
        this.listingRepo = listingRepo;
    }

    @Cacheable(cacheNames = "resolve", key = "#raw")
    public reactor.core.publisher.Mono<String> normalize(String raw) {
        if (raw == null) return reactor.core.publisher.Mono.just("");
        String t = raw.trim().toUpperCase();
        if (t.contains(".") || t.startsWith("^")) return reactor.core.publisher.Mono.just(t);
        boolean maybeKr = t.matches("\\d{6}");
        if (maybeKr) {
            // 우선 로컬 메타에서 조회
            return listingRepo.findByBaseCode(t)
                    .map(ListingMetaDoc::getId)
                    .switchIfEmpty(fetchFromYahooAndFallback(t, true));
        }
        String q = URLEncoder.encode(t, StandardCharsets.UTF_8);
        String path = "/v1/finance/search?q=" + q + "&quotesCount=5&newsCount=0&enableFuzzyQuery=false&lang=en-US&region=US";
        return yahoo.getJson(path, "/search").map(body -> {
            Object quotesObj = body.get("quotes");
            if (!(quotesObj instanceof List<?> list) || list.isEmpty()) {
                return maybeKr ? (t + ".KS") : t;
            }
            String best = null;
            for (Object o : list) {
                if (!(o instanceof Map)) continue;
                Map<?,?> m = (Map<?,?>) o;
                Object sym = m.get("symbol");
                Object qtype = m.get("quoteType");
                if (sym instanceof String s) {
                    if (qtype == null || "EQUITY".equalsIgnoreCase(String.valueOf(qtype))) {
                        if (s.startsWith(t + ".KS") || s.startsWith(t + ".KQ")) { best = s; break; }
                        if (s.startsWith(t + ".")) { best = (best == null) ? s : best; }
                        if (best == null) best = s;
                    }
                }
            }
            if (best == null) best = t;
            if (maybeKr && !best.contains(".")) best = t + ".KS";
            return best;
        });
    }

    private reactor.core.publisher.Mono<String> fetchFromYahooAndFallback(String t, boolean maybeKr) {
        String q = URLEncoder.encode(t, StandardCharsets.UTF_8);
        String path = "/v1/finance/search?q=" + q + "&quotesCount=5&newsCount=0&enableFuzzyQuery=false&lang=en-US&region=US";
        return yahoo.getJson(path, "/search").map(body -> {
            Object quotesObj = body.get("quotes");
            if (!(quotesObj instanceof List<?> list) || list.isEmpty()) {
                return maybeKr ? (t + ".KS") : t;
            }
            String best = null;
            for (Object o : list) {
                if (!(o instanceof Map)) continue;
                Map<?,?> m = (Map<?,?>) o;
                Object sym = m.get("symbol");
                Object qtype = m.get("quoteType");
                if (sym instanceof String s) {
                    if (qtype == null || "EQUITY".equalsIgnoreCase(String.valueOf(qtype))) {
                        if (s.startsWith(t + ".KS") || s.startsWith(t + ".KQ")) { best = s; break; }
                        if (s.startsWith(t + ".")) { best = (best == null) ? s : best; }
                        if (best == null) best = s;
                    }
                }
            }
            if (best == null) best = t;
            if (maybeKr && !best.contains(".")) best = t + ".KS";
            return best;
        });
    }

    @Cacheable(cacheNames = "resolve", key = "#raw + ':' + #exchange")
    public reactor.core.publisher.Mono<String> normalize(String raw, String exchange) {
        if (raw == null) return reactor.core.publisher.Mono.just("");
        String t = raw.trim().toUpperCase();
        if (t.contains(".") || t.startsWith("^")) return reactor.core.publisher.Mono.just(t);
        if (exchange == null || exchange.isBlank()) return normalize(t);
        String ex = exchange.trim().toUpperCase();
        if (ex.startsWith(".")) ex = ex.substring(1);
        return reactor.core.publisher.Mono.just(t + "." + ex);
    }
}


