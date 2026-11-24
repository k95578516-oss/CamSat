package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local MySQL only. Repairs a database that was created by an older version of the application (or by hand) and then
 * adopted by Flyway with baseline-on-migrate, which SKIPS V1__initial_schema.sql. Such a database can lack tables or
 * columns the current code needs, for example missions.user_id ("Unknown column 'm1_0.user_id'") and
 * simulation_runs.user_id.
 *
 * What it does, in order: create any missing table; add any missing column; give legacy rows an owner (the oldest
 * user) so they stay visible; normalise unknown run statuses; then tighten user_id to NOT NULL and add the foreign
 * keys and indexes when the data allows it. Nothing is dropped. Every statement is attempted on its own and logged,
 * so one failure never stops the application from starting. Safe to run more than once.
 */
public class V11__repair_missing_columns extends BaseJavaMigration {
    private static final Logger log = LoggerFactory.getLogger(V11__repair_missing_columns.class);

    private static final String TS = "DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)";

    private static final Map<String, String> CREATE = new LinkedHashMap<>();
    private static final Map<String, Map<String, String>> COLUMNS = new LinkedHashMap<>();

    static {
        CREATE.put("users", "CREATE TABLE users (id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(100) NOT NULL, "
                + "email VARCHAR(255) NOT NULL, password_hash VARCHAR(100) NOT NULL, role VARCHAR(20) NOT NULL, "
                + "created_at DATETIME(6) NOT NULL, PRIMARY KEY (id), CONSTRAINT uq_users_email UNIQUE (email))");
        CREATE.put("missions", "CREATE TABLE missions (id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NULL, "
                + "name VARCHAR(100) NOT NULL, payload_mass_kg DOUBLE NOT NULL, release_altitude_m DOUBLE NOT NULL, "
                + "parachute_diameter_m DOUBLE NOT NULL, drag_coefficient DOUBLE NOT NULL, wind_speed_mps DOUBLE NOT NULL, "
                + "wind_direction_deg DOUBLE NOT NULL, initial_battery_percent DOUBLE NOT NULL, "
                + "created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, PRIMARY KEY (id))");
        CREATE.put("simulation_runs", "CREATE TABLE simulation_runs (id BIGINT NOT NULL AUTO_INCREMENT, "
                + "mission_id BIGINT NOT NULL, user_id BIGINT NULL, status VARCHAR(20) NOT NULL, started_at DATETIME(6) NULL, "
                + "ended_at DATETIME(6) NULL, landing_time_seconds DOUBLE NULL, landing_drift_m DOUBLE NULL, "
                + "final_result VARCHAR(500) NULL, elapsed_sim_seconds DOUBLE NULL, progress_percent DOUBLE NULL, "
                + "created_at DATETIME(6) NOT NULL, PRIMARY KEY (id))");
        CREATE.put("telemetry_readings", "CREATE TABLE telemetry_readings (id BIGINT NOT NULL AUTO_INCREMENT, "
                + "run_id BIGINT NOT NULL, elapsed_time_seconds DOUBLE NOT NULL, altitude_meters DOUBLE NOT NULL, "
                + "vertical_velocity_mps DOUBLE NOT NULL, temperature_celsius DOUBLE NULL, battery_percent DOUBLE NULL, "
                + "latitude DOUBLE NULL, longitude DOUBLE NULL, flight_state VARCHAR(30) NOT NULL, "
                + "recorded_at DATETIME(6) NOT NULL, PRIMARY KEY (id))");
        CREATE.put("flight_events", "CREATE TABLE flight_events (id BIGINT NOT NULL AUTO_INCREMENT, run_id BIGINT NOT NULL, "
                + "event_type VARCHAR(40) NOT NULL, message VARCHAR(500) NOT NULL, elapsed_time_seconds DOUBLE NOT NULL, "
                + "created_at DATETIME(6) NOT NULL, dedupe_key VARCHAR(80) NOT NULL, PRIMARY KEY (id))");

        COLUMNS.put("users", cols(
                "name", "VARCHAR(100) NOT NULL DEFAULT ''",
                "password_hash", "VARCHAR(100) NOT NULL DEFAULT ''",
                "role", "VARCHAR(20) NOT NULL DEFAULT 'USER'",
                "created_at", TS));
        COLUMNS.put("missions", cols(
                "user_id", "BIGINT NULL",
                "name", "VARCHAR(100) NOT NULL DEFAULT ''",
                "payload_mass_kg", "DOUBLE NOT NULL DEFAULT 0",
                "release_altitude_m", "DOUBLE NOT NULL DEFAULT 0",
                "parachute_diameter_m", "DOUBLE NOT NULL DEFAULT 0",
                "drag_coefficient", "DOUBLE NOT NULL DEFAULT 0",
                "wind_speed_mps", "DOUBLE NOT NULL DEFAULT 0",
                "wind_direction_deg", "DOUBLE NOT NULL DEFAULT 0",
                "initial_battery_percent", "DOUBLE NOT NULL DEFAULT 100",
                "created_at", TS,
                "updated_at", TS));
        COLUMNS.put("simulation_runs", cols(
                "mission_id", "BIGINT NULL",
                "user_id", "BIGINT NULL",
                "status", "VARCHAR(20) NOT NULL DEFAULT 'CREATED'",
                "started_at", "DATETIME(6) NULL",
                "ended_at", "DATETIME(6) NULL",
                "landing_time_seconds", "DOUBLE NULL",
                "landing_drift_m", "DOUBLE NULL",
                "final_result", "VARCHAR(500) NULL",
                "elapsed_sim_seconds", "DOUBLE NULL",
                "progress_percent", "DOUBLE NULL",
                "created_at", TS));
        COLUMNS.put("telemetry_readings", cols(
                "run_id", "BIGINT NULL",
                "elapsed_time_seconds", "DOUBLE NOT NULL DEFAULT 0",
                "altitude_meters", "DOUBLE NOT NULL DEFAULT 0",
                "vertical_velocity_mps", "DOUBLE NOT NULL DEFAULT 0",
                "temperature_celsius", "DOUBLE NULL",
                "battery_percent", "DOUBLE NULL",
                "latitude", "DOUBLE NULL",
                "longitude", "DOUBLE NULL",
                "flight_state", "VARCHAR(30) NOT NULL DEFAULT 'READY'",
                "recorded_at", TS));
        COLUMNS.put("flight_events", cols(
                "run_id", "BIGINT NULL",
                "event_type", "VARCHAR(40) NOT NULL DEFAULT 'SIMULATION_STARTED'",
                "message", "VARCHAR(500) NOT NULL DEFAULT ''",
                "elapsed_time_seconds", "DOUBLE NOT NULL DEFAULT 0",
                "created_at", TS,
                "dedupe_key", "VARCHAR(80) NOT NULL DEFAULT ''"));
    }

    private static Map<String, String> cols(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        return m;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        if (!c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql")) return;

        // 1. tables and columns
        for (Map.Entry<String, String> t : CREATE.entrySet()) {
            String table = t.getKey();
            Set<String> existing = columnsOf(c, table);
            if (existing.isEmpty()) {
                tryExec(c, t.getValue());
                continue;
            }
            for (Map.Entry<String, String> col : COLUMNS.get(table).entrySet()) {
                if (!existing.contains(col.getKey())) {
                    tryExec(c, "ALTER TABLE `" + table + "` ADD COLUMN `" + col.getKey() + "` " + col.getValue());
                }
            }
        }

        // 2. legacy rows: give them an owner so they remain visible to someone (the oldest user), and fix bad statuses
        tryExec(c, "UPDATE missions SET user_id = (SELECT MIN(id) FROM users) WHERE user_id IS NULL");
        tryExec(c, "UPDATE simulation_runs r JOIN missions m ON m.id = r.mission_id SET r.user_id = m.user_id WHERE r.user_id IS NULL");
        tryExec(c, "UPDATE simulation_runs SET status = 'FAILED' WHERE status NOT IN "
                + "('CREATED','RUNNING','PAUSED','COMPLETED','FAILED','CANCELLED')");

        // 3. tighten constraints only when the data allows it
        if (countNulls(c, "missions", "user_id") == 0) {
            tryExec(c, "ALTER TABLE missions MODIFY COLUMN user_id BIGINT NOT NULL");
        } else {
            log.warn("missions.user_id still has NULLs (no users exist yet to own legacy missions); left nullable.");
        }
        if (countNulls(c, "simulation_runs", "user_id") == 0) {
            tryExec(c, "ALTER TABLE simulation_runs MODIFY COLUMN user_id BIGINT NOT NULL");
        } else {
            log.warn("simulation_runs.user_id still has NULLs; left nullable.");
        }

        // 4. indexes and foreign keys (each only if absent; failures are logged, never fatal)
        addIndex(c, "missions", "idx_missions_user_created", "(user_id, created_at)");
        addIndex(c, "missions", "idx_missions_user_name", "(user_id, name)");
        addIndex(c, "simulation_runs", "idx_runs_user_id", "(user_id, id)");
        addIndex(c, "simulation_runs", "idx_runs_mission", "(mission_id, id)");
        addIndex(c, "simulation_runs", "idx_runs_user_status", "(user_id, status)");
        addIndex(c, "telemetry_readings", "idx_telemetry_run_time", "(run_id, elapsed_time_seconds, id)");
        addIndex(c, "flight_events", "idx_events_run_time", "(run_id, elapsed_time_seconds, id)");
        addUnique(c, "flight_events", "uq_events_run_dedupe", "(run_id, dedupe_key)");
        addUnique(c, "users", "uq_users_email", "(email)");
        addForeignKey(c, "missions", "fk_missions_user", "user_id", "users");
        addForeignKey(c, "simulation_runs", "fk_runs_user", "user_id", "users");
        addForeignKey(c, "simulation_runs", "fk_runs_mission", "mission_id", "missions");
        addForeignKey(c, "telemetry_readings", "fk_telemetry_run", "run_id", "simulation_runs");
        addForeignKey(c, "flight_events", "fk_events_run", "run_id", "simulation_runs");
    }

    private static void addIndex(Connection c, String table, String name, String cols) throws Exception {
        if (!indexExists(c, table, name)) tryExec(c, "CREATE INDEX `" + name + "` ON `" + table + "` " + cols);
    }

    private static void addUnique(Connection c, String table, String name, String cols) throws Exception {
        if (!indexExists(c, table, name)) tryExec(c, "ALTER TABLE `" + table + "` ADD CONSTRAINT `" + name + "` UNIQUE " + cols);
    }

    private static void addForeignKey(Connection c, String table, String name, String column, String refTable) throws Exception {
        if (constraintExists(c, table, name)) return;
        tryExec(c, "ALTER TABLE `" + table + "` ADD CONSTRAINT `" + name + "` FOREIGN KEY (`" + column
                + "`) REFERENCES `" + refTable + "` (id) ON DELETE RESTRICT");
    }

    private static Set<String> columnsOf(Connection c, String table) throws Exception {
        Set<String> out = new HashSet<>();
        String sql = "select lower(column_name) from information_schema.columns where table_schema = database() and lower(table_name) = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    private static boolean indexExists(Connection c, String table, String name) throws Exception {
        String sql = "select count(*) from information_schema.statistics where table_schema = database() "
                + "and lower(table_name) = ? and lower(index_name) = ?";
        return count(c, sql, table, name.toLowerCase(Locale.ROOT)) > 0;
    }

    private static boolean constraintExists(Connection c, String table, String name) throws Exception {
        String sql = "select count(*) from information_schema.table_constraints where table_schema = database() "
                + "and lower(table_name) = ? and lower(constraint_name) = ?";
        return count(c, sql, table, name.toLowerCase(Locale.ROOT)) > 0;
    }

    private static long count(Connection c, String sql, String a, String b) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static long countNulls(Connection c, String table, String column) {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from `" + table + "` where `" + column + "` is null")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) {
            log.warn("Could not inspect {}.{}: {}", table, column, e.getMessage());
            return 1; // unknown: do not tighten
        }
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