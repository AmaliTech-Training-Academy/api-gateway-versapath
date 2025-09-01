package com.capstone.api_gateway.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseDto {
    private Boolean status;
    private String message;
    private Object data;
    private List<Map<String, String>> errors;
}
