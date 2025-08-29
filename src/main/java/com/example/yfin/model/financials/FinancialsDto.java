package com.example.yfin.model.financials;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "요약 재무제표(최근 값 기준) - 단일 항목 DTO")
public class FinancialsDto {
    @Schema(description = "매출(최근)" )
    private Long totalRevenue;

    @Schema(description = "영업이익(최근)")
    private Long operatingIncome;

    @Schema(description = "순이익(최근)")
    private Long netIncome;

    @Schema(description = "총자산(최근)")
    private Long totalAssets;

    @Schema(description = "총부채(최근)")
    private Long totalLiab;

    @Schema(description = "영업현금흐름(최근)")
    private Long totalCashFromOperatingActivities;
}


