package com.example.yfin.repo;

import com.example.yfin.model.doc.ProfileDoc;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ProfileRepository extends ReactiveCrudRepository<ProfileDoc, String> {
    Flux<ProfileDoc> findByTickerOrderByFetchedAtDesc(String ticker);
}


