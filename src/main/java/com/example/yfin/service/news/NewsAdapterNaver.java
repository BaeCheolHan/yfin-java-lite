package com.example.yfin.service.news;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class NewsAdapterNaver {
    private final WebClient googleNewsClient;

    public NewsAdapterNaver(@Qualifier("googleNewsClient") WebClient googleNewsClient) {
        this.googleNewsClient = googleNewsClient;
    }

    public reactor.core.publisher.Mono<List<Map<String, Object>>> fetch(String query, int count, String lang) {
        String lg = (lang == null || lang.isBlank()) ? "ko" : lang.substring(0,2);
        int c = (count <= 0 || count > 50) ? 10 : count;
        String q = URLEncoder.encode("site:news.naver.com " + query, StandardCharsets.UTF_8);
        String rss = "/rss/search?q=" + q + "&hl=" + lg + "&gl=" + (lg.equals("ko")?"KR":"US") + "&ceid=" + (lg.equals("ko")?"KR:ko":"US:en");
        return googleNewsClient.get().uri(rss).retrieve().bodyToMono(String.class)
                .map(com.example.yfin.service.RssParser::parse)
                .map(items -> items.size() > c ? items.subList(0, c) : items)
                .onErrorReturn(List.of());
    }
}


