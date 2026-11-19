package com.squared.locus.integration;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

// LOCUS-37: charges a reservation through Stripe (test mode, fake cards only).
@Component
public class StripePaymentGateway implements PaymentGateway {

    private final String secretKey;

    public StripePaymentGateway(@Value("${locus.stripe.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public PaymentResult charge(UUID reservationId, BigDecimal amount, String currency, String paymentMethod) {
        // LOCUS-40: the same reservation always uses the same idempotency key, so if we retry
        // after a timeout, Stripe returns the first result instead of charging twice.
        RequestOptions options = RequestOptions.builder()
                .setApiKey(secretKey)
                .setIdempotencyKey("reservation-" + reservationId)
                .setConnectTimeout(5_000)   // stability: never wait forever on Stripe
                .setReadTimeout(10_000)
                .build();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.movePointRight(2).longValueExact())   // Stripe wants cents: 20.00 -> 2000
                .setCurrency(currency.toLowerCase())
                .setPaymentMethod(paymentMethod)
                .addPaymentMethodType("card")
                .setConfirm(true)                                       // charge right away
                .putMetadata("reservationId", reservationId.toString())
                .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params, options);
            if ("succeeded".equals(intent.getStatus())) {
                return PaymentResult.succeeded(intent.getId());
            }
            return PaymentResult.declined(intent.getId(), "Payment not completed: " + intent.getStatus());
        } catch (CardException e) {
            // The card was declined: a final answer.
            return PaymentResult.declined(null, e.getMessage());
        } catch (InvalidRequestException e) {
            // Bad card id or amount: retrying the same request would fail the same way.
            return PaymentResult.declined(null, e.getMessage());
        } catch (ApiConnectionException e) {
            // Timeout or network problem: try again later.
            return PaymentResult.retry("Could not reach Stripe: " + e.getMessage());
        } catch (StripeException e) {
            // Anything else from Stripe (rate limit, Stripe error, bad key): try again later.
            return PaymentResult.retry(e.getMessage());
        }
    }
}
