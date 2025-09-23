package com.example.yfin.util;

import com.example.yfin.kis.KisTrId;
import com.example.yfin.model.ExchangeSuffix;

public final class SymbolUtils {

    private SymbolUtils() {}

    /**
     * 심볼에 기반해 KIS WS TR ID를 결정합니다.
     * - 접미사 KS/KQ 또는 숫자-only이면 국내 체결가(H0STCNT0)
     * - 그 외는 해외 체결가(HDFSCNT0)
     */
    public static KisTrId determineTransactionId(String symbol) {
        if (symbol != null) {
            int dot = symbol.indexOf('.');
            String suffix = dot > 0 && dot + 1 < symbol.length() ? symbol.substring(dot + 1).toUpperCase() : "";
            ExchangeSuffix ex = ExchangeSuffix.from(suffix);
            if (ex.isKorea()) return KisTrId.H0STCNT0;  // 국내 주식 실시간 체결가
            String left = dot > 0 ? symbol.substring(0, dot) : symbol;
            if (left.matches("\\d+")) return KisTrId.H0STCNT0;  // 숫자만 있으면 국내
        }
        return KisTrId.HDFSCNT0;  // 해외 주식 실시간 체결가
    }

    /**
     * TR 별 tr_key 정규화.
     * - 국내: 숫자 6자리까지만 유지
     * - 해외: 접미사 제거 후 대문자 변환
     */
    public static String determineTransactionKey(String symbol) {
        if (symbol == null) return "";
        int dot = symbol.indexOf('.');
        String left = dot > 0 ? symbol.substring(0, dot) : symbol;
        String suffix = dot > 0 && dot + 1 < symbol.length() ? symbol.substring(dot + 1).toUpperCase() : "";
        ExchangeSuffix ex = ExchangeSuffix.from(suffix);
        if (ex.isKorea() || left.matches("\\d+")) {
            String digits = left.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) return digits.length() >= 6 ? digits.substring(0, 6) : digits;
            return left.toUpperCase();
        }
        return left.toUpperCase();
    }
}


