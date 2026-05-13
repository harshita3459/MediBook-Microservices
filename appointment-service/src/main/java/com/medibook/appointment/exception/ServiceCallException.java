package com.medibook.appointment.exception;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a call to another microservice (schedule, payment, notification) fails */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ServiceCallException extends RuntimeException {
    public ServiceCallException(String msg) { 
    	super(msg); 
    }
    
    public ServiceCallException(String service, Throwable cause) {
        super("Call to " + service + " failed: " + cause.getMessage(), cause);
    }
}