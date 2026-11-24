package com.example.camsat.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Local development only. Before migrating, realigns checksums and descriptions in flyway_schema_history with the
 * migration files on disk, so harmless edits (whitespace, comments) to already-applied migrations do not block startup.
 * Never active in the prod profile, where a changed migration must fail loudly.
 */
@Configuration
@Profile("dev")
public class FlywayDevRepairConfig {

    @Bean
    FlywayMigrationStrategy devRepairThenMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}