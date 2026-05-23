package com.yuno.payment.mapper;

import com.yuno.commons.enums.PaymentStatus;
import com.yuno.commons.enums.ProviderType;
import com.yuno.payment.model.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * MyBatis Mapper interface for the payments table.
 *
 * All SQL lives in PaymentMapper.xml — this interface is just the Java
 * contract. MyBatis generates the implementation at startup by binding
 * each method to the matching SQL statement id in the XML.
 *
 * Why this beats JPA for payments:
 * - INSERT is a hand-tuned single-row insert — no entity lifecycle overhead
 * - SELECT fetchById selects exactly the columns we need, nothing more
 * - UPDATE updateStatusAfterResult is a targeted update — no dirty tracking,
 *   no stale reads, no unexpected cascades
 * - All queries are in plain SQL readable by any DBA for review/optimisation
 */
@Mapper
public interface PaymentMapper {

    /**
     * Inserts a new payment in PENDING state.
     * Called once per new payment request before publishing to Kafka.
     */
    void insert(Payment payment);

    /**
     * Fetches a payment by its UUID primary key.
     * Called by GET /payments/{id} and by idempotency replay logic.
     */
    Payment findById(@Param("id") UUID id);

    /**
     * Fetches a payment by idempotency key.
     * Called as secondary check if Redis/idempotency-service is unavailable.
     */
    Payment findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * Updates payment status after provider-service publishes the result event.
     * Targeted UPDATE — only touches status-related columns, not the full row.
     */
    void updateStatusAfterResult(
            @Param("id")                    UUID id,
            @Param("status")                PaymentStatus status,
            @Param("providerUsed")          ProviderType providerUsed,
            @Param("providerTransactionId") String providerTransactionId,
            @Param("retryCount")            int retryCount,
            @Param("failoverUsed")          boolean failoverUsed,
            @Param("failureReason")         String failureReason
    );

    /**
     * Lists payments by status — for operational monitoring endpoints.
     */
    List<Payment> findByStatus(@Param("status") PaymentStatus status);

    /**
     * Checks whether an idempotency key already exists.
     * Lightweight EXISTS query — avoids fetching the full row unnecessarily.
     */
    boolean existsByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
