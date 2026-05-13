package com.medibook.appointment.exception;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when patient tries to book a slot that already has an appointment */
@ResponseStatus(HttpStatus.CONFLICT)
public class AppointmentAlreadyExistsException extends RuntimeException {
    public AppointmentAlreadyExistsException(String msg) { 
    	super(msg); 
    }
}