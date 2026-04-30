/**
 * OpenTelemetry bootstrap library for the OSE OTel Demo workshop.
 *
 * <p>Phase 1 ships this module empty. Phase 3 populates it with the
 * shared AMQP propagation pair ({@code TracingMessagePostProcessor} +
 * {@code TracingMessageListenerAdvice}). Per {@code PROJECT.md}, the SDK
 * bootstrap itself is intentionally NOT extracted here — that lives
 * per-service so attendees see the wiring twice (TRACE-01, DOC-05).
 *
 * <p>This file exists only so the otherwise-empty module has at least one
 * Java source, which keeps Maven's compiler plugin happy and produces a
 * non-empty JAR.
 */
package com.example.otel;
