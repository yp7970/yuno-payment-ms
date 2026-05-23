package com.yuno.payment.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String field, String value) {
        super(String.format("Payment not found with %s: %s", field, value));
    }
}
