package com.example.yfin.service;

import com.example.yfin.model.SearchResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class SearchService {
    private final WebClient googleNewsClient;
    private final com.example.yfin.http.YahooApiClient yahoo;

    private final com.example.yfin.service.cache.RedisCacheService l2;
    private final com.example.yfin.service.news.NewsAggregator newsAgg;

    public SearchService(@Qualifier("googleNewsClient") WebClient googleNewsClient,
                         com.example.yfin.http.YahooApiClient yahoo,
                         com.example.yfin.service.cache.RedisCacheService l2,
                         com.example.yfin.service.news.NewsAggregator newsAgg) {
        this.googleNewsClient = googleNewsClient;
        this.yahoo = yahoo;
        this.l2 = l2;
        this.newsAgg = newsAgg;
    }

    @org.springframework.cache.annotation.Cacheable(cacheNames = "search", key = "#query + ':' + #count + ':' + (#lang?:'') + ':' + (#region?:'')")
    public Mono<SearchResponse> search(String query, int count, String lang, String region) {
        String q = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        int c = (count <= 0 || count > 50) ? 10 : count;
        String lower = query == null ? "" : query.toLowerCase();
        boolean guessKr = lower.matches(".*[\\p{IsHangul}].*") || lower.contains(".ks") || lower.contains(".kq");
        String lg = (lang == null || lang.isBlank()) ? (guessKr ? "ko-KR" : "en-US") : lang;
        String rg = (region == null || region.isBlank()) ? (guessKr ? "KR" : "US") : region;
        String path = "/v1/finance/search?q=" + q + "&quotesCount=" + c + "&newsCount=" + c + "&enableFuzzyQuery=true&lang=" + lg + "&region=" + rg;
        String key = "search:" + query + ":" + c + ":" + lg + ":" + rg;
        return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.SearchResponse>() {})
                .switchIfEmpty(
                        yahoo.getJson(path, "/search")
                                .map(this::mapSearch)
                                .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(15)).thenReturn(res))
                );
    }

    @org.springframework.cache.annotation.Cacheable(cacheNames = "search", key = "'google:' + #query + ':' + #count + ':' + (#lang?:'')")
    public Mono<SearchResponse> searchGoogle(String query, int count, String lang) {
        String lg = (lang == null || lang.isBlank()) ? "ko" : lang.substring(0,2);
        int c = (count <= 0 || count > 50) ? 10 : count;
        String q = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String rss = "/rss/search?q=" + q + "&hl=" + lg + "&gl=" + (lg.equals("ko")?"KR":"US") + "&ceid=" + (lg.equals("ko")?"KR:ko":"US:en");
        String key = "newsagg:" + query + ":" + c + ":" + lg;
        return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.SearchResponse>() {})
                .switchIfEmpty(
                        newsAgg.fetchAll(query, c, lg)
                                .map(items -> {
                                    SearchResponse res = new SearchResponse();
                                    res.setQuotes(List.of());
                                    res.setNews(items);
                                    return res;
                                })
                                .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(30)).thenReturn(res))
                )
                .onErrorResume(e -> {
                    SearchResponse res = new SearchResponse();
                    res.setQuotes(List.of());
                    res.setNews(List.of());
                    return Mono.just(res);
                });
    }

    @SuppressWarnings("unchecked")
    private SearchResponse mapSearch(Map<String, Object> body) {
        SearchResponse res = new SearchResponse();
        List<Map<String, Object>> quotes = (List<Map<String, Object>>) body.getOrDefault("quotes", List.of());
        List<Map<String, Object>> news = (List<Map<String, Object>>) body.getOrDefault("news", List.of());
        res.setQuotes(quotes);
        res.setNews(news);
        return res;
    }
}


