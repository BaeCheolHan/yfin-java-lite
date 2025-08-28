package com.example.yfin.model.earnings;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "분기별 실적(예상/실적)")
public class QuarterlyEarnings {
    @Schema(description = "분기 표기. 예) 3Q2024")
    private String date;

    @Schema(description = "EPS 실적 값")
    private Double actual;

    @Schema(description = "EPS 예상 값")
    private Double estimate;
}


