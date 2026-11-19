package com.squared.locus.integration;

// What happened when we asked the payment provider to charge a reservation.
public record PaymentResult(Outcome outcome, String paymentIntentId, String message) {

    public enum Outcome {
        SUCCEEDED,  // money taken: confirm the reservation
        DECLINED,   // final "no" (e.g. card declined): retrying will not help
        RETRY       // temporary problem (timeout, Stripe down): try again later
    }

    public static PaymentResult succeeded(String paymentIntentId) {
        return new PaymentResult(Outcome.SUCCEEDED, paymentIntentId, null);
    }

    public static PaymentResult declined(String paymentIntentId, String message) {
        return new PaymentResult(Outcome.DECLINED, paymentIntentId, message);
    }

    public static PaymentResult retry(String message) {
        return new PaymentResult(Outcome.RETRY, null, message);
    }
}
