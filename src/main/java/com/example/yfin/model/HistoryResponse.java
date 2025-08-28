package com.example.yfin.model;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "과거 시세 응답")
@Getter
@Setter
public class HistoryResponse {
    @Schema(description = "티커", example = "JEPI")
    private String ticker;
    @Schema(description = "요청 범위", example = "1mo")
    private String range;
    @Schema(description = "간격", example = "1d")
    private String interval;
    @Schema(description = "시계열 데이터")
    private List<HistoryRow> rows;
}