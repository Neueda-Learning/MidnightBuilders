package com.example.demo.repository;

import com.example.demo.entity.PaymentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for PaymentStatusHistory entity.
 *
 * <p><b>Responsibility:</b> Provide database access operations for payment status history records.
 * History records are immutable and write-once; this repository primarily supports querying and audit trail retrieval.</p>
 *
 * <p><b>Key Methods:</b></p>
 * <ul>
 *   <li>{@code findAllByPaymentIdOrderByChangedAtAsc}: Retrieve chronological audit trail, oldest to newest</li>
 *   <li>{@code findFirstByPaymentIdOrderByChangedAtDesc}: Get the most recent transition for consistency checks</li>
 *   <li>Inherited {@code save}: Persist new history records (write-once)</li>
 * </ul>
 *
 * <p><b>Important Notes:</b></p>
 * <ul>
 *   <li>History records should be written in the same transaction as payment status updates for consistency</li>
 *   <li>Time-based ordering is strict: ascending for timeline display, descending for "latest event" queries</li>
 *   <li>All timestamps are UTC; repositories do not perform timezone conversion</li>
 *   <li>Foreign key constraint ensures no orphaned history (every paymentId must exist in payments table)</li>
 * </ul>
 */
@Repository
public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, String> {

    /**
     * Retrieve all status history records for a payment, ordered chronologically (oldest first).
     * Used to display a timeline of state transitions to users.
     *
     * @param paymentId the payment ID to retrieve history for
     * @return list of history records sorted by changedAt ascending (earliest to latest)
     */
    List<PaymentStatusHistory> findAllByPaymentIdOrderByChangedAtAsc(String paymentId);

    /**
     * Retrieve the most recent status transition for a payment.
     * Used for consistency validation: the toStatus of the latest history
     * should match the current status field in Payment entity.
     *
     * @param paymentId the payment ID
     * @return Optional containing the most recent history record, empty if no history exists
     */
    Optional<PaymentStatusHistory> findFirstByPaymentIdOrderByChangedAtDesc(String paymentId);
}

