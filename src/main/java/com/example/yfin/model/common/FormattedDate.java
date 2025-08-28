package com.example.yfin.model.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "에포크 초 및 포맷 문자열을 포함한 날짜 표현")
public class FormattedDate {
    @Schema(description = "에포크 초(raw). null일 수 있음")
    private Long raw;

    @Schema(description = "사람이 읽기 쉬운 날짜 포맷. 예) 2025-08-14")
    private String fmt;
}


