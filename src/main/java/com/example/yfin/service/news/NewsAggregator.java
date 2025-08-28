package com.example.yfin.service.news;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class NewsAggregator {
    private final NewsAdapterGoogle google;
    private final NewsAdapterNaver naver;
    private final NewsAdapterYonhap yonhap;
    private final NewsAdapterMaeil mk;

    public NewsAggregator(NewsAdapterGoogle google, NewsAdapterNaver naver, NewsAdapterYonhap yonhap, NewsAdapterMaeil mk) {
        this.google = google;
        this.naver = naver;
        this.yonhap = yonhap;
        this.mk = mk;
    }

    public Mono<List<Map<String, Object>>> fetchAll(String q, int count, String lang) {
        List<Mono<List<Map<String, Object>>>> calls = new ArrayList<>();
        calls.add(google.fetch(q, count, lang));
        calls.add(naver.fetch(q, count, lang));
        calls.add(yonhap.fetch(q, count, lang));
        calls.add(mk.fetch(q, count, lang));
        return Mono.zip(calls, arr -> {
            List<Map<String, Object>> merged = new ArrayList<>();
            for (Object o : arr) merged.addAll((List<Map<String, Object>>) o);
            return merged;
        });
    }
}


