package com.squared.locus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// LOCUS-39: the background worker. Every 5 seconds it sends the waiting payments to Stripe.
// It runs on its own, outside any HTTP request, so a slow Stripe never slows down booking.
@Configuration
@EnableScheduling
public class PaymentWorker {
    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);

    // Handle at most this many payments per round, so one round never runs forever.
    private static final int MAX_PER_ROUND = 20;

    private final PaymentProcessor paymentProcessor;
    private final String stripeSecretKey;

    public PaymentWorker(PaymentProcessor paymentProcessor,
                         @Value("${locus.stripe.secret-key:}") String stripeSecretKey) {
        this.paymentProcessor = paymentProcessor;
        this.stripeSecretKey = stripeSecretKey;
    }

    @Scheduled(fixedDelayString = "${locus.payments.poll-interval-ms:5000}")
    public void sendPendingPayments() {
        // No Stripe key (tests, or a teammate without one): do nothing.
        if (stripeSecretKey.isBlank()) {
            return;
        }
        try {
            for (int i = 0; i < MAX_PER_ROUND; i++) {
                boolean handledOne = paymentProcessor.processNext();
                if (!handledOne) {
                    return;   // nothing left to send right now
                }
            }
        } catch (RuntimeException e) {
            log.error("Payment worker round failed; will try again next round", e);
        } finally {
            MDC.remove("correlationId");   // the worker thread is reused, so always clean up
        }
    }
}
