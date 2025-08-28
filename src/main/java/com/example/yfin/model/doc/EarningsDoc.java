package com.example.yfin.model.doc;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Document(collection = "earnings")
public class EarningsDoc {
    @Id
    private String id;           // ticker + timestamp
    private String ticker;
    private Instant fetchedAt;
    private Map<String, Object> earnings;
    private Map<String, Object> earningsTrend;
    private Map<String, Object> calendarEvents;
}


