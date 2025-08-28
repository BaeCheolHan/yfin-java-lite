package com.example.yfin.repo;

import com.example.yfin.model.doc.ListingMetaDoc;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ListingMetaRepository extends ReactiveCrudRepository<ListingMetaDoc, String> {
    Mono<ListingMetaDoc> findByBaseCode(String baseCode);
}


