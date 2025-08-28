package com.example.yfin.model;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "배당 행")
@Getter
@Setter
public class DivRow {
    @Schema(description = "지급일(UTC)")
    private Instant date;
    @Schema(description = "배당금(주당)")
    private Double amount;

}