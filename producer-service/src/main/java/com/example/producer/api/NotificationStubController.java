package com.example.producer.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * In-process notification stub — the outbound HTTP target for Phase 15.
 *
 * <p><strong>D-H4:</strong> {@code POST /notifications} is semantically correct (triggers a
 * side effect) and distinct from the existing {@code POST /orders} endpoint. Attendees see
 * two different POST endpoints in the same service — one inbound (orders), one outbound target.
 *
 * <p><strong>D-H5:</strong> The stub logs the {@code traceparent} header at INFO level so
 * attendees can grep the producer log for proof that W3C context propagation worked. Returns
 * {@code 200 OK} with empty body — minimal implementation satisfying HCLI-04.
 *
 * <p><strong>D-H5: {@code required = false}.</strong> Prevents a {@code 400 Bad Request} if
 * propagation breaks during development (e.g., interceptor misconfigured). The stub degrades
 * gracefully — logs {@code traceparent: null} instead of rejecting the call.
 *
 * <p><strong>Teaching moment:</strong> {@code HttpServerSpanFilter} automatically wraps this
 * endpoint in a {@code SERVER} span (it only excludes {@code /actuator/*}). Attendees see a
 * {@code CLIENT → SERVER} span nesting within the same JVM — proof that {@code traceparent}
 * was correctly injected by the interceptor and extracted by the filter.
 */
@RestController
public class NotificationStubController {
    private static final Logger log = LoggerFactory.getLogger(NotificationStubController.class);

    // D-H4: POST /notifications — semantically correct (side-effect trigger).
    // D-H5: @RequestHeader required=false prevents 400 if propagation breaks during development.
    @PostMapping("/notifications")
    public ResponseEntity<Void> notify(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "traceparent", required = false) String traceparent) {
        // D-H5: Log traceparent so attendees can grep: "Notification received for order"
        // The traceparent value is in the format: "00-<traceId>-<spanId>-01"
        // A null value here means the interceptor is not registered or misconfigured.
        log.info("Notification received for order {}. traceparent header: {}",
            body.get("orderId"), traceparent);
        return ResponseEntity.ok().build();
    }
}
