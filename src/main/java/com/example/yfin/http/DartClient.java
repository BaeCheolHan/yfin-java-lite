package com.example.yfin.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class DartClient {
    private final WebClient dart;
    private final String apiKey;

    public DartClient(@Value("${dart.api-key:}") String apiKey) {
        this.dart = WebClient.builder().baseUrl("https://opendart.fss.or.kr/api").build();
        this.apiKey = apiKey;
    }

    public Mono<Map> company(String corpCode) {
        return dart.get().uri(uri -> uri.path("/company.json").queryParam("crtfc_key", apiKey).queryParam("corp_code", corpCode).build())
                .retrieve().bodyToMono(Map.class);
    }

    public Mono<Map> list(String corpCode, String bgnDe, String endDe, int pageNo, int pageCount) {
        return dart.get().uri(uri -> uri.path("/list.json").queryParam("crtfc_key", apiKey)
                .queryParam("corp_code", corpCode).queryParam("bgn_de", bgnDe).queryParam("end_de", endDe)
                .queryParam("page_no", pageNo).queryParam("page_count", pageCount).build())
                .retrieve().bodyToMono(Map.class);
    }
}


