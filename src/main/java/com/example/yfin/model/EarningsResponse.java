package com.example.yfin.model;

import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "실적/가이던스/일정 응답")
public class EarningsResponse {
    @Schema(description = "티커")
    private String ticker;

    @Schema(description = "earnings 모듈 원본")
    private Map<String, Object> earnings;

    @Schema(description = "earningsTrend 모듈 원본")
    private Map<String, Object> earningsTrend;

    @Schema(description = "calendarEvents 모듈 원본")
    private Map<String, Object> calendarEvents;

    @Schema(description = "DART 실적/공시 보조 정보")
    private Map<String, Object> dartAddon;
}


