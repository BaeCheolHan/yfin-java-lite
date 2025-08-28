package com.example.yfin.model.doc;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "listing_meta")
public class ListingMetaDoc {
    @Id
    private String id;            // symbol (e.g., 005930.KS)
    @Indexed
    private String baseCode;      // 6-digit for KR stocks (e.g., 005930)
    private String market;        // KS/KQ/KONEX/ETF/NASDAQ/NYS etc
    private String name;          // company/etf name
    private String sector;        // optional sector
    private Instant updatedAt;    // snapshot updated time
}


