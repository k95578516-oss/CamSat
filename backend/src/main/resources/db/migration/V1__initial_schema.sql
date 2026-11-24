-- CamSat initial schema. Portable MySQL 8 DDL (also runs on H2 in MySQL mode for the fast test profile).

CREATE TABLE users (
                       id            BIGINT       NOT NULL AUTO_INCREMENT,
                       name          VARCHAR(100) NOT NULL,
                       email         VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(100) NOT NULL,
                       role          VARCHAR(20)  NOT NULL,
                       created_at    DATETIME(6)  NOT NULL,
                       PRIMARY KEY (id),
                       CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE missions (
                          id                      BIGINT       NOT NULL AUTO_INCREMENT,
                          user_id                 BIGINT       NOT NULL,
                          name                    VARCHAR(100) NOT NULL,
                          payload_mass_kg         DOUBLE       NOT NULL,
                          release_altitude_m      DOUBLE       NOT NULL,
                          parachute_diameter_m    DOUBLE       NOT NULL,
                          drag_coefficient        DOUBLE       NOT NULL,
                          wind_speed_mps          DOUBLE       NOT NULL,
                          wind_direction_deg      DOUBLE       NOT NULL,
                          initial_battery_percent DOUBLE       NOT NULL,
                          created_at              DATETIME(6)  NOT NULL,
                          updated_at              DATETIME(6)  NOT NULL,
                          PRIMARY KEY (id),
                          CONSTRAINT fk_missions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);
CREATE INDEX idx_missions_user_created ON missions (user_id, created_at);
CREATE INDEX idx_missions_user_name ON missions (user_id, name);

-- user_id is denormalised from the mission owner so every run query can be scoped by owner without a join.
CREATE TABLE simulation_runs (
                                 id                   BIGINT       NOT NULL AUTO_INCREMENT,
                                 mission_id           BIGINT       NOT NULL,
                                 user_id              BIGINT       NOT NULL,
                                 status               VARCHAR(20)  NOT NULL,
                                 started_at           DATETIME(6)  NULL,
                                 ended_at             DATETIME(6)  NULL,
                                 landing_time_seconds DOUBLE       NULL,
                                 final_result         VARCHAR(500) NULL,
                                 progress_percent     DOUBLE       NULL,
                                 created_at           DATETIME(6)  NOT NULL,
                                 PRIMARY KEY (id),
                                 CONSTRAINT fk_runs_mission FOREIGN KEY (mission_id) REFERENCES missions (id) ON DELETE RESTRICT,
                                 CONSTRAINT fk_runs_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE RESTRICT,
                                 CONSTRAINT ck_runs_status CHECK (status IN ('CREATED','RUNNING','PAUSED','COMPLETED','FAILED','CANCELLED'))
);
CREATE INDEX idx_runs_user_id ON simulation_runs (user_id, id);
CREATE INDEX idx_runs_mission ON simulation_runs (mission_id, id);
CREATE INDEX idx_runs_user_status ON simulation_runs (user_id, status);

CREATE TABLE telemetry_readings (
                                    id                    BIGINT      NOT NULL AUTO_INCREMENT,
                                    run_id                BIGINT      NOT NULL,
                                    elapsed_time_seconds  DOUBLE      NOT NULL,
                                    altitude_meters       DOUBLE      NOT NULL,
                                    vertical_velocity_mps DOUBLE      NOT NULL,
                                    temperature_celsius   DOUBLE      NULL,
                                    battery_percent       DOUBLE      NULL,
                                    latitude              DOUBLE      NULL,
                                    longitude             DOUBLE      NULL,
                                    flight_state          VARCHAR(30) NOT NULL,
                                    recorded_at           DATETIME(6) NOT NULL,
                                    PRIMARY KEY (id),
                                    CONSTRAINT fk_telemetry_run FOREIGN KEY (run_id) REFERENCES simulation_runs (id) ON DELETE RESTRICT
);
CREATE INDEX idx_telemetry_run_time ON telemetry_readings (run_id, elapsed_time_seconds, id);

-- dedupe_key + unique constraint make every one-shot event (e.g. PARACHUTE_DEPLOYED) idempotent per run.
-- Repeatable events (pause/resume) use "TYPE:n".
CREATE TABLE flight_events (
                               id                   BIGINT       NOT NULL AUTO_INCREMENT,
                               run_id               BIGINT       NOT NULL,
                               event_type           VARCHAR(40)  NOT NULL,
                               message              VARCHAR(500) NOT NULL,
                               elapsed_time_seconds DOUBLE       NOT NULL,
                               created_at           DATETIME(6)  NOT NULL,
                               dedupe_key           VARCHAR(80)  NOT NULL,
                               PRIMARY KEY (id),
                               CONSTRAINT fk_events_run FOREIGN KEY (run_id) REFERENCES simulation_runs (id) ON DELETE RESTRICT,
                               CONSTRAINT uq_events_run_dedupe UNIQUE (run_id, dedupe_key)
);
CREATE INDEX idx_events_run_time ON flight_events (run_id, elapsed_time_seconds, id);