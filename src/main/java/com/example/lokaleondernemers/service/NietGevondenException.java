package com.example.lokaleondernemers.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NietGevondenException extends RuntimeException {

    public NietGevondenException(String message) {
        super(message);
    }
}
