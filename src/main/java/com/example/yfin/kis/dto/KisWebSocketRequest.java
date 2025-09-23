package com.example.yfin.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KisWebSocketRequest {
    
    @JsonProperty("header")
    private Header header;
    
    @JsonProperty("body")
    private Body body;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        @JsonProperty("approval_key")
        private String approvalKey;
        
        @JsonProperty("custtype")
        private String custtype = "P";
        
        @JsonProperty("tr_type")
        private String trType = "1";
        
        @JsonProperty("content-type")
        private String contentType = "utf-8";
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        @JsonProperty("input")
        private Input input;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Input {
        @JsonProperty("tr_id")
        private String trId;
        
        @JsonProperty("tr_key")
        private String trKey;
    }
    
    // 편의 생성자 (구독용)
    public KisWebSocketRequest(String approvalKey, String trId, String trKey) {
        this.header = new Header(approvalKey, "P", "1", "utf-8");
        this.body = new Body(new Input(trId, trKey));
    }
    
    // 편의 생성자 (해제용)
    public KisWebSocketRequest(String approvalKey, String trId, String trKey, boolean unsubscribe) {
        this.header = new Header(approvalKey, "P", unsubscribe ? "2" : "1", "utf-8");
        this.body = new Body(new Input(trId, trKey));
    }
}
