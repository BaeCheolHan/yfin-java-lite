package com.example.yfin.model.calendar;

import com.example.yfin.model.common.FormattedDate;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "실적 캘린더 요약")
public class EarningsCalendar {
    @Schema(description = "실적 발표일(추정 가능)")
    private FormattedDate earningsDate;

    @Schema(description = "실적 콜 일정")
    private FormattedDate earningsCallDate;

    @Schema(description = "실적 발표일이 추정치인지 여부")
    private Boolean earningsDateEstimate;

    @Schema(description = "EPS 컨센서스 평균")
    private Double earningsAverage;

    @Schema(description = "EPS 컨센서스 하단")
    private Double earningsLow;

    @Schema(description = "EPS 컨센서스 상단")
    private Double earningsHigh;

    @Schema(description = "매출 컨센서스 평균")
    private Long revenueAverage;

    @Schema(description = "매출 컨센서스 하단")
    private Long revenueLow;

    @Schema(description = "매출 컨센서스 상단")
    private Long revenueHigh;
}


