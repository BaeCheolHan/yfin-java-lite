package com.example.yfin.model;

import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "재무제표 요약 응답")
@Getter
@Setter
public class FinancialsResponse {
    @Schema(description = "티커", example = "AAPL")
    private String ticker;
    @Schema(description = "손익계산서 모듈 원본")
    private Map<String, Object> incomeStatement;
    @Schema(description = "대차대조표 모듈 원본")
    private Map<String, Object> balanceSheet;
    @Schema(description = "현금흐름표 모듈 원본")
    private Map<String, Object> cashflowStatement;

}


