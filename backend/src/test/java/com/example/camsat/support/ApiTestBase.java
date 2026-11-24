package com.example.camsat.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestBase {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;

    public record Session(String token, long userId, String email) { }

    protected Session register() throws Exception {
        String email = "user" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@example.com";
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("name", "Test User", "email", email, "password", "correct-horse-battery"))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode n = json(r);
        return new Session(n.get("token").asText(), n.get("userId").asLong(), email);
    }

    protected JsonNode json(MvcResult r) throws Exception { return om.readTree(r.getResponse().getContentAsString()); }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, Session s) {
        return b.header("Authorization", "Bearer " + s.token());
    }

    protected Map<String, Object> missionBody() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Alpha"); m.put("payloadMassKg", 0.35); m.put("releaseAltitudeM", 300.0);
        m.put("parachuteDiameterM", 0.5); m.put("dragCoefficient", 1.5); m.put("windSpeedMps", 3.0);
        m.put("windDirectionDeg", 90.0); m.put("initialBatteryPercent", 100.0);
        return m;
    }

    protected long createMission(Session s, Map<String, Object> body) throws Exception {
        MvcResult r = mvc.perform(auth(post("/api/missions"), s).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body))).andExpect(status().isCreated()).andReturn();
        return json(r).get("id").asLong();
    }

    protected long createRun(Session s, long missionId) throws Exception {
        MvcResult r = mvc.perform(auth(post("/api/missions/" + missionId + "/runs"), s)).andExpect(status().isCreated()).andReturn();
        return json(r).get("id").asLong();
    }

    protected JsonNode getJson(Session s, String url) throws Exception {
        return json(mvc.perform(auth(get(url), s)).andExpect(status().isOk()).andReturn());
    }

    protected JsonNode waitForStatus(Session s, long runId, Set<String> wanted, Duration timeout) throws Exception {
        long end = System.nanoTime() + timeout.toNanos();
        JsonNode run;
        do {
            run = getJson(s, "/api/runs/" + runId);
            if (wanted.contains(run.get("status").asText())) return run;
            Thread.sleep(25);
        } while (System.nanoTime() < end);
        throw new AssertionError("Run " + runId + " did not reach " + wanted + " in time; last status " + run.get("status").asText());
    }
}
