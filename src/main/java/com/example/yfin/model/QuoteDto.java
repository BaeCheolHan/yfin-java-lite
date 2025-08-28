package com.example.yfin.model;

import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@Schema(description = "시세 요약 응답")
public class QuoteDto {
    @Schema(description = "티커", example = "JEPI")
    private String symbol;

    @Schema(description = "약식 명칭", example = "JPMorgan Equity Premium Income")
    private String shortName;

    @Schema(description = "통화", example = "USD")
    private String currency;

    @Schema(description = "현재가")
    private Double regularMarketPrice;

    @Schema(description = "당일 절대변화")
    private Double regularMarketChange;

    @Schema(description = "당일 변동률(소수)", example = "0.0123")
    private Double regularMarketChangePercent;

    @Schema(description = "당일 거래량")
    private Long   regularMarketVolume;

    @Schema(description = "전일 종가")
    private Double previousClose;

    @Schema(description = "당일 고가")
    private Double dayHigh;

    @Schema(description = "당일 저가")
    private Double dayLow;

    @Schema(description = "(일부 소스) 배당수익률")
    private Double dividendYield;

    @Schema(description = "52주 고가")
    private Double fiftyTwoWeekHigh;

    @Schema(description = "52주 저가")
    private Double fiftyTwoWeekLow;

    @Schema(description = "TTM 배당금 총액")
    private Double trailingAnnualDividendRate;

    @Schema(description = "TTM 배당수익률(소수)", example = "0.021")
    private Double trailingAnnualDividendYield;

    // forward (from summaryDetail)
    @Schema(description = "선행 배당금 총액")
    private Double forwardDividendRate;

    @Schema(description = "선행 배당수익률(소수)", example = "0.084")
    private Double forwardDividendYield;

    // human-readable percent
    @Schema(description = "선행 배당수익률(% 단위)", example = "8.4")
    private Double forwardDividendYieldPct;

}