package com.example.camsat.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/** Mirrors the frontend's ApiErrorBody (src/types/api.ts). */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiErrorBody(Instant timestamp, int status, String error, String message, Map<String, String> fieldErrors) {
    public static ApiErrorBody of(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ApiErrorBody(Instant.now(), status, error, message, fieldErrors);
    }
}
