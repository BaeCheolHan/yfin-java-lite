package com.example.yfin.model.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보유 포지션 입력")
public class PositionDto {
    @Schema(description = "티커", example = "AAPL")
    private String symbol;
    @Schema(description = "수량", example = "10")
    private Double quantity;
    @Schema(description = "평균 매입단가", example = "190.5")
    private Double averageCost;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public Double getAverageCost() { return averageCost; }
    public void setAverageCost(Double averageCost) { this.averageCost = averageCost; }
}


