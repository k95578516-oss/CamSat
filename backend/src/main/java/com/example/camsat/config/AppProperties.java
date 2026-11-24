package com.example.camsat.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camsat")
public record AppProperties(Jwt jwt, Cors cors, Simulation simulation, Telemetry telemetry) {
    public record Jwt(String secret, long expirationSeconds, boolean allowEphemeralSecret) { }
    public record Cors(List<String> allowedOrigins) { }
    public record Simulation(long sampleDelayMs, int coreThreads, int maxThreads, int queueCapacity) { }
    public record Telemetry(int maxRows) { }
}
