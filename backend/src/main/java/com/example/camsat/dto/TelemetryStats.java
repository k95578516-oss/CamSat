package com.example.camsat.dto;

/** Aggregate projection computed in the database; all values except count are null when there are no readings. */
public record TelemetryStats(Long count, Double maxAltitude, Double minBattery, Double maxDescentSpeed) { }
