package com.capstone.api_gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponseDto<T> {
    private boolean success;
    private String message;
    private T data;
    private List<String> errors;

    // Static factory methods for success responses
    public static <T> ApiResponseDto<T> success(T data) {
        return ApiResponseDto.<T>builder()
                .success(true)
                .message("Operation completed successfully")
                .data(data)
                .build();
    }

    public static <T> ApiResponseDto<T> success(T data, String message) {
        return ApiResponseDto.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponseDto<T> success(String message) {
        return ApiResponseDto.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    // Static factory methods for error responses
    public static <T> ApiResponseDto<T> error(List<String> errors) {
        return ApiResponseDto.<T>builder()
                .success(false)
                .message("Operation failed")
                .errors(errors)
                .build();
    }

    public static <T> ApiResponseDto<T> error(String error) {
        return ApiResponseDto.<T>builder()
                .success(false)
                .message("Operation failed")
                .errors(List.of(error))
                .build();
    }

    public static <T> ApiResponseDto<T> error(List<String> errors, String message) {
        return ApiResponseDto.<T>builder()
                .success(false)
                .message(message)
                .errors(errors)
                .build();
    }

    public static <T> ApiResponseDto<T> error(String error, String message) {
        return ApiResponseDto.<T>builder()
                .success(false)
                .message(message)
                .errors(List.of(error))
                .build();
    }
}

