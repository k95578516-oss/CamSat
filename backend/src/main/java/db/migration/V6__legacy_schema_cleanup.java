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

/**
 * Local MySQL only. Databases created by hand or by an older Hibernate version can differ from the entities in ways
 * that make INSERTs fail with HTTP 500: an id column without AUTO_INCREMENT, leftover NOT NULL columns the code no
 * longer fills, or missing users columns. This makes those tables insertable again. Nothing is dropped or reset, and
 * each statement is attempted on its own so one failure can never stop the application from starting.
 */
public class V6__legacy_schema_cleanup extends BaseJavaMigration {
    private static final Logger log = LoggerFactory.getLogger(V6__legacy_schema_cleanup.class);

    private static final Map<String, Set<String>> KNOWN = Map.of(
            "users", Set.of("id", "name", "email", "password_hash", "role", "created_at"),
            "missions", Set.of("id", "user_id", "name", "payload_mass_kg", "release_altitude_m", "parachute_diameter_m",
                    "drag_coefficient", "wind_speed_mps", "wind_direction_deg", "initial_battery_percent", "created_at", "updated_at"),
            "simulation_runs", Set.of("id", "mission_id", "user_id", "status", "started_at", "ended_at", "landing_time_seconds",
                    "landing_drift_m", "final_result", "elapsed_sim_seconds", "progress_percent", "created_at"),
            "telemetry_readings", Set.of("id", "run_id", "elapsed_time_seconds", "altitude_meters", "vertical_velocity_mps",
                    "temperature_celsius", "battery_percent", "latitude", "longitude", "flight_state", "recorded_at"),
            "flight_events", Set.of("id", "run_id", "event_type", "message", "elapsed_time_seconds", "created_at", "dedupe_key"));

    private record ColInfo(String name, String type, boolean nullable, String dflt, String extra, String key) { }

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        if (!c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql")) return;

        for (Map.Entry<String, Set<String>> entry : KNOWN.entrySet()) {
            String table = entry.getKey();
            List<ColInfo> cols = describe(c, table);
            if (cols.isEmpty()) continue;

            if ("users".equals(table)) {
                addIfMissing(c, cols, table, "name", "VARCHAR(100) NOT NULL DEFAULT ''");
                addIfMissing(c, cols, table, "password_hash", "VARCHAR(100) NOT NULL DEFAULT ''");
                addIfMissing(c, cols, table, "role", "VARCHAR(20) NOT NULL DEFAULT 'USER'");
            }
            for (ColInfo col : cols) {
                String name = col.name().toLowerCase(Locale.ROOT);
                boolean autoInc = col.extra() != null && col.extra().toLowerCase(Locale.ROOT).contains("auto_increment");
                if ("id".equals(name) && !autoInc && "PRI".equalsIgnoreCase(col.key())) {
                    tryExec(c, "ALTER TABLE `" + table + "` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT");
                } else if (!entry.getValue().contains(name) && !col.nullable() && col.dflt() == null && !autoInc
                        && (col.extra() == null || !col.extra().toLowerCase(Locale.ROOT).contains("generated"))) {
                    tryExec(c, "ALTER TABLE `" + table + "` MODIFY COLUMN `" + col.name() + "` " + col.type() + " NULL");
                }
            }
        }
    }

    private static void addIfMissing(Connection c, List<ColInfo> cols, String table, String column, String ddl) {
        boolean present = cols.stream().anyMatch(x -> x.name().equalsIgnoreCase(column));
        if (!present) tryExec(c, "ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + ddl);
    }

    private static List<ColInfo> describe(Connection c, String table) throws Exception {
        List<ColInfo> out = new ArrayList<>();
        String sql = "select column_name, column_type, is_nullable, column_default, extra, column_key "
                + "from information_schema.columns where table_schema = database() and table_name = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ColInfo(rs.getString(1), rs.getString(2), "YES".equalsIgnoreCase(rs.getString(3)),
                            rs.getString(4), rs.getString(5), rs.getString(6)));
                }
            }
        }
        return out;
    }

    private static void tryExec(Connection c, String sql) {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
            log.info("Schema cleanup applied: {}", sql);
        } catch (Exception e) {
            log.warn("Schema cleanup skipped ({}): {}", e.getMessage(), sql);
        }
    }
}