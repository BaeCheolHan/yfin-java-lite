package com.example.yfin.model.earnings;

import com.example.yfin.model.calendar.CalendarResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "실적/가이던스/일정 응답")
public class EarningsResponse {
    @Schema(description = "티커")
    private String ticker;

    @Schema(description = "요약 실적 차트/다음 실적일 등 (단일 항목)")
    private EarningsSummary summary;

    @Schema(description = "실적 트렌드 요약 (단일 항목, 상세는 리스트가 아님)")
    private EarningsTrendDto trend;

    @Schema(description = "캘린더/이벤트 요약 (CalendarResponse 내 컬렉션 필드 포함)")
    private CalendarResponse calendar;

    @Schema(description = "DART 실적/공시 보조 정보")
    private java.util.Map<String, Object> dartAddon;
}


