package com.uci.transformer.odk.repository;

import com.uci.transformer.odk.entity.GupshupStateEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StateRepository extends R2dbcRepository<GupshupStateEntity, Long> {
  Flux<GupshupStateEntity> findByPhoneNo(String phoneNo);
  Mono<GupshupStateEntity> findByPhoneNoAndBotFormName(String phoneNo, String botFormName);
}
