package com.example.yfin.model;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "옵션 체인 행")
public class OptionRow {
    @Schema(description = "만기(UTC)")
    private Instant expiration;
    @Schema(description = "종류 CALL/PUT")
    private OptionType type;
    @Schema(description = "행사가")
    private Double strike;
    @Schema(description = "최종 체결가")
    private Double lastPrice;
    @Schema(description = "호가 Bid")
    private Double bid;
    @Schema(description = "호가 Ask")
    private Double ask;
    @Schema(description = "거래량")
    private Long volume;
    @Schema(description = "미결제약정")
    private Long openInterest;
    @Schema(description = "내재변동성")
    private Double impliedVolatility;

    public Instant getExpiration() { return expiration; }
    public void setExpiration(Instant expiration) { this.expiration = expiration; }
    public OptionType getType() { return type; }
    public void setType(OptionType type) { this.type = type; }
    public Double getStrike() { return strike; }
    public void setStrike(Double strike) { this.strike = strike; }
    public Double getLastPrice() { return lastPrice; }
    public void setLastPrice(Double lastPrice) { this.lastPrice = lastPrice; }
    public Double getBid() { return bid; }
    public void setBid(Double bid) { this.bid = bid; }
    public Double getAsk() { return ask; }
    public void setAsk(Double ask) { this.ask = ask; }
    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }
    public Long getOpenInterest() { return openInterest; }
    public void setOpenInterest(Long openInterest) { this.openInterest = openInterest; }
    public Double getImpliedVolatility() { return impliedVolatility; }
    public void setImpliedVolatility(Double impliedVolatility) { this.impliedVolatility = impliedVolatility; }
}


