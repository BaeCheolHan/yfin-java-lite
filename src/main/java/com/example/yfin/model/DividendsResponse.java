package com.example.yfin.model;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "배당 이력 응답")
@Getter
@Setter
public class DividendsResponse {
    @Schema(description = "티커", example = "JEPI")
    private String ticker;
    @Schema(description = "조회 범위", example = "5y")
    private String range;
    @Schema(description = "배당 행 목록(오름차순)")
    private List<DivRow> rows;
}