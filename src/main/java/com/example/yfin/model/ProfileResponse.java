package com.example.yfin.model;

import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "기업 개요/ESG 응답")
public class ProfileResponse {
    @Schema(description = "티커")
    private String ticker;

    @Schema(description = "summaryProfile 모듈 원본")
    private Map<String, Object> summaryProfile;

    @Schema(description = "esgScores 모듈 원본")
    private Map<String, Object> esgScores;

    @Schema(description = "상장 메타(시장/섹터/종목명 등)")
    private Map<String, Object> listingMeta;

    @Schema(description = "DART 기업개요/최근공시 요약")
    private Map<String, Object> dartSummary;
}


