package com.example.yfin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("yfin-java-lite API")
                        .version("0.1.0")
                        .description("Yahoo/KIS/Finnhub 기반 시세/차트 API 및 실시간 WebSocket 안내"));
    }

    @Bean
    public OpenApiCustomizer websocketDocs() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths == null) {
                paths = new Paths();
                openApi.setPaths(paths);
            }

            // /ws/quotes 문서를 OpenAPI에 서술적으로 추가(WS 실제 업그레이드 경로)
            Operation op = new Operation()
                    .summary("Realtime WebSocket for quotes")
                    .description("KIS 우선 → Finnhub 폴백. 보강 스냅샷 병합. 응답 예: {\"symbol\":\"AAPL\",\"price\":230.49,\"dp\":0.51}");

            op.addParametersItem(new Parameter()
                    .name("tickers").in("query").required(true)
                    .description("쉼표 구분 멀티 심볼. 한국 6자리 티커는 자동으로 .KS/.KQ 보정")
                    .schema(new Schema<String>().type("string")));

            op.addParametersItem(new Parameter()
                    .name("intervalSec").in("query").required(false)
                    .description("보강 스냅샷 최소 간격(기본 2). KIS 경로는 1초까지 허용")
                    .schema(new Schema<Integer>().type("integer").format("int32")));

            ApiResponses responses = new ApiResponses();
            ApiResponse r200 = new ApiResponse()
                    .description("Upgrades to WebSocket; thereafter text frames with JSON quotes");
            r200.setContent(new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().type("object"))));
            responses.addApiResponse("200", r200);
            op.setResponses(responses);

            PathItem item = new PathItem().get(op);
            paths.addPathItem("/ws/quotes", item);
        };
    }
}


