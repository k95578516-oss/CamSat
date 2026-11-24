package com.example.camsat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.example.camsat.support.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Verifies real backend responses against the frontend's TypeScript types (src/types/api.ts): exact property names,
 * primitive kinds, nullability, enum values, arrays and nested objects. Skipped if the frontend file cannot be found
 * (set -Dcamsat.frontend.types=/path/to/api.ts to point at it).
 */
class ContractTest extends ApiTestBase {
    record Iface(String parent, Map<String, String[]> fields) { }   // field -> [type, optional?"?":""]

    static Map<String, Iface> ifaces = new HashMap<>();
    static Map<String, Set<String>> aliases = new HashMap<>();
    static boolean loaded;

    @BeforeAll
    static void loadTypes() throws Exception {
        Path p = Path.of(System.getProperty("camsat.frontend.types", "../frontend/src/types/api.ts"));
        if (!Files.exists(p)) return;
        String ts = Files.readString(p).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        Matcher a = Pattern.compile("export type (\\w+) =\\s*((?:\\s*\\|?\\s*'\\w+')+)").matcher(ts);
        while (a.find()) {
            Set<String> values = new HashSet<>();
            Matcher lit = Pattern.compile("'(\\w+)'").matcher(a.group(2));
            while (lit.find()) values.add(lit.group(1));
            aliases.put(a.group(1), values);
        }
        Matcher m = Pattern.compile("export interface (\\w+)(?: extends (\\w+))? \\{(.*?)\\n\\}", Pattern.DOTALL).matcher(ts);
        while (m.find()) {
            Map<String, String[]> fields = new LinkedHashMap<>();
            Matcher f = Pattern.compile("^\\s*(\\w+)(\\?)?:\\s*(.+?)\\s*$", Pattern.MULTILINE).matcher(m.group(3));
            while (f.find()) fields.put(f.group(1), new String[]{f.group(3), f.group(2) == null ? "" : "?"});
            ifaces.put(m.group(1), new Iface(m.group(2), fields));
        }
        loaded = !ifaces.isEmpty();
    }

    private static Map<String, String[]> allFields(String name) {
        Iface i = ifaces.get(name);
        Map<String, String[]> out = new LinkedHashMap<>(i.parent() == null ? Map.of() : allFields(i.parent()));
        out.putAll(i.fields());
        return out;
    }

    private void checkObject(JsonNode v, String iface, String path, List<String> errors) {
        if (!v.isObject()) { errors.add(path + ": expected object for " + iface); return; }
        Map<String, String[]> fields = allFields(iface);
        for (String key : (Iterable<String>) v::fieldNames) if (!fields.containsKey(key)) errors.add(path + "." + key + ": not declared in TS " + iface);
        fields.forEach((name, spec) -> {
            if (!v.has(name)) { if (spec[1].isEmpty()) errors.add(path + "." + name + ": missing (required by TS " + iface + ")"); return; }
            checkValue(v.get(name), spec[0], path + "." + name, errors);
        });
    }

    private void checkValue(JsonNode v, String type, String path, List<String> errors) {
        List<String> parts = new ArrayList<>(Arrays.asList(type.trim().split("\\s*\\|\\s*(?![^<]*>)")));
        if (parts.remove("null")) { if (v.isNull()) return; }
        if (v.isNull()) { errors.add(path + ": null but TS type is " + type); return; }
        List<String> trial = new ArrayList<>();
        for (String part : parts) {
            List<String> e = new ArrayList<>();
            if (matches(v, part.trim(), path, e)) return;
            trial.addAll(e);
        }
        errors.add(path + ": value " + abbreviate(v) + " does not match TS type " + type + (trial.isEmpty() ? "" : " " + trial));
    }

    private boolean matches(JsonNode v, String t, String path, List<String> errors) {
        if (t.equals("number")) return v.isNumber();
        if (t.equals("string")) return v.isTextual();
        if (t.equals("boolean")) return v.isBoolean();
        if (t.startsWith("'") && t.endsWith("'")) return v.isTextual() && v.asText().equals(t.substring(1, t.length() - 1));
        if (t.endsWith("[]")) {
            if (!v.isArray()) return false;
            for (int i = 0; i < v.size(); i++) { List<String> e = new ArrayList<>(); checkValue(v.get(i), t.substring(0, t.length() - 2), path + "[" + i + "]", e); if (!e.isEmpty()) { errors.addAll(e); return false; } }
            return true;
        }
        if (t.startsWith("Partial<Record<") || t.startsWith("Record<")) {
            if (!v.isObject()) return false;
            String inner = t.replaceAll("^Partial<", "").replaceAll(">$", "");
            String valueType = inner.substring(inner.lastIndexOf(',') + 1).replace(">", "").trim();
            String keyType = inner.substring(inner.indexOf('<') + 1, inner.indexOf(',')).trim();
            for (Iterator<Map.Entry<String, JsonNode>> it = v.fields(); it.hasNext();) {
                var en = it.next();
                if (aliases.containsKey(keyType) && !aliases.get(keyType).contains(en.getKey())) { errors.add(path + "." + en.getKey() + ": key not in " + keyType); return false; }
                List<String> e = new ArrayList<>(); checkValue(en.getValue(), valueType, path + "." + en.getKey(), e);
                if (!e.isEmpty()) { errors.addAll(e); return false; }
            }
            return true;
        }
        if (aliases.containsKey(t)) return v.isTextual() && aliases.get(t).contains(v.asText());
        if (ifaces.containsKey(t)) { List<String> e = new ArrayList<>(); checkObject(v, t, path, e); errors.addAll(e); return e.isEmpty(); }
        errors.add(path + ": unsupported TS type " + t);
        return false;
    }

    private static String abbreviate(JsonNode v) { String s = v.toString(); return s.length() > 60 ? s.substring(0, 60) + "…" : s; }

    private void assertConforms(JsonNode body, String iface) {
        List<String> errors = new ArrayList<>();
        checkObject(body, iface, iface, errors);
        assertThat(errors).as("Response does not conform to TS interface " + iface).isEmpty();
    }

    @Test
    void liveResponsesConformToTheFrontendTypes() throws Exception {
        assumeTrue(loaded, "frontend src/types/api.ts not found; set -Dcamsat.frontend.types");
        Session s = register();
        JsonNode auth = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", s.email(), "password", "correct-horse-battery")))).andReturn());
        assertConforms(auth, "AuthResponse");

        long missionId = createMission(s, missionBody());
        assertConforms(getJson(s, "/api/missions/" + missionId), "MissionResponse");
        for (JsonNode m : getJson(s, "/api/missions")) assertConforms(m, "MissionResponse");

        long run = createRun(s, missionId);
        assertConforms(getJson(s, "/api/runs/" + run), "SimulationRunResponse");   // CREATED: nullable fields are null
        assertConforms(getJson(s, "/api/runs/" + run + "/report"), "MissionReportResponse");   // no telemetry yet
        assertConforms(getJson(s, "/api/runs/" + run + "/recovery"), "RecoveryResponse");

        mvc.perform(auth(post("/api/runs/" + run + "/start"), s));
        JsonNode done = waitForStatus(s, run, Set.of("COMPLETED", "FAILED"), Duration.ofSeconds(60));
        assertConforms(done, "SimulationRunResponse");
        for (JsonNode r : getJson(s, "/api/runs/recent")) assertConforms(r, "SimulationRunResponse");
        for (JsonNode r : getJson(s, "/api/missions/" + missionId + "/runs")) assertConforms(r, "SimulationRunResponse");
        for (JsonNode t : getJson(s, "/api/runs/" + run + "/telemetry")) assertConforms(t, "TelemetryReadingResponse");
        assertConforms(getJson(s, "/api/runs/" + run + "/telemetry/latest-reading"), "TelemetryReadingResponse");
        for (JsonNode e : getJson(s, "/api/runs/" + run + "/events")) assertConforms(e, "FlightEventResponse");
        for (JsonNode e : getJson(s, "/api/runs/" + run + "/events/latest")) assertConforms(e, "FlightEventResponse");
        assertThat(getJson(s, "/api/runs/" + run + "/events/count").isIntegralNumber()).isTrue();
        assertConforms(getJson(s, "/api/runs/" + run + "/recovery"), "RecoveryResponse");
        assertConforms(getJson(s, "/api/runs/" + run + "/report"), "MissionReportResponse");
        assertConforms(getJson(s, "/api/dashboard"), "DashboardResponse");
        assertConforms(getJson(s, "/api/analytics"), "AnalyticsResponse");
    }

    @Test
    void errorBodiesConformToApiErrorBody() throws Exception {
        assumeTrue(loaded, "frontend src/types/api.ts not found; set -Dcamsat.frontend.types");
        Session s = register();
        assertConforms(json(mvc.perform(auth(get("/api/missions/999999"), s)).andReturn()), "ApiErrorBody");
        assertConforms(json(mvc.perform(auth(post("/api/missions"), s).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn()), "ApiErrorBody");
        assertConforms(json(mvc.perform(get("/api/missions")).andReturn()), "ApiErrorBody");
        assertConforms(json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@y.zz\",\"password\":\"nope-nope\"}")).andReturn()), "ApiErrorBody");
    }
}
