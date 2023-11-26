package com.uci.transformer.odk.entity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;
import io.r2dbc.postgresql.codec.Json;

import javax.persistence.*;
import javax.persistence.Entity;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Timestamp;
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
@Builder
@NoArgsConstructor
@Table(value = "question")
public class Question implements Serializable {

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

    private void writeObject(ObjectOutputStream outputStream) throws IOException {
        outputStream.writeUTF(id != null ? id.toString() : "");
        outputStream.writeUTF(formID != null ? formID : "");
        outputStream.writeUTF(formVersion != null ? formVersion : "");
        outputStream.writeUTF(XPath != null ? XPath : "");
        outputStream.writeUTF(questionType != null ? questionType.toString() : "");
        outputStream.writeUTF(meta != null ? meta.asString() : "");
        outputStream.writeLong(updatedOn != null ? Timestamp.valueOf(updatedOn).getTime() : 0);
        outputStream.writeLong(createdOn != null ? Timestamp.valueOf(createdOn).getTime() : 0);
    }

    private void readObject(ObjectInputStream inputStream) throws IOException {
        String idString = inputStream.readUTF();
        id = !idString.isEmpty() ? UUID.fromString(idString) : null;
        formID = inputStream.readUTF();
        formVersion = inputStream.readUTF();
        XPath = inputStream.readUTF();
        String questionTypeString = inputStream.readUTF();
        questionType = !questionTypeString.isEmpty() ? QuestionType.valueOf(questionTypeString) : null;
        String metaString = inputStream.readUTF();
        meta = !metaString.isEmpty() ? Json.of(metaString) : null;
        long updatedOnTime = inputStream.readLong();
        updatedOn = updatedOnTime != 0 ? new Timestamp(updatedOnTime).toLocalDateTime() : null;
        long createdOnTime = inputStream.readLong();
        createdOn = createdOnTime != 0 ? new Timestamp(createdOnTime).toLocalDateTime() : null;
    }
}

