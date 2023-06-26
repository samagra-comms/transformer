package com.uci.transformer.odk.entity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.*;
import io.r2dbc.postgresql.codec.Json;

import javax.persistence.*;
import javax.persistence.Entity;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.annotation.Id;


@Data
@Getter
@Setter
@AllArgsConstructor
@Entity
@Builder
@NoArgsConstructor
@Table(value = "question")
public class Question {

    public enum QuestionType {
        SINGLE_SELECT,
        MULTI_SELECT,
        STRING;

        public String toString() {
            return this.name();
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(value = "form_id")
    private String formID;

    @Column(value = "form_version")
    private String formVersion;

    @Column(value = "x_path")
    private String XPath;

    @Column(value = "question_type")
    @Enumerated(EnumType.STRING)
    private QuestionType questionType;

    @JsonSerialize(using = PgJsonObjectSerializer.class)
    @JsonDeserialize(using = PgJsonObjectDeserializer.class)
    @Column(value = "meta")
    private Json meta;

    @Column(value = "updated")
    private LocalDateTime updatedOn;

    @Column(value = "created")
    private LocalDateTime createdOn;

}

