package com.example.yfin.repo;

import com.example.yfin.model.doc.EarningsDoc;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface EarningsRepository extends ReactiveCrudRepository<EarningsDoc, String> {
    Flux<EarningsDoc> findByTickerOrderByFetchedAtDesc(String ticker);
}


