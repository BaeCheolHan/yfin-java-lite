package com.example.yfin.kis;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "KIS WebSocket TR ID")
public enum KisTrId {
    H0STCNT0, // 국내 체결
    H0UNCNT0; // 해외 체결
}


