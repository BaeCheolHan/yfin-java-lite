package com.example.yfin.model.earnings;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "실적 트렌드(요약)")
public class EarningsTrendDto {
    @Schema(description = "다음 분기 EPS 예상치")
    private Double nextQuarterEpsEstimate;

    @Schema(description = "올해 EPS 예상치")
    private Double currentYearEpsEstimate;

    @Schema(description = "내년 EPS 예상치")
    private Double nextYearEpsEstimate;
}


