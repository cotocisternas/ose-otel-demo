package com.example.consumer.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for persisting processed orders (Phase 14 DBSP-02).
 *
 * <p>Replaces the Phase 8 raw-JDBC {@code processed_orders} table.
 * Compare tags: {@code git diff step-08-db-cache..step-14-jpa-spans}
 *
 * <p><b>Table design (CONTEXT.md D-J7):</b>
 * <ul>
 *   <li>{@code id} — surrogate Long key ({@code @GeneratedValue IDENTITY}) — JPA convention</li>
 *   <li>{@code orderId} — business key ({@code unique = true}) — idempotency anchor (D-J4)</li>
 *   <li>{@code payload} — JSON stored as TEXT — workshop focus is OTel, not JSONB type mapping</li>
 *   <li>{@code processedAt} — Instant (UTC) of successful processing</li>
 *   <li>{@code traceId} — W3C trace_id at persist time (D-J8): bridge column between
 *       relational and observability worlds — find any row's originating trace in Tempo</li>
 * </ul>
 *
 * <p><b>DDL ownership (D-J2):</b> Hibernate creates/patches this table via
 * {@code spring.jpa.hibernate.ddl-auto=update}. No {@code schema.sql} needed.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", unique = true, nullable = false)
    private String orderId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    /** JPA no-arg constructor requirement. Not for application use. */
    protected Order() {}

    /**
     * Factory constructor. Use this for all application-side creation.
     * Immutable after construction — only getters are exposed (coding-style.md).
     */
    public Order(String orderId, String payload, Instant processedAt, String traceId) {
        this.orderId      = orderId;
        this.payload      = payload;
        this.processedAt  = processedAt;
        this.traceId      = traceId;
    }

    public Long    getId()          { return id; }
    public String  getOrderId()     { return orderId; }
    public String  getPayload()     { return payload; }
    public Instant getProcessedAt() { return processedAt; }
    public String  getTraceId()     { return traceId; }
}
