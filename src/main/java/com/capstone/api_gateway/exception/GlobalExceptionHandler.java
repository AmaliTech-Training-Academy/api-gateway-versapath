package com.capstone.api_gateway.exception;

import com.capstone.api_gateway.dto.ResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handle all other exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpectedException
    (Exception exception) {
        ResponseDto response = ResponseDto.builder()
                .status(false)
                .message(exception.getMessage())
                .errors(null)
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // handle session is not found exception response
    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<?> handleSessionNotFound(
            SessionNotFoundException exception) {
        ResponseDto response = ResponseDto.builder()
                .status(false)
                .message(exception.getMessage())
                .errors(null)
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> handleUnauthorizedException
            (UnauthorizedException exception) {
        ResponseDto response = ResponseDto.builder()
                .status(false)
                .message(exception.getMessage())
                .errors(null)
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    // handle any unexpected Error
    @ExceptionHandler(Error.class)
    public ResponseEntity<?> handleError(Error error) {
        ResponseDto response = ResponseDto.builder()
                .status(false)
                .message(error.getMessage())
                .errors(null)
                .data(null)
                .build();

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
