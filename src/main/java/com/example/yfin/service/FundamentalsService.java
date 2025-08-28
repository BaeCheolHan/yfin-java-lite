package com.example.yfin.service;

import com.example.yfin.http.YahooApiClient;
import com.example.yfin.model.EarningsResponse;
import com.example.yfin.model.FinancialsResponse;
import com.example.yfin.model.ProfileResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class FundamentalsService {
    private final YahooApiClient yahoo;
    private final TickerResolver resolver;
    private final com.example.yfin.service.cache.RedisCacheService l2;
    private final com.example.yfin.repo.ListingMetaRepository listingRepo;
    private final com.example.yfin.http.DartClient dart;

    public FundamentalsService(YahooApiClient yahoo, TickerResolver resolver, com.example.yfin.service.cache.RedisCacheService l2,
                               com.example.yfin.repo.ListingMetaRepository listingRepo,
                               com.example.yfin.http.DartClient dart) {
        this.yahoo = yahoo;
        this.resolver = resolver;
        this.l2 = l2;
        this.listingRepo = listingRepo;
        this.dart = dart;
    }

    @Cacheable(cacheNames = "financials", key = "#ticker")
    public Mono<FinancialsResponse> financials(String ticker) {
        String modules = "incomeStatementHistory,balanceSheetHistory,cashflowStatementHistory";
        return resolver.normalize(ticker).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "financials:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.FinancialsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapFinancials(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(30)).thenReturn(res))
                    );
        });
    }

    public Mono<FinancialsResponse> financialsEx(String ticker, String exchange) {
        String modules = "incomeStatementHistory,balanceSheetHistory,cashflowStatementHistory";
        return resolver.normalize(ticker, exchange).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "financials:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.FinancialsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapFinancials(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(30)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "earnings", key = "#ticker")
    public Mono<EarningsResponse> earnings(String ticker) {
        String modules = "earnings,earningsTrend,calendarEvents";
        return resolver.normalize(ticker).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "earnings:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.EarningsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapEarnings(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(30)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "earnings", key = "#ticker + ':' + #exchange")
    public Mono<EarningsResponse> earningsEx(String ticker, String exchange) {
        String modules = "earnings,earningsTrend,calendarEvents";
        return resolver.normalize(ticker, exchange).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "earnings:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.EarningsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapEarnings(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(30)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "profile", key = "#ticker")
    public Mono<ProfileResponse> profile(String ticker) {
        String modules = "summaryProfile,esgScores";
        return resolver.normalize(ticker).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "profile:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.ProfileResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapProfile(nt, m))
                                    .flatMap(res -> enrichProfileWithMetaAndDart(nt, res))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(60)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "profile", key = "#ticker + ':' + #exchange")
    public Mono<ProfileResponse> profileEx(String ticker, String exchange) {
        String modules = "summaryProfile,esgScores";
        return resolver.normalize(ticker, exchange).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "profile:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.ProfileResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapProfile(nt, m))
                                    .flatMap(res -> enrichProfileWithMetaAndDart(nt, res))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(60)).thenReturn(res))
                    );
        });
    }

    private reactor.core.publisher.Mono<com.example.yfin.model.ProfileResponse> enrichProfileWithMetaAndDart(String ticker, com.example.yfin.model.ProfileResponse res) {
        return listingRepo.findById(ticker)
                .map(doc -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("market", doc.getMarket());
                    m.put("name", doc.getName());
                    m.put("sector", doc.getSector());
                    res.setListingMeta(m);
                    return res;
                })
                .defaultIfEmpty(res)
                .flatMap(r -> {
                    // DART 요약(키 없으면 패스)
                    return dart.company("")
                            .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                            .map(map -> {
                                r.setDartSummary((java.util.Map<String, Object>) map);
                                return r;
                            })
                            .defaultIfEmpty(r);
                });
    }

    @SuppressWarnings("unchecked")
    private FinancialsResponse mapFinancials(String ticker, Map<String, Object> body) {
        FinancialsResponse res = new FinancialsResponse();
        res.setTicker(ticker);
        Map<String, Object> qs = (Map<String, Object>) body.get("quoteSummary");
        if (qs == null) return res;
        List<Map<String, Object>> result = (List<Map<String, Object>>) qs.get("result");
        if (result == null || result.isEmpty()) return res;
        Map<String, Object> r0 = result.get(0);
        res.setIncomeStatement((Map<String, Object>) r0.get("incomeStatementHistory"));
        res.setBalanceSheet((Map<String, Object>) r0.get("balanceSheetHistory"));
        res.setCashflowStatement((Map<String, Object>) r0.get("cashflowStatementHistory"));
        return res;
    }

    @SuppressWarnings("unchecked")
    private EarningsResponse mapEarnings(String ticker, Map<String, Object> body) {
        EarningsResponse res = new EarningsResponse();
        res.setTicker(ticker);
        Map<String, Object> qs = (Map<String, Object>) body.get("quoteSummary");
        if (qs == null) return res;
        List<Map<String, Object>> result = (List<Map<String, Object>>) qs.get("result");
        if (result == null || result.isEmpty()) return res;
        Map<String, Object> r0 = result.get(0);
        res.setEarnings((Map<String, Object>) r0.get("earnings"));
        res.setEarningsTrend((Map<String, Object>) r0.get("earningsTrend"));
        res.setCalendarEvents((Map<String, Object>) r0.get("calendarEvents"));
        return res;
    }

    @SuppressWarnings("unchecked")
    private ProfileResponse mapProfile(String ticker, Map<String, Object> body) {
        ProfileResponse res = new ProfileResponse();
        res.setTicker(ticker);
        Map<String, Object> qs = (Map<String, Object>) body.get("quoteSummary");
        if (qs == null) return res;
        List<Map<String, Object>> result = (List<Map<String, Object>>) qs.get("result");
        if (result == null || result.isEmpty()) return res;
        Map<String, Object> r0 = result.get(0);
        res.setSummaryProfile((Map<String, Object>) r0.get("summaryProfile"));
        res.setEsgScores((Map<String, Object>) r0.get("esgScores"));
        return res;
    }
}


