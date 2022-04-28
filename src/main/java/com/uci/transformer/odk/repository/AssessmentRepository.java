package com.uci.transformer.odk.repository;

import com.datastax.driver.mapping.annotations.Query;
import com.uci.transformer.odk.entity.Assessment;
import com.uci.transformer.odk.entity.GupshupStateEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.List;

public interface AssessmentRepository extends R2dbcRepository<Assessment, UUID> {
	
//	Mono<List<Assessment> findByUserID(UUID userID);
	Flux<Assessment> findByUserIDOrderByUpdatedOnDesc(UUID userID);

//	@Query("from assessment as INNER JOIN question q ON q.id=as.question where as.userID=:userID AND as.botID=:botID ORDER BY updatedOn")
	Flux<Assessment> findByUserIDAndBotIDOrderByUpdatedOnDesc(UUID userID, UUID botID);
	
	Mono<Assessment> findById(UUID id);

}
