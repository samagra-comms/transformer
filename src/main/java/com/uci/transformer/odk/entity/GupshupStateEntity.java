package com.uci.transformer.odk.entity;


import lombok.*;

import javax.persistence.*;

import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.annotation.Id;


@Data
@Getter
@Setter
@AllArgsConstructor
@Entity
@NoArgsConstructor
@ToString
@Table(value = "xmessage_state")
public class GupshupStateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(value = "phone_no")
    private String phoneNo;

    @Column(value = "state")
    private String xmlPrevious;

    @Column(value = "previous_path")
    private String previousPath;

    @Column(value = "bot_form_name")
    private String botFormName;

}
