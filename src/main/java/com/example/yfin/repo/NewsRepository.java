package com.example.yfin.repo;

import com.example.yfin.model.doc.NewsDoc;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface NewsRepository extends ReactiveCrudRepository<NewsDoc, String> {
    @Query("{ $text: { $search: ?0 } }")
    Flux<NewsDoc> textSearch(String q);
}


