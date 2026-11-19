package com.squared.locus.integration;

import java.math.BigDecimal;
import java.util.UUID;

// The foreign payment service, seen from our side. StripePaymentGateway is the real one;
// tests use a fake, so they never call Stripe.
public interface PaymentGateway {

    PaymentResult charge(UUID reservationId, BigDecimal amount, String currency, String paymentMethod);
}
