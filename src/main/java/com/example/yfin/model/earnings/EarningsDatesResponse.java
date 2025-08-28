package com.example.yfin.model.earnings;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "실적발표일(과거/미래) 응답")
public class EarningsDatesResponse {
    @Schema(description = "티커")
    private String ticker;

    @Schema(description = "요약 실적 정보")
    private EarningsSummary summary;
}


