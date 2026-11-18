package com.squared.locus.repository;

import com.squared.locus.model.PaymentOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, Long> {

    // The next payment that is due, locked for this transaction.
    // SKIP LOCKED: if another worker is already handling a row, skip it instead of waiting,
    // so the same payment is never processed twice at the same time.
    @Query(value = "select * from payment_outbox "
            + "where status = 'PENDING' and next_attempt_at <= now() "
            + "order by id limit 1 for update skip locked", nativeQuery = true)
    Optional<PaymentOutboxEvent> findNextDueForUpdate();
}
