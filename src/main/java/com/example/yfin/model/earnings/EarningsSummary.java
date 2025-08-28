package com.example.yfin.model.earnings;

import com.example.yfin.model.common.FormattedDate;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "요약 실적 정보(차트/다음 실적일 포함)")
public class EarningsSummary {
    @Schema(description = "분기별 실적 이력")
    private List<QuarterlyEarnings> quarterly;

    @Schema(description = "현재 분기 EPS 예상치")
    private Double currentQuarterEstimate;

    @Schema(description = "현재 분기(예: 3Q)")
    private String currentQuarterEstimateDate;

    @Schema(description = "현재 분기 연도")
    private Integer currentQuarterEstimateYear;

    @Schema(description = "다음 실적 발표일(추정 가능)")
    private FormattedDate nextEarningsDate;
}


