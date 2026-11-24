package com.example.camsat.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.camsat.CamsatApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.SpringApplication;

/**
 * Starts the whole application twice against the same on-disk database to prove that data survives a restart and that a
 * run interrupted by the shutdown is closed as FAILED rather than left RUNNING forever.
 */
class RestartPersistenceTest {
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private ConfigurableApplicationContext start(Path dir, long sampleDelayMs) {
        return SpringApplication.run(CamsatApplication.class,
                "--spring.profiles.active=test", "--server.port=0",
                "--spring.datasource.url=jdbc:h2:file:" + dir.resolve("camsat").toString().replace('\\', '/') + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "--camsat.simulation.sample-delay-ms=" + sampleDelayMs);
    }

    private JsonNode call(ConfigurableApplicationContext ctx, String method, String path, String token, String body) throws Exception {
        String port = ctx.getEnvironment().getProperty("local.server.port");
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).as(method + " " + path + " -> " + r.body()).isBetween(200, 299);
        return r.body().isBlank() ? null : om.readTree(r.body());
    }

    private static final String MISSION = "{\"name\":\"Persist\",\"payloadMassKg\":0.35,\"releaseAltitudeM\":%s,\"parachuteDiameterM\":0.5,"
            + "\"dragCoefficient\":1.5,\"windSpeedMps\":3,\"windDirectionDeg\":90,\"initialBatteryPercent\":100}";

    @Test
    void dataSurvivesARestartAndInterruptedRunsAreClosed(@TempDir Path dir) throws Exception {
        String token; long finishedRun, interruptedRun, missionId, telemetryCount;
        ConfigurableApplicationContext first = start(dir, 0);
        try {
            token = call(first, "POST", "/api/auth/register", null, "{\"name\":\"Rita\",\"email\":\"rita@example.com\",\"password\":\"password123\"}").get("token").asText();
            missionId = call(first, "POST", "/api/missions", token, MISSION.formatted("300")).get("id").asLong();
            finishedRun = call(first, "POST", "/api/missions/" + missionId + "/runs", token, null).get("id").asLong();
            call(first, "POST", "/api/runs/" + finishedRun + "/start", token, null);
            long end = System.currentTimeMillis() + 60_000;
            while (!call(first, "GET", "/api/runs/" + finishedRun, token, null).get("status").asText().equals("COMPLETED") && System.currentTimeMillis() < end) Thread.sleep(50);
            telemetryCount = call(first, "GET", "/api/runs/" + finishedRun + "/telemetry", token, null).size();
        } finally { first.close(); }
        // second run is slow and is interrupted by closing the application mid-flight
        ConfigurableApplicationContext slow = start(dir, 20);
        try {
            long m2 = call(slow, "POST", "/api/missions", token, MISSION.formatted("10000")).get("id").asLong();
            interruptedRun = call(slow, "POST", "/api/missions/" + m2 + "/runs", token, null).get("id").asLong();
            call(slow, "POST", "/api/runs/" + interruptedRun + "/start", token, null);
            long end = System.currentTimeMillis() + 30_000;
            while (call(slow, "GET", "/api/runs/" + interruptedRun + "/telemetry", token, null).isEmpty() && System.currentTimeMillis() < end) Thread.sleep(20);
        } finally { slow.close(); }

        ConfigurableApplicationContext second = start(dir, 0);
        try {
            // the JWT issued before the restart is still valid (same configured secret) and all data is still there
            assertThat(call(second, "GET", "/api/missions/" + missionId, token, null).get("name").asText()).isEqualTo("Persist");
            JsonNode run = call(second, "GET", "/api/runs/" + finishedRun, token, null);
            assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(call(second, "GET", "/api/runs/" + finishedRun + "/telemetry", token, null)).hasSize((int) telemetryCount);
            assertThat(call(second, "GET", "/api/runs/" + finishedRun + "/report", token, null).get("telemetryReadingCount").asLong()).isEqualTo(telemetryCount);

            JsonNode cut = call(second, "GET", "/api/runs/" + interruptedRun, token, null);
            assertThat(Set.of("FAILED", "COMPLETED")).contains(cut.get("status").asText());
            assertThat(cut.get("status").asText()).isNotEqualTo("RUNNING");
            assertThat(call(second, "POST", "/api/auth/login", null, "{\"email\":\"rita@example.com\",\"password\":\"password123\"}").get("userId").asLong()).isPositive();
        } finally { second.close(); }
    }
}
