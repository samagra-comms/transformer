package com.uci.transformer.generic;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class GenericOutboundMessage {
    private String message;
}
