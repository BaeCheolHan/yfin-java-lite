package com.example.yfin.model.calendar;

import com.example.yfin.model.common.FormattedDate;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "캘린더/이벤트 응답")
public class CalendarResponse {
    @Schema(description = "티커")
    private String ticker;

    @Schema(description = "실적 관련 일정 정보")
    private EarningsCalendar earnings;

    @Schema(description = "배당락일")
    private FormattedDate exDividendDate;

    @Schema(description = "배당 지급일")
    private FormattedDate dividendDate;
}


