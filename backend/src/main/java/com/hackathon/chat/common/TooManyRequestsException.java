package com.hackathon.chat.common;

public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String scope) {
        super("Too many requests in scope " + scope);
    }
}
