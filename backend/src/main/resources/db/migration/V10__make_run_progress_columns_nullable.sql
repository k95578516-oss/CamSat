-- V3 and V4 added these columns as NOT NULL DEFAULT 0, but the application stores NULL for "not known yet"
-- (a run that has not started or never landed has no elapsed time / drift). Hibernate inserts NULL explicitly,
-- so every run insert failed with "cannot be null". Relax both to nullable and clear the meaningless default 0 drift.
ALTER TABLE simulation_runs MODIFY COLUMN elapsed_sim_seconds DOUBLE NULL;
ALTER TABLE simulation_runs MODIFY COLUMN landing_drift_m DOUBLE NULL;
UPDATE simulation_runs SET landing_drift_m = NULL WHERE landing_time_seconds IS NULL;