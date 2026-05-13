package com.medibook.appointment.exception;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when trying an illegal status change, e.g. cancelling a completed appointment */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String msg) { 
    	super(msg); 
    }
}