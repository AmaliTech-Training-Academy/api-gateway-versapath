package com.capstone.api_gateway.exception;

public class AppException extends RuntimeException{
    public AppException(String message){
        super(message);
    }
}
