package com.example.consumer.db;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Order} entities (Phase 14 DBSP-02).
 *
 * <p>Spring Data generates the implementation — no {@code @Repository} annotation
 * needed (Spring Data JPA auto-detects interfaces extending {@link JpaRepository}).
 *
 * <p>{@link #findByOrderId} is a derived query method: Spring Data generates
 * {@code SELECT ... WHERE order_id = ?} without any JPQL. This is the idempotency
 * check in {@link OrderJpaService#persist} (CONTEXT.md D-J3/D-J4).
 *
 * <p><b>Why {@code JpaRepository<Order, Long>} not {@code CrudRepository}?</b>
 * {@code JpaRepository} provides flush/batch operations — useful if the workshop
 * expands to bulk scenarios. No cost at the single-entity level.
 */
public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    /**
     * Find an {@link Order} by its business key.
     * Used by {@link OrderJpaService#persist} to check existence before inserting.
     *
     * @param orderId the business order identifier (matches the {@code order_id} column)
     * @return an {@link Optional} containing the entity if found, empty otherwise
     */
    Optional<Order> findByOrderId(String orderId);
}
