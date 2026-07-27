package com.example.demo.repository;

import com.example.demo.entity.Payment;
import com.example.demo.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for Payment entity.
 *
 * <p><b>Responsibility:</b> Provide database access operations for payments.
 * All queries are read-only or persistence operations; business validation is delegated to Service layer.</p>
 *
 * <p><b>Key Methods:</b></p>
 * <ul>
 *   <li>{@code findByIdempotencyKey}: Locate existing payment by idempotency key for duplicate detection</li>
 *   <li>{@code findAllByOrderByCreatedAtDesc}: List all payments, newest first</li>
 *   <li>{@code findAllByStatusOrderByCreatedAtDesc}: Filter by status, newest first</li>
 *   <li>Inherited {@code findById}, {@code save}, {@code existsById}</li>
 * </ul>
 *
 * <p><b>Important Notes:</b></p>
 * <ul>
 *   <li>Ordering is strict and must match API contract: most recent payments first (DESC by createdAt)</li>
 *   <li>Do not add custom methods for business logic; keep repository focused on data access</li>
 *   <li>Idempotency key uniqueness is enforced at database level (UNIQUE constraint)</li>
 * </ul>
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    /**
     * Find a payment by its idempotency key.
     * Used to detect duplicate or replay requests.
     *
     * @param idempotencyKey the idempotency key to search for
     * @return Optional containing the payment if found, empty otherwise
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Retrieve all payments sorted by creation time, newest first.
     * Used for listing all payments without filters.
     *
     * @return list of all payments ordered by createdAt descending
     */
    List<Payment> findAllByOrderByCreatedAtDesc();

    /**
     * Retrieve all payments with a specific status, sorted by creation time, newest first.
     * Used for status-based filtering.
     *
     * @param status the payment status to filter by
     * @return list of payments with the specified status, ordered by createdAt descending
     */
    List<Payment> findAllByStatusOrderByCreatedAtDesc(PaymentStatus status);
}

