package com.uci.transformer.odk.entity;

import javax.persistence.*;

import lombok.*;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.annotation.Id;

@Data
@Getter
@Setter
@AllArgsConstructor
@Entity
@NoArgsConstructor
@Table(value = "xmessage")
public class GupshupMessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  
  @Column(value = "phone_no")
  private String phoneNo;
  
  @Column(value = "message")
  private String message;

  @Column(value = "is_last_message")
  private boolean isLastResponse;
}
