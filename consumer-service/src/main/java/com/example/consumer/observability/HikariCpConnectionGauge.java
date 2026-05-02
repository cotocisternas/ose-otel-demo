package com.example.consumer.observability;

import com.zaxxer.hikari.HikariDataSource;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;

import org.springframework.stereotype.Component;

/**
 * Registers the {@code db.client.connection.count} ObservableGauge (Phase 8 METRIC-08-02).
 *
 * <p>Mirrors the {@link QueueDepthGauge} pattern from Phase 4 (D-18b shape):
 * a separate {@link Component}, injecting {@link Meter} and a {@link DataSource},
 * registering the gauge in the constructor and closing the handle in {@link #close()}.
 *
 * <p><b>Why a gauge not a counter?</b> Connection pool state is a CURRENT value
 * (how many connections are active RIGHT NOW), not a cumulative total. Counters
 * are for events that accumulate over time; gauges are for current-state readings
 * sampled on each collection cycle. This is the same design decision as
 * {@code orders.queue.depth.estimate} in Phase 4.
 *
 * <p><b>The {@link com.zaxxer.hikari.pool.HikariPool} null guard (RESEARCH §4 gotcha):</b>
 * HikariCP initializes the pool lazily — the first {@code getConnection()} call
 * triggers initialization. At Spring startup, the MXBean may be null when the
 * gauge callback first fires. The null guard skips the collection cycle silently
 * rather than throwing, so Mimir just sees a gap in the early samples.
 *
 * <p><b>Three state dimensions:</b> {@code state=used} (active connections),
 * {@code state=idle}, {@code state=pending} (threads waiting for a connection).
 * The {@code state} attribute follows the OTel incubating DB client metrics semconv
 * ({@code db.client.connection.count} metric with {@code state} in used/idle/pending).
 */
@Component
public class HikariCpConnectionGauge {

    private final ObservableLongGauge gauge;

    public HikariCpConnectionGauge(Meter meter, DataSource dataSource) {
        HikariDataSource hikariDs = (HikariDataSource) dataSource;

        // db.client.connection.count follows OTel DB client metrics semconv (incubating).
        // Surfaces in Mimir as db_client_connection_count{state="used|idle|pending"}.
        // Unit "{connections}" follows OTel curly-brace convention for dimensionless units.
        this.gauge = meter.gaugeBuilder("db.client.connection.count")
            .setDescription("HikariCP connection pool state (Phase 8 — active/idle/pending connections)")
            .setUnit("{connections}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                // RESEARCH §4 gotcha: HikariPoolMXBean is null until the pool is initialized
                // (first getConnection() call). Guard with null check; return without recording
                // to let the collection cycle pass cleanly.
                var mxBean = hikariDs.getHikariPoolMXBean();
                if (mxBean == null) {
                    return;
                }
                measurement.record(mxBean.getActiveConnections(),
                    Attributes.of(AttributeKey.stringKey("state"), "used"));
                measurement.record(mxBean.getIdleConnections(),
                    Attributes.of(AttributeKey.stringKey("state"), "idle"));
                measurement.record(mxBean.getThreadsAwaitingConnection(),
                    Attributes.of(AttributeKey.stringKey("state"), "pending"));
            });
    }

    /** Closes the gauge handle so the SDK stops invoking the callback at shutdown. */
    @PreDestroy
    public void close() {
        gauge.close();
    }
}
