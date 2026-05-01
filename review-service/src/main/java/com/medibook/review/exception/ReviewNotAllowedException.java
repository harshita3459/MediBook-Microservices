package com.medibook.review.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ReviewNotAllowedException extends RuntimeException {
    public ReviewNotAllowedException(String message) { super(message); }
}
