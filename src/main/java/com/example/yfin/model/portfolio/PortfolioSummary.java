package com.example.yfin.model.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "포트폴리오 요약")
public class PortfolioSummary {
    @Schema(description = "평가금액")
    private Double marketValue;
    @Schema(description = "투입원금")
    private Double cost;
    @Schema(description = "평가손익")
    private Double pnl;
    @Schema(description = "평가수익률(소수)")
    private Double pnlRate;
    @Schema(description = "추정 연간 배당금(TTM/선행) 합")
    private Double dividendAnnual;
    @Schema(description = "추정 배당수익률(소수)")
    private Double dividendYield;
    @Schema(description = "개별 항목 계산 값")
    private List<PositionBreakdown> items;

    public Double getMarketValue() { return marketValue; }
    public void setMarketValue(Double marketValue) { this.marketValue = marketValue; }
    public Double getCost() { return cost; }
    public void setCost(Double cost) { this.cost = cost; }
    public Double getPnl() { return pnl; }
    public void setPnl(Double pnl) { this.pnl = pnl; }
    public Double getPnlRate() { return pnlRate; }
    public void setPnlRate(Double pnlRate) { this.pnlRate = pnlRate; }
    public Double getDividendAnnual() { return dividendAnnual; }
    public void setDividendAnnual(Double dividendAnnual) { this.dividendAnnual = dividendAnnual; }
    public Double getDividendYield() { return dividendYield; }
    public void setDividendYield(Double dividendYield) { this.dividendYield = dividendYield; }
    public List<PositionBreakdown> getItems() { return items; }
    public void setItems(List<PositionBreakdown> items) { this.items = items; }

    @Schema(description = "개별 포지션 요약")
    public static class PositionBreakdown {
        private String symbol;
        private Double quantity;
        private Double price;
        private Double marketValue;
        private Double cost;
        private Double pnl;
        private Double pnlRate;
        private Double dividendAnnual;
        private Double dividendYield;

        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public Double getQuantity() { return quantity; }
        public void setQuantity(Double quantity) { this.quantity = quantity; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        public Double getMarketValue() { return marketValue; }
        public void setMarketValue(Double marketValue) { this.marketValue = marketValue; }
        public Double getCost() { return cost; }
        public void setCost(Double cost) { this.cost = cost; }
        public Double getPnl() { return pnl; }
        public void setPnl(Double pnl) { this.pnl = pnl; }
        public Double getPnlRate() { return pnlRate; }
        public void setPnlRate(Double pnlRate) { this.pnlRate = pnlRate; }
        public Double getDividendAnnual() { return dividendAnnual; }
        public void setDividendAnnual(Double dividendAnnual) { this.dividendAnnual = dividendAnnual; }
        public Double getDividendYield() { return dividendYield; }
        public void setDividendYield(Double dividendYield) { this.dividendYield = dividendYield; }
    }
}


