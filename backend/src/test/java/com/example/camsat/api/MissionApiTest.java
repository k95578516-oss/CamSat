package com.example.camsat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.camsat.support.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class MissionApiTest extends ApiTestBase {

    @Test
    void createListSearchGetUpdateDelete() throws Exception {
        Session s = register();
        Map<String, Object> body = missionBody();
        body.put("name", "Falcon Drop");
        long id = createMission(s, body);
        long other = createMission(s, missionBody());

        JsonNode created = getJson(s, "/api/missions/" + id);
        assertThat(created.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name", "payloadMassKg", "releaseAltitudeM",
                "parachuteDiameterM", "dragCoefficient", "windSpeedMps", "windDirectionDeg", "initialBatteryPercent", "createdAt", "updatedAt");
        assertThat(created.get("name").asText()).isEqualTo("Falcon Drop");
        assertThat(created.get("createdAt").asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");

        assertThat(getJson(s, "/api/missions")).hasSize(2);
        JsonNode found = om.readTree(mvc.perform(auth(get("/api/missions").param("search", "falcon"), s)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).get("id").asLong()).isEqualTo(id);
        assertThat(om.readTree(mvc.perform(auth(get("/api/missions").param("search", "zzz"), s)).andReturn().getResponse().getContentAsString())).isEmpty();

        body.put("name", "Falcon Drop v2"); body.put("windSpeedMps", 7.5);
        mvc.perform(auth(put("/api/missions/" + id), s).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Falcon Drop v2")).andExpect(jsonPath("$.windSpeedMps").value(7.5));

        mvc.perform(auth(delete("/api/missions/" + id), s)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/missions/" + id), s)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/missions/" + other), s)).andExpect(status().isOk());
    }

    @Test
    void invalidParametersAreRejectedWithFieldErrors() throws Exception {
        Session s = register();
        Object[][] bad = {
                {"payloadMassKg", 0.0, "payloadMassKg"}, {"payloadMassKg", 51.0, "payloadMassKg"},
                {"releaseAltitudeM", 19.0, "releaseAltitudeM"}, {"releaseAltitudeM", 10001.0, "releaseAltitudeM"},
                {"parachuteDiameterM", 0.0, "parachuteDiameterM"}, {"parachuteDiameterM", 3.5, "parachuteDiameterM"},
                {"dragCoefficient", 2.5, "dragCoefficient"}, {"windSpeedMps", -1.0, "windSpeedMps"},
                {"windDirectionDeg", 360.0, "windDirectionDeg"}, {"initialBatteryPercent", 0.0, "initialBatteryPercent"},
                {"initialBatteryPercent", 101.0, "initialBatteryPercent"}, {"name", "", "name"}, {"name", "x".repeat(101), "name"},
        };
        for (Object[] b : bad) {
            Map<String, Object> body = missionBody();
            body.put((String) b[0], b[1]);
            mvc.perform(auth(post("/api/missions"), s).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors." + b[2]).exists());
        }
        Map<String, Object> missing = missionBody();
        missing.remove("dragCoefficient");
        mvc.perform(auth(post("/api/missions"), s).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(missing)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.dragCoefficient").exists());
    }

    @Test
    void malformedBodiesAndIdsAreBadRequestsAndUnknownIdsAreNotFound() throws Exception {
        Session s = register();
        mvc.perform(auth(post("/api/missions"), s).contentType(MediaType.APPLICATION_JSON).content("{not json")).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/missions"), s).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"payloadMassKg\":\"heavy\"}")).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/missions/abc"), s)).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/missions/999999"), s)).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(auth(put("/api/missions/999999"), s).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(missionBody()))).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/missions/999999"), s)).andExpect(status().isNotFound());
    }

    @Test
    void missionWithRunsCannotBeEditedOrDeleted() throws Exception {
        Session s = register();
        long id = createMission(s, missionBody());
        createRun(s, id);
        mvc.perform(auth(put("/api/missions/" + id), s).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(missionBody())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(auth(delete("/api/missions/" + id), s)).andExpect(status().isConflict());
        mvc.perform(auth(get("/api/missions/" + id), s)).andExpect(status().isOk());
    }

    @Test
    void errorBodiesNeverLeakInternals() throws Exception {
        Session s = register();
        String body = mvc.perform(auth(get("/api/missions/999999"), s)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception").doesNotContain("at com.example").doesNotContain("SQL");
    }
}
