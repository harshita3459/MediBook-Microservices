package com.medibook.record.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AppointmentNotCompletedException extends RuntimeException {
    public AppointmentNotCompletedException(String message) { super(message); }
}
