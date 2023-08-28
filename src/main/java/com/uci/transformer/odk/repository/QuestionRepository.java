package com.uci.transformer.odk.repository;

import com.uci.transformer.odk.entity.Question;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface QuestionRepository extends R2dbcRepository<Question, UUID> {

    Flux<Question> findQuestionByXPathAndFormIDAndFormVersion(String xPath, String formID, String formVersion);
    Mono<Question> findQuestionByXPathAndFormIDAndFormVersionOrderByCreatedOnDesc(String xPath, String formID, String formVersion);

}