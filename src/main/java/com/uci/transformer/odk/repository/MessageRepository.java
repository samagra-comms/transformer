package com.uci.transformer.odk.repository;

import com.uci.transformer.odk.entity.GupshupMessageEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface MessageRepository extends R2dbcRepository<GupshupMessageEntity, Long> {
  Flux<GupshupMessageEntity> findByPhoneNo(String phoneNo);
}
