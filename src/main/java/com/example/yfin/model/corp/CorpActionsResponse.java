package com.example.yfin.model.corp;

import com.example.yfin.model.DivRow;
import com.example.yfin.model.common.FormattedDate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "기업행위 응답")
public class CorpActionsResponse {
    @Schema(description = "티커")
    private String ticker;
    @Schema(description = "예정 배당락일")
    private FormattedDate exDividendDate;
    @Schema(description = "예정 배당 지급일")
    private FormattedDate dividendDate;
    @Schema(description = "최근 배당 이력")
    private List<DivRow> recentDividends;
    @Schema(description = "최근 분할 이력(yyyy-MM-dd: ratio 텍스트)")
    private List<String> recentSplits;

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public FormattedDate getExDividendDate() { return exDividendDate; }
    public void setExDividendDate(FormattedDate exDividendDate) { this.exDividendDate = exDividendDate; }
    public FormattedDate getDividendDate() { return dividendDate; }
    public void setDividendDate(FormattedDate dividendDate) { this.dividendDate = dividendDate; }
    public List<DivRow> getRecentDividends() { return recentDividends; }
    public void setRecentDividends(List<DivRow> recentDividends) { this.recentDividends = recentDividends; }
    public List<String> getRecentSplits() { return recentSplits; }
    public void setRecentSplits(List<String> recentSplits) { this.recentSplits = recentSplits; }
}


