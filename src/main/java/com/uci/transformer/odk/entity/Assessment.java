package com.uci.transformer.odk.entity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.vladmihalcea.hibernate.type.json.JsonType;
import io.r2dbc.postgresql.codec.Json;
import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.annotation.Id;


@Data
@Getter
@Setter
@AllArgsConstructor
@Entity
@NoArgsConstructor
@Builder
@Table(value = "assessment")
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @Column(value = "question")
    private Question question;

    // Has to be encrypted.
    @Column(value = "answer")
    private String answer;

    @Column(value = "bot_id")
    private UUID botID;

    @Column(value = "user_id")
    private UUID userID;

    @Column(value = "device_id")
    private UUID deviceID;

    @JsonSerialize(using = PgJsonObjectSerializer.class)
    @JsonDeserialize(using = PgJsonObjectDeserializer.class)
    @Column(value = "meta")
    private Json meta;

    @Column(value = "updated")
    private LocalDateTime updatedOn;

    @Column(value = "created")
    private LocalDateTime createdOn;

}

