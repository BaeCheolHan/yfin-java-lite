package com.example.yfin.model;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "옵션 체인 응답")
public class OptionsResponse {
    @Schema(description = "티커", example = "JEPI")
    private String ticker;
    @Schema(description = "만기(epoch 초 또는 nearest)")
    private String expiration; // epoch seconds or "nearest"
    @Schema(description = "콜 체인")
    private List<OptionRow> calls;
    @Schema(description = "풋 체인")
    private List<OptionRow> puts;

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public String getExpiration() { return expiration; }
    public void setExpiration(String expiration) { this.expiration = expiration; }
    public List<OptionRow> getCalls() { return calls; }
    public void setCalls(List<OptionRow> calls) { this.calls = calls; }
    public List<OptionRow> getPuts() { return puts; }
    public void setPuts(List<OptionRow> puts) { this.puts = puts; }
}


