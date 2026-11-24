package com.example.camsat.service;

import com.example.camsat.domain.FlightEvent;
import com.example.camsat.domain.FlightEventType;
import com.example.camsat.repository.FlightEventRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Writes flight events idempotently. The (run_id, dedupe_key) unique constraint is the final guard; the
 * existence check keeps the normal path free of constraint errors. Call inside a transaction.
 */
@Component
public class EventRecorder {
    private final FlightEventRepository events;
    private final Clock clock;

    public EventRecorder(FlightEventRepository events, Clock clock) { this.events = events; this.clock = clock; }

    /** @return true if a new event was written, false if the same event already existed. */
    public boolean record(long runId, FlightEventType type, String message, double elapsedSeconds, String dedupeKey) {
        if (events.existsByRunIdAndDedupeKey(runId, dedupeKey)) return false;
        events.save(new FlightEvent(runId, type, message, elapsedSeconds, clock.instant(), dedupeKey));
        return true;
    }

    public boolean recordOnce(long runId, FlightEventType type, String message, double elapsedSeconds) {
        return record(runId, type, message, elapsedSeconds, type.name());
    }
}
