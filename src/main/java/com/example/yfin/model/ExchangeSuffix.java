package com.example.yfin.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래소 접미사")
public enum ExchangeSuffix {
    KS, KQ, NASDAQ, NYSE, AMEX, OTHER;

    public static ExchangeSuffix from(String suffix) {
        if (suffix == null) return OTHER;
        String s = suffix.trim().toUpperCase();
        for (ExchangeSuffix v : values()) if (v.name().equals(s)) return v;
        return OTHER;
    }

    public boolean isKorea() { return this == KS || this == KQ; }
}


