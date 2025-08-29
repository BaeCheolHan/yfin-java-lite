package com.example.yfin.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "스크리너 정렬 기준")
public enum ScreenerSortBy {
    CHANGE_PERCENT,
    VOLUME;

    public static ScreenerSortBy from(String s) {
        if (s == null || s.isBlank()) return CHANGE_PERCENT;
        String t = s.trim().toUpperCase();
        if (t.equals("VOLUME")) return VOLUME;
        return CHANGE_PERCENT;
    }
}


