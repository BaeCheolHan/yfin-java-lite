package com.example.yfin.model.indicators;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "이동평균 포인트")
public class MaPoint {
    @Schema(description = "시각(UTC)")
    private Instant time;
    @Schema(description = "이동평균 값")
    private Double value;

    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }
}


