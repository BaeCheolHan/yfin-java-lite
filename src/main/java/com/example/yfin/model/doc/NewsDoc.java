package com.example.yfin.model.doc;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "news")
public class NewsDoc {
    @Id
    private String id;           // link hash or uuid
    @TextIndexed
    private String title;
    @Indexed(unique = true)
    private String link;
    private Instant publishedAt;
    private String source;
    private String lang;
}


