package com.medibook.record.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecordNotFoundException extends RuntimeException {
    public RecordNotFoundException(String message) { super(message); }
    public RecordNotFoundException(Long recordId) {
        super("Medical record not found with ID: " + recordId);
    }
}
