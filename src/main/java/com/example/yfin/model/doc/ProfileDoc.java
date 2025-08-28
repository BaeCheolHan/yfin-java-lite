package com.example.yfin.model.doc;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Document(collection = "profiles")
public class ProfileDoc {
    @Id
    private String id;       // ticker + timestamp
    private String ticker;
    private Instant fetchedAt;
    private Map<String, Object> summaryProfile;
    private Map<String, Object> esgScores;
}


