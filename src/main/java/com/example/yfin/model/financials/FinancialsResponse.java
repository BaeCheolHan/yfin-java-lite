package com.example.yfin.model.financials;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "재무제표 요약 응답")
@Getter
@Setter
public class FinancialsResponse {
    @Schema(description = "티커", example = "AAPL")
    private String ticker;

    @Schema(description = "요약 재무제표(최근 값)")
    private FinancialsDto summary;
}


