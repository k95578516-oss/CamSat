package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class V12__relax_nullable_and_widen_columns extends BaseJavaMigration {
    private static final Logger log = LoggerFactory.getLogger(V12__relax_nullable_and_widen_columns.class);

    /** table -> columns the entities declare as optional. */
    private static final Map<String, Set<String>> OPTIONAL = Map.of(
            "simulation_runs", Set.of("started_at", "ended_at", "landing_time_seconds", "landing_drift_m", "final_result",
                    "elapsed_sim_seconds", "progress_percent"),
            "telemetry_readings", Set.of("temperature_celsius", "battery_percent", "latitude", "longitude"));

    /** table -> every column the entities know about (anything else that is NOT NULL without a default is relaxed). */
    private static final Map<String, Set<String>> KNOWN = Map.of(
            "users", Set.of("id", "name", "email", "password_hash", "role", "created_at"),
            "missions", Set.of("id", "user_id", "name", "payload_mass_kg", "release_altitude_m", "parachute_diameter_m",
                    "drag_coefficient", "wind_speed_mps", "wind_direction_deg", "initial_battery_percent", "created_at", "updated_at"),
            "simulation_runs", Set.of("id", "mission_id", "user_id", "status", "started_at", "ended_at", "landing_time_seconds",
                    "landing_drift_m", "final_result", "elapsed_sim_seconds", "progress_percent", "created_at"),
            "telemetry_readings", Set.of("id", "run_id", "elapsed_time_seconds", "altitude_meters", "vertical_velocity_mps",
                    "temperature_celsius", "battery_percent", "latitude", "longitude", "flight_state", "recorded_at"),
            "flight_events", Set.of("id", "run_id", "event_type", "message", "elapsed_time_seconds", "created_at", "dedupe_key"));

    /** "table.column" -> required VARCHAR length; these columns are NOT NULL in the entities. */
    private static final Map<String, Integer> STRING_COLUMNS = Map.of(
            "users.role", 20,
            "simulation_runs.status", 20,
            "telemetry_readings.flight_state", 30,
            "flight_events.event_type", 40,
            "flight_events.message", 500,
            "flight_events.dedupe_key", 80);

    private record Col(String name, String columnType, String dataType, Long maxLength, boolean nullable, String dflt, String extra) { }

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        if (!c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql")) return;

        for (String table : KNOWN.keySet()) {
            List<Col> cols = describe(c, table);
            if (cols.isEmpty()) continue;
            Set<String> optional = OPTIONAL.getOrDefault(table, Set.of());
            Set<String> known = KNOWN.get(table);

            for (Col col : cols) {
                String name = col.name().toLowerCase(Locale.ROOT);
                String extra = col.extra() == null ? "" : col.extra().toLowerCase(Locale.ROOT);
                boolean autoInc = extra.contains("auto_increment");
                boolean generated = extra.contains("generated");

                if (optional.contains(name) && !col.nullable()) {
                    tryExec(c, "ALTER TABLE `" + table + "` MODIFY COLUMN `" + col.name() + "` " + col.columnType() + " NULL DEFAULT NULL");
                } else if (!known.contains(name) && !col.nullable() && col.dflt() == null && !autoInc && !generated) {
                    tryExec(c, "ALTER TABLE `" + table + "` MODIFY COLUMN `" + col.name() + "` " + col.columnType() + " NULL");
                }

                Integer wanted = STRING_COLUMNS.get(table + "." + name);
                if (wanted != null) {
                    boolean isVarchar = "varchar".equalsIgnoreCase(col.dataType());
                    boolean tooShort = isVarchar && col.maxLength() != null && col.maxLength() < wanted;
                    if (!isVarchar && ("enum".equalsIgnoreCase(col.dataType()) || "char".equalsIgnoreCase(col.dataType())) || tooShort) {
                        tryExec(c, "ALTER TABLE `" + table + "` MODIFY COLUMN `" + col.name() + "` VARCHAR(" + wanted + ") NOT NULL");
                    }
                }
            }
        }
    }

    private static List<Col> describe(Connection c, String table) throws Exception {
        List<Col> out = new ArrayList<>();
        String sql = "select column_name, column_type, data_type, character_maximum_length, is_nullable, column_default, extra "
                + "from information_schema.columns where table_schema = database() and lower(table_name) = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long len = rs.getLong(4);
                    Long maxLength = rs.wasNull() ? null : len;
                    out.add(new Col(rs.getString(1), rs.getString(2), rs.getString(3), maxLength,
                            "YES".equalsIgnoreCase(rs.getString(5)), rs.getString(6), rs.getString(7)));
                }
            }
        }
        return out;
    }

    private static void tryExec(Connection c, String sql) {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
            log.info("Schema repair applied: {}", sql);
        } catch (Exception e) {
            log.warn("Schema repair skipped ({}): {}", e.getMessage(), sql);
        }
    }
}