package com.example.yfin.kis;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "KIS WebSocket TR ID")
public enum KisTrId {
    H0STCNT0, // 국내 주식 실시간 체결가
    H0GSCNI0, // 국내 주식 실시간 시세
    HDFSCNT0, // 해외 주식 실시간 체결가
    HDFSASP0; // 해외 주식 실시간 시세
}


