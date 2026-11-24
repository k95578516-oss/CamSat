-- One-time local MySQL 8 setup. Run as an administrator, e.g. from PowerShell:
--   mysql -u root -p < scripts\db-setup.sql
-- Replace the password below (and use the same value in DB_PASSWORD). Flyway creates all tables on first start.
CREATE DATABASE IF NOT EXISTS cam_sat CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'camsat'@'localhost' IDENTIFIED BY 'camsat_dev_password';
GRANT ALL PRIVILEGES ON cam_sat.* TO 'camsat'@'localhost';
FLUSH PRIVILEGES;
