package com.medibook.record.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class RecordEditWindowExpiredException extends RuntimeException {
    public RecordEditWindowExpiredException(String message) { super(message); }
}
