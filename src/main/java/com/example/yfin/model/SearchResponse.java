package com.example.yfin.model;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "검색 응답")
public class SearchResponse {
    @Schema(description = "원본 quotes 리스트")
    private List<Map<String, Object>> quotes;

    @Schema(description = "원본 news 리스트")
    private List<Map<String, Object>> news;
}


