package com.example.consumer.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.DbAttributes;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that wraps every Spring Data JPA repository method call in a
 * {@link SpanKind#CLIENT} span (Phase 14 DBSP-03).
 *
 * <p><b>Pointcut:</b> {@code bean(*Repository) && execution(public * *(..))}.
 * The {@code bean()} designator targets the Spring-managed proxy bean by name pattern
 * — more reliable than {@code execution()} alone for Spring Data JPA proxies.
 * See RESEARCH.md Pitfall 1 and spring-projects/spring-framework#24207 for why
 * {@code execution()} on interface methods is unreliable in this context.
 *
 * <p><b>Span naming (D-J6):</b> {@code "OrderJpaRepository.{methodName}"} e.g.
 * {@code "OrderJpaRepository.findByOrderId"} and {@code "OrderJpaRepository.save"}.
 * Phase 14 names spans after repository methods, not SQL verbs (compare with Phase 8's
 * {@code "INSERT processed_orders"}) — because JPA hides the SQL. Attendees see
 * what they called, not what Hibernate generated.
 *
 * <p><b>db.query.text (D-J5):</b> JPA method description, not generated SQL:
 * {@code "JpaRepository.findByOrderId(orderId)"} and {@code "JpaRepository.save(Order)"}.
 * Uses {@link DbAttributes#DB_QUERY_TEXT} constant — NEVER the legacy string literal
 * for db.statement (F5-2 mitigation — see RESEARCH.md Pitfall 3).
 *
 * <p><b>Span wrapping (F5-1):</b> wraps at repository METHOD level, not SQL execution level.
 * This prevents N+1 span explosion if lazy-loaded associations are ever added.
 */
@Aspect
@Component
public class TracingRepositoryAspect {

    // PostgreSQL database name — matches application.yaml datasource URL path segment.
    // Used for db.namespace (semconv 1.40.0: "the name of the database, fully qualified
    // within the server address and port"). For this single-database workshop, database
    // name only (not schema) is the recommended form.
    private static final String DB_NAMESPACE = "orders";

    // Table name from @Table(name="orders") in Order.java (D-J7).
    private static final String DB_COLLECTION = "orders";

    private final Tracer tracer;

    public TracingRepositoryAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Wrap each public JPA repository method in a CLIENT span with full {@code db.*} semconv.
     *
     * <p>Uses {@code bean(*Repository)} to reliably intercept Spring Data JPA proxy beans
     * (see class-level JavaDoc for why {@code execution()} alone is unreliable).
     */
    @Around("bean(*Repository) && execution(public * *(..)) && target(org.springframework.data.repository.Repository)")
    public Object traceRepositoryMethod(ProceedingJoinPoint pjp) throws Throwable {
        // D-J6: span name = "OrderJpaRepository.{methodName}".
        // Use the target bean's most-specific repository interface (e.g. OrderJpaRepository)
        // rather than the method's declaring class (e.g. CrudRepository.save is declared
        // in CrudRepository, not in OrderJpaRepository). The proxy bean implements both;
        // we want the user-defined interface that matches the bean(*Repository) pointcut.
        String simpleName    = resolveRepositoryInterfaceName(pjp.getTarget());
        String methodName    = pjp.getSignature().getName();
        String spanName      = simpleName + "." + methodName;        // D-J6
        String operationName = resolveOperationName(methodName);
        String queryText     = resolveQueryText(methodName, pjp.getArgs()); // D-J5

        // ---- D-01 CLIENT span template (adapted for AOP) ----
        Span span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(DbAttributes.DB_SYSTEM_NAME, DbAttributes.DbSystemNameValues.POSTGRESQL)
            .setAttribute(DbAttributes.DB_NAMESPACE, DB_NAMESPACE)
            .setAttribute(DbAttributes.DB_OPERATION_NAME, operationName)
            .setAttribute(DbAttributes.DB_COLLECTION_NAME, DB_COLLECTION)
            .setAttribute(DbAttributes.DB_QUERY_TEXT, queryText)   // F5-2: typed constant, not the legacy db.statement string
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            return pjp.proceed();
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Resolve the most-specific user-defined repository interface name from the AOP target.
     *
     * <p>Spring Data JPA proxies implement multiple interfaces in their hierarchy
     * (e.g. {@code OrderJpaRepository} → {@code JpaRepository} → {@code CrudRepository}).
     * The method's {@code declaringType} points to where the method is DEFINED in the hierarchy
     * (e.g. {@code CrudRepository.save}), not to the user-defined interface. We traverse
     * the target bean's interfaces and return the first one whose simple name ends in
     * {@code "Repository"} and is not a Spring Data framework interface — this gives us
     * {@code "OrderJpaRepository"} for all methods on the bean, including inherited ones.
     *
     * <p>Falls back to the declaring type's simple name if no user-defined interface is found
     * (should not occur in practice — the bean(*Repository) pointcut already guarantees
     * the target implements a *Repository interface).
     */
    private static String resolveRepositoryInterfaceName(Object target) {
        for (Class<?> iface : target.getClass().getInterfaces()) {
            String name = iface.getSimpleName();
            // Skip Spring Data framework interfaces (they're in the org.springframework.data
            // or org.springframework.data.jpa packages). User-defined repository interfaces
            // are in application packages (e.g. com.example.consumer.db).
            if (name.endsWith("Repository")
                    && !iface.getName().startsWith("org.springframework.data")) {
                return name;
            }
        }
        // Fallback: use declaring type (covers edge case where no user-defined interface found)
        return target.getClass().getSimpleName();
    }

    /**
     * Map JPA method name prefix to SQL operation verb for {@code db.operation.name}.
     * Semconv 1.40.0 examples: SELECT, INSERT, DELETE, EXECUTE.
     */
    private static String resolveOperationName(String methodName) {
        if (methodName.startsWith("find") || methodName.startsWith("get"))      return "SELECT";
        if (methodName.startsWith("save") || methodName.startsWith("persist"))  return "INSERT";
        if (methodName.startsWith("delete") || methodName.startsWith("remove")) return "DELETE";
        return methodName.toUpperCase();
    }

    /**
     * Build the {@code db.query.text} value: JPA method description (D-J5).
     * Honest about the abstraction level — attendees see what they called, not
     * what Hibernate generated.
     *
     * <p>Format: {@code "JpaRepository.{methodName}({firstArgSimpleType})"}.
     * Examples: {@code "JpaRepository.findByOrderId(String)"},
     *           {@code "JpaRepository.save(Order)"}.
     */
    private static String resolveQueryText(String methodName, Object[] args) {
        String argType = (args != null && args.length > 0 && args[0] != null)
            ? args[0].getClass().getSimpleName()
            : "?";
        return "JpaRepository." + methodName + "(" + argType + ")";
    }
}
