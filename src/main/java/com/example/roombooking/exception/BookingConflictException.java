package com.example.roombooking.exception;

public class BookingConflictException extends ConflictException {
    public BookingConflictException(String message) {
        super(message);
    }
}
