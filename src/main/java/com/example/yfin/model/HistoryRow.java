package com.example.yfin.model;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "OHLCV 행")
@Getter
@Setter
public class HistoryRow {
    @Schema(description = "시각(UTC)")
    private Instant time;
    @Schema(description = "시가")
    private Double open;
    @Schema(description = "고가")
    private Double high;
    @Schema(description = "저가")
    private Double low;
    @Schema(description = "종가")
    private Double close;
    @Schema(description = "거래량")
    private Long volume;
}