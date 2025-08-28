package com.example.yfin.model.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "ESG 점수 요약")
public class EsgScoresDto {
    @Schema(description = "총합 ESG 점수")
    private Double totalEsg;

    @Schema(description = "환경 점수")
    private Double environmentalScore;

    @Schema(description = "사회 점수")
    private Double socialScore;

    @Schema(description = "지배구조 점수")
    private Double governanceScore;
}


