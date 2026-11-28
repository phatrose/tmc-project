-- PHỤ TRÁCH: Task 8 — tích hợp với Task 5 (FE-ADMIN-02).
-- Migration additive V013: lưu trữ bền vững dữ liệu quản trị hệ thống trên PostgreSQL
-- (factories, gates, shifts, holidays, system configurations, device statuses, DSR).

CREATE TABLE IF NOT EXISTS admin_factories (
    id      VARCHAR(128) PRIMARY KEY,
    name    VARCHAR(200) NOT NULL,
    zone_id VARCHAR(64) NOT NULL,
    address TEXT NOT NULL DEFAULT '',
    status  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS admin_gates (
    id               VARCHAR(128) PRIMARY KEY,
    factory_id       VARCHAR(128) NOT NULL,
    edge_id          VARCHAR(128),
    name             VARCHAR(200) NOT NULL,
    direction        VARCHAR(16) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    turnstile_api_id VARCHAR(128),
    status           VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
);
CREATE INDEX IF NOT EXISTS idx_admin_gates_factory ON admin_gates (factory_id);
CREATE INDEX IF NOT EXISTS idx_admin_gates_turnstile ON admin_gates (turnstile_api_id) WHERE turnstile_api_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS admin_shifts (
    id                         VARCHAR(128) PRIMARY KEY,
    factory_id                 VARCHAR(128) NOT NULL,
    name                       VARCHAR(200) NOT NULL,
    starts_at                  TIME NOT NULL,
    ends_at                    TIME NOT NULL,
    is_overnight               BOOLEAN NOT NULL DEFAULT FALSE,
    grace_period_minutes_late  INT NOT NULL DEFAULT 0,
    grace_period_minutes_early INT NOT NULL DEFAULT 0,
    active                     BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_admin_shifts_factory ON admin_shifts (factory_id);

CREATE TABLE IF NOT EXISTS admin_holidays (
    id           VARCHAR(128) PRIMARY KEY,
    factory_id   VARCHAR(128),
    name         VARCHAR(200) NOT NULL,
    holiday_date DATE NOT NULL,
    paid         BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_admin_holidays_factory ON admin_holidays (factory_id);

CREATE TABLE IF NOT EXISTS admin_system_configurations (
    config_key   VARCHAR(128) PRIMARY KEY,
    config_value TEXT NOT NULL,
    category     VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    description  TEXT NOT NULL DEFAULT '',
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_admin_sysconfig_category ON admin_system_configurations (category);

CREATE TABLE IF NOT EXISTS admin_device_statuses (
    device_id         VARCHAR(128) PRIMARY KEY,
    device_type       VARCHAR(64) NOT NULL,
    name              VARCHAR(200),
    factory_id        VARCHAR(128),
    status            VARCHAR(32) NOT NULL DEFAULT 'ONLINE',
    last_heartbeat_at TIMESTAMPTZ NOT NULL,
    firmware_version  VARCHAR(64),
    ip_address        VARCHAR(64),
    error_message     TEXT
);
CREATE INDEX IF NOT EXISTS idx_admin_devices_factory ON admin_device_statuses (factory_id);

CREATE TABLE IF NOT EXISTS admin_data_subject_requests (
    id           VARCHAR(128) PRIMARY KEY,
    employee_id  VARCHAR(128) NOT NULL,
    request_type VARCHAR(64) NOT NULL,
    status       VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reason       TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    processed_by VARCHAR(128),
    processed_at TIMESTAMPTZ,
    notes        TEXT
);
CREATE INDEX IF NOT EXISTS idx_admin_dsr_employee ON admin_data_subject_requests (employee_id);
