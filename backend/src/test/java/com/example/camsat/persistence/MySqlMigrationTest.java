package com.example.camsat.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the real migrations on a real MySQL 8. Skipped automatically when Docker is not available. */
@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("camsat");

    @Test
    void migrationsApplyToMySqlAndConstraintsHold() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load();
        assertThat(flyway.migrate().success).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()); Statement st = c.createStatement()) {
            st.executeUpdate("insert into users (name,email,password_hash,role,created_at) values ('a','a@x.io','h','USER',now(6))");
            assertThatThrownBy(() -> st.executeUpdate("insert into users (name,email,password_hash,role,created_at) values ('b','a@x.io','h','USER',now(6))"))
                    .hasMessageContaining("Duplicate");
            assertThatThrownBy(() -> st.executeUpdate("insert into telemetry_readings (run_id,elapsed_time_seconds,altitude_meters,vertical_velocity_mps,flight_state,recorded_at) values (999,0,1,0,'READY',now(6))"))
                    .hasMessageContaining("foreign key");
        }
    }
}
